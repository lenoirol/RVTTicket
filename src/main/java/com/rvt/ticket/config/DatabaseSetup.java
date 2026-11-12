package com.rvt.ticket.config;

import com.rvt.ticket.booking.TicketService;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
public class DatabaseSetup implements ApplicationRunner {
    private final JdbcTemplate db;
    private final TicketService tickets;
    private final com.rvt.ticket.booking.PlaceService places;
    private final com.rvt.ticket.booking.FleetService fleet;
    private final com.rvt.ticket.booking.AdminIdentityService admins;
    private final com.rvt.ticket.booking.LegacyOrderService legacy;
    private final com.rvt.ticket.booking.StationService stations;
    public DatabaseSetup(JdbcTemplate db,TicketService tickets,com.rvt.ticket.booking.PlaceService places,com.rvt.ticket.booking.FleetService fleet,com.rvt.ticket.booking.AdminIdentityService admins,com.rvt.ticket.booking.LegacyOrderService legacy,com.rvt.ticket.booking.StationService stations) { this.db=db;this.tickets=tickets;this.places=places;this.fleet=fleet;this.admins=admins;this.legacy=legacy; this.stations=stations;}
    @Override @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        // Migration chỉ chạy khi nội dung các file (hoặc danh mục địa điểm) thay đổi so với lần trước,
        // để lần khởi động sau (vd. Render "thức dậy") không giữ khóa lược đồ và trả trang nhanh hơn.
        String fingerprint=fingerprint();
        db.execute("IF OBJECT_ID(N'dbo.TrangThaiMigration',N'U') IS NULL CREATE TABLE dbo.TrangThaiMigration(Id INT NOT NULL CONSTRAINT PK_TrangThaiMigration PRIMARY KEY,DauVan CHAR(64) NOT NULL,CapNhatLuc DATETIME2(0) NOT NULL CONSTRAINT DF_TrangThaiMigration_Luc DEFAULT SYSUTCDATETIME())");
        boolean changed=!db.queryForList("SELECT DauVan FROM dbo.TrangThaiMigration WHERE Id=1",String.class).contains(fingerprint);
        if(changed){
            for(String file:EXECUTE_SCRIPTS)db.execute(script(file));
            for(String file:BATCH_SCRIPTS)runScript(script(file));
        }
        legacy.importAll();
        admins.seed();
        if(changed){places.seed();places.seedAreas();stations.seed();} else places.linkTrips();
        fleet.seed();
        seedLayoutShowcase();
        tickets.closeInvalidTrips();
        seedSeaRoutes();
        tickets.closeSupersededReferenceTrips();
        // Initial reference schedules are reviewed by an administrator before any new sales.
        if(changed)db.update("MERGE dbo.TrangThaiMigration t USING (SELECT 1 AS Id) s ON t.Id=s.Id WHEN MATCHED THEN UPDATE SET DauVan=?,CapNhatLuc=SYSUTCDATETIME() WHEN NOT MATCHED THEN INSERT(Id,DauVan) VALUES(1,?);",fingerprint,fingerprint);
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi WHERE MaChuyen LIKE 'RVT-DEMO-%'",Integer.class)>0) return;
        LocalDate start=tickets.databaseNow().plusHours(7).toLocalDate();
        for(int day=0;day<7;day++) {
            seed(start.plusDays(day),day,"XE_KHACH","TP. Hồ Chí Minh","Đà Lạt","RVT Limousine",22,7,36,320000,true);
            seed(start.plusDays(day),day,"XE_KHACH","TP. Hồ Chí Minh","Cần Thơ","RVT Express",16,3,28,180000,false);
            seed(start.plusDays(day),day,"TAU_THUYEN","Rạch Giá","Phú Quốc","RVT Sea Express",10,3,40,350000,false);
            seed(start.plusDays(day),day,"TAU_THUYEN","Vũng Tàu","Côn Đảo","RVT Ocean",8,4,40,790000,false);
        }
    }
    private static final List<String> EXECUTE_SCRIPTS=List.of("V2__orders_and_layout.sql","V3__operations.sql","V4__fleet_and_places.sql","V5__admin_workspace.sql",
        "V6__rvt_balance_and_passengers.sql","V7__product_workflows.sql","V8__passenger_profile.sql","V9__vnpay_sandbox.sql","V10__operational_integrity.sql");
    private static final List<String> BATCH_SCRIPTS=List.of("V11__appointments_roles.sql","V12__search_index.sql","V13__booking_features.sql","V14__local_areas.sql");
    private static String script(String file) throws java.io.IOException {return new ClassPathResource("db/migration/"+file).getContentAsString(StandardCharsets.UTF_8);}
    private static String fingerprint() throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        for(String file:EXECUTE_SCRIPTS)digest.update(script(file).getBytes(StandardCharsets.UTF_8));
        for(String file:BATCH_SCRIPTS)digest.update(script(file).getBytes(StandardCharsets.UTF_8));
        digest.update(new ClassPathResource("data/vietnam-places.tsv").getContentAsByteArray());
        digest.update(new ClassPathResource("data/vietnam-areas.tsv").getContentAsByteArray());
        digest.update(new ClassPathResource("data/vietnam-bus-stations.tsv").getContentAsByteArray());
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    /** Chạy cả lô T-SQL và duyệt hết kết quả, để lỗi ở câu lệnh sau không bị JDBC bỏ qua im lặng. */
    private void runScript(String sql) {
        db.execute((java.sql.Connection con) -> {
            try(var st=con.createStatement()) {
                boolean hasResultSet=st.execute(sql);
                while(hasResultSet||st.getUpdateCount()!=-1) hasResultSet=st.getMoreResults();
            }
            return null;
        });
    }
    /**
     * Chuyến minh họa cho hai sơ đồ xe mới (giường nằm 41 chỗ có hàng cuối 5 giường, limousine giường phòng 2 dãy).
     * Mỗi xe chạy đi / về luân phiên trong 7 ngày nên lịch xe luôn nối tiếp nhau. Chỉ tạo một lần.
     */
    private void seedLayoutShowcase(){
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi WHERE MaChuyen LIKE 'RVT-GN-%'",Integer.class)>0)return;
        LocalDate start=tickets.databaseNow().plusHours(7).toLocalDate().plusDays(1);
        record Line(String vehicle,String tag,String a,String b,String stationA,String stationB,int outHour,int backHour,int hours,int price,String amenities){}
        var lines=List.of(
            new Line("RVT-MAU-4","DL","TP. Hồ Chí Minh","Đà Lạt","Bến xe Miền Đông","Bến xe Liên tỉnh Đà Lạt",22,13,7,300000,"Giường nằm 3 dãy, hàng cuối 5 giường, Điều hòa, Chăn gối, Nước suối, Wifi"),
            new Line("RVT-MAU-5","NT","TP. Hồ Chí Minh","Nha Trang","Bến xe Miền Đông","Bến xe phía Nam Nha Trang",21,12,8,480000,"Limousine giường phòng 2 dãy, rèm riêng, Điều hòa, Chăn gối, Nước suối, Wifi, Sạc USB"));
        for(var line:lines){
            var ids=db.queryForList("SELECT PhuongTienID FROM dbo.PhuongTien WHERE MaPhuongTien=? AND HoatDong=1",Integer.class,line.vehicle());
            // Xe đã có lịch (do quản trị phân công) thì không chen lịch minh họa vào.
            if(ids.isEmpty()||db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi WHERE PhuongTienID=?",Integer.class,ids.getFirst())>0)continue;
            for(int day=0;day<7;day++){
                for(int leg=0;leg<2;leg++){
                    var f=new com.rvt.ticket.booking.TripForm();
                    f.setCode("RVT-GN-"+line.tag()+"-"+day+"-"+leg);f.setOperator("RVT mẫu");f.setLayout("BUS_SLEEPER");f.setVehicleId(ids.getFirst());
                    f.setFrom(leg==0?line.a():line.b());f.setTo(leg==0?line.b():line.a());f.setPickup(leg==0?line.stationA():line.stationB());f.setDropoff(leg==0?line.stationB():line.stationA());
                    var departure=leg==0?start.plusDays(day).atTime(line.outHour(),0):start.plusDays(day+1).atTime(line.backHour(),0);
                    f.setDeparture(departure);f.setArrival(departure.plusHours(line.hours()));f.setPrice(BigDecimal.valueOf(line.price()));
                    f.setAmenities(line.amenities());f.setDescription("Chuyến minh họa sơ đồ xe giường nằm theo loại xe. Có mặt trước giờ khởi hành 30 phút.");
                    // Lịch đi / về nối tiếp nhau, cách nhau trên 30 phút, nên các kiểm tra mở bán luôn đạt.
                    int id=tickets.createTrip(f);tickets.changeSale(id,"MO_BAN");
                }
            }
        }
    }
    private void seedSeaRoutes(){
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi WHERE MaChuyen LIKE 'RVT-SEA-%'",Integer.class)>0)return;
        LocalDate start=tickets.databaseNow().plusHours(7).toLocalDate().plusDays(1);
        for(var r:com.rvt.ticket.booking.SeaRouteCatalog.routes())for(int day=0;day<3;day++)for(int direction=0;direction<2;direction++){
            var f=new com.rvt.ticket.booking.TripForm();f.setCode("RVT-SEA-"+r.code()+"-"+day+"-"+direction);f.setOperator("RVT Sea · mẫu đồ án");f.setLayout("BOAT");f.setRows(10);f.setVipSeats(8);
            f.setFrom(direction==0?r.from():r.to());f.setTo(direction==0?r.to():r.from());f.setPickup(direction==0?r.portFrom():r.portTo());f.setDropoff(direction==0?r.portTo():r.portFrom());
            // Weekend fares on these two routes are distinct in the research table. No automatic public-holiday guess.
            boolean weekend=start.plusDays(day).getDayOfWeek().getValue()>=5;
            int adult=r.adult(),child=r.child(),senior=r.senior(),vip=r.vip();
            if(weekend&&r.code().equals("VT-CD")){adult=950000;child=760000;senior=760000;vip=1100000;}
            if(weekend&&r.code().equals("TD-CD")){adult=450000;child=360000;senior=360000;}
            f.setPrice(BigDecimal.valueOf(adult));f.setChildPrice(BigDecimal.valueOf(child));f.setSeniorPrice(BigDecimal.valueOf(senior));f.setVipExtra(BigDecimal.valueOf(vip-adult));
            f.setDeparture(start.plusDays(day).atTime(direction==0?8:14,0));f.setArrival(f.getDeparture().plusMinutes(r.minutes()));
            f.setDescription("Lịch và cấu hình 40 chỗ chưa được đơn vị vận chuyển xác nhận. Có mặt tại cảng trước 45 phút. Giá tham khảo 03/10/2026; cần rà soát phụ giá lễ. "+r.note());f.setAmenities("Ghế ECO / VIP theo cấu hình chuyến; mang giấy tờ đúng vé.");int id=tickets.createTrip(f);tickets.changeSale(id,"DONG_BAN");
        }
    }
    private void seed(LocalDate date,int day,String type,String from,String to,String operator,int hour,int duration,int count,int price,boolean sleeper) {
        int id=tickets.insert("INSERT dbo.ChuyenDi(MaChuyen,LoaiVe,DonViVanChuyen,NoiDi,NoiDen,KhoiHanh,DuKienDen,TongSoCho,GiaCoBan,TrangThai) VALUES(?,?,?,?,?,?,?,?,?,'DONG_BAN')",
            "RVT-DEMO-"+day+"-"+hour,type,operator,from,to,Timestamp.valueOf(date.atTime(hour,0).minusHours(7)),Timestamp.valueOf(date.atTime(hour,0).plusHours(duration).minusHours(7)),count,price);
        int columns=sleeper?3:4;
        int floors=sleeper?2:1;
        for(int i=0;i<count;i++) {
            int floor=i/(count/floors)+1;
            int index=i%(count/floors);
            String seat=(floor==1?"A":"B")+String.format("%02d",index+1);
            boolean boat="TAU_THUYEN".equals(type);
            boolean vip=boat && i<8;
            db.update("INSERT dbo.ChoNgoi(ChuyenDiID,MaCho,LoaiVe,LoaiCho,PhuThu) VALUES(?,?,?,?,?)",id,seat,type,boat?(vip?"VIP":"PHO_THONG"):(sleeper?"GIUONG_NAM":"GHE_NGOI"),vip?BigDecimal.valueOf(100000):BigDecimal.ZERO);
            db.update("INSERT dbo.SoDoCho VALUES(?,?,?,?,?)",id,seat,floor,index/columns+1,index%columns+1);
        }
    }
}
