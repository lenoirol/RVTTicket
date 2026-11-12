package com.rvt.ticket.booking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.constraints.*;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FleetService {
    private final JdbcTemplate db;private final ObjectMapper json;private final Validator validator;
    public FleetService(JdbcTemplate db,ObjectMapper json,Validator validator){this.db=db;this.json=json;this.validator=validator;}
    public record Seat(@NotBlank @Pattern(regexp="[A-Z0-9-]{1,20}") String seat,
        @NotNull @Pattern(regexp="GHE_NGOI|GIUONG_NAM|PHO_THONG|VIP") String kind,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal extra,
        @Min(1) @Max(3) int floor,@Min(1) @Max(30) int row,@Min(1) @Max(6) int column){}
    public List<Seat> parse(String source,String type){
        try {
            var seats=json.readValue(source,new TypeReference<List<Seat>>(){});
            if(seats==null||seats.isEmpty()||seats.size()>300)throw new IllegalArgumentException("Sơ đồ phải có 1–300 chỗ.");
            Set<String> codes=new HashSet<>(),positions=new HashSet<>();
            for(var s:seats){
                if(s==null||!validator.validate(s).isEmpty())throw new IllegalArgumentException("Mã chỗ, vị trí hoặc phụ thu không hợp lệ.");
                if(!codes.add(s.seat())||!positions.add(s.floor()+":"+s.row()+":"+s.column()))throw new IllegalArgumentException("Mã chỗ và vị trí không được trùng nhau.");
                if(("XE_KHACH".equals(type)&&!Set.of("GHE_NGOI","GIUONG_NAM").contains(s.kind()))||("TAU_THUYEN".equals(type)&&!Set.of("PHO_THONG","VIP").contains(s.kind())))
                    throw new IllegalArgumentException("Loại chỗ không phù hợp phương tiện.");
            }
            return seats;
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Dữ liệu sơ đồ chưa hợp lệ. Hãy tạo hoặc chỉnh các ô chỗ trong trình biên tập.");}
    }
    public List<Map<String,Object>> all(){return db.queryForList("SELECT p.*,(SELECT COUNT(*) FROM dbo.ChoPhuongTien s WHERE s.PhuongTienID=p.PhuongTienID) AS SucChua FROM dbo.PhuongTien p ORDER BY p.LoaiVe,p.MaPhuongTien");}
    public Map<String,Object> vehicle(int id,boolean lock){
        var rows=db.queryForList("SELECT * FROM dbo.PhuongTien "+(lock?"WITH (UPDLOCK,HOLDLOCK) ":"")+"WHERE PhuongTienID=?",id);
        if(rows.isEmpty())throw new IllegalArgumentException("Không tìm thấy phương tiện.");return rows.getFirst();
    }
    public List<Map<String,Object>> seats(int id){return db.queryForList("SELECT * FROM dbo.ChoPhuongTien WHERE PhuongTienID=? ORDER BY Tang,Hang,Cot",id);}
    /** Caller holds the vehicle lock. Allow thirty minutes for turnaround. */
    public void requireAvailable(int vehicleId,int tripId,String from,String to,java.time.LocalDateTime departure,java.time.LocalDateTime arrival){
        var rows=db.queryForList("SELECT KhoiHanh,DuKienDen,NoiDi,NoiDen FROM dbo.ChuyenDi WHERE PhuongTienID=? AND ChuyenDiID<>? AND TrangThai<>'DA_HUY' ORDER BY KhoiHanh",vehicleId,tripId);
        for(var row:rows){
            var start=((java.sql.Timestamp)row.get("KhoiHanh")).toLocalDateTime();var end=((java.sql.Timestamp)row.get("DuKienDen")).toLocalDateTime();
            if(start.isBefore(arrival.plusMinutes(30))&&end.plusMinutes(30).isAfter(departure))throw new IllegalArgumentException("Phương tiện trùng lịch hoặc chưa đủ 30 phút quay đầu giữa hai chuyến.");
        }
    }
    public void requireConnection(int vehicleId,int tripId,String from,String to,java.time.LocalDateTime departure,java.time.LocalDateTime arrival){
        var previous=db.queryForList("SELECT TOP(1) NoiDen FROM dbo.ChuyenDi WHERE PhuongTienID=? AND ChuyenDiID<>? AND TrangThai<>'DA_HUY' AND DuKienDen<=? ORDER BY DuKienDen DESC",String.class,vehicleId,tripId,java.sql.Timestamp.valueOf(departure));
        var next=db.queryForList("SELECT TOP(1) NoiDi FROM dbo.ChuyenDi WHERE PhuongTienID=? AND ChuyenDiID<>? AND TrangThai<>'DA_HUY' AND KhoiHanh>=? ORDER BY KhoiHanh",String.class,vehicleId,tripId,java.sql.Timestamp.valueOf(arrival));
        if(!previous.isEmpty()&&!previous.getFirst().strip().equalsIgnoreCase(from.strip()))throw new IllegalArgumentException("Điểm kết thúc chuyến trước không khớp điểm đi. Cần lập lịch chuyến về / điều chuyển trước khi mở bán.");
        if(!next.isEmpty()&&!to.strip().equalsIgnoreCase(next.getFirst().strip()))throw new IllegalArgumentException("Điểm đến không khớp điểm đi chuyến kế tiếp. Cần hoàn tất lịch nối chuyến trước khi mở bán.");
    }
    public VehicleForm form(int id){
        var p=vehicle(id,false);
        try {
            var layout=seats(id).stream().map(s->new Seat((String)s.get("MaCho"),(String)s.get("LoaiCho"),(BigDecimal)s.get("PhuThu"),((Number)s.get("Tang")).intValue(),((Number)s.get("Hang")).intValue(),((Number)s.get("Cot")).intValue())).toList();
            return new VehicleForm((String)p.get("MaPhuongTien"),(String)p.get("TenPhuongTien"),(String)p.get("SoDangKy"),(String)p.get("DonViVanChuyen"),(String)p.get("LoaiVe"),(Boolean)p.get("HoatDong"),json.writeValueAsString(layout));
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    @Transactional(rollbackFor = Exception.class)
    public int save(Integer id,VehicleForm form,String actor){
        if(!validator.validate(form).isEmpty())throw new IllegalArgumentException("Kiểm tra thông tin phương tiện.");
        var layout=parse(form.layout(),form.type());
        if(id==null){
            var key=new GeneratedKeyHolder();
            db.update(c->{var s=c.prepareStatement("INSERT dbo.PhuongTien(MaPhuongTien,TenPhuongTien,SoDangKy,DonViVanChuyen,LoaiVe,HoatDong) VALUES(?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
                s.setString(1,form.code());s.setString(2,form.name().strip());s.setString(3,form.registration().strip());s.setString(4,form.operator().strip());s.setString(5,form.type());s.setBoolean(6,form.active());return s;},key);
            id=Objects.requireNonNull(key.getKey()).intValue();
        }else{
            vehicle(id,true);
            if(db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi WHERE PhuongTienID=? AND TrangThai<>'DA_HUY' AND DuKienDen>SYSUTCDATETIME()",Integer.class,id)>0)
                throw new IllegalArgumentException("Phương tiện đang được phân công chuyến chưa kết thúc. Không thể đổi thông tin hoặc sơ đồ lúc này.");
            db.update("UPDATE dbo.PhuongTien SET MaPhuongTien=?,TenPhuongTien=?,SoDangKy=?,DonViVanChuyen=?,LoaiVe=?,HoatDong=? WHERE PhuongTienID=?",form.code(),form.name().strip(),form.registration().strip(),form.operator().strip(),form.type(),form.active(),id);
            // Only replace this vehicle's reusable template; existing trip seat snapshots stay intact.
            db.update("DELETE FROM dbo.ChoPhuongTien WHERE PhuongTienID=?",id);
        }
        for(var s:layout)db.update("INSERT dbo.ChoPhuongTien VALUES(?,?,?,?,?,?,?)",id,s.seat(),s.kind(),s.extra(),s.floor(),s.row(),s.column());
        db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong) VALUES(?,'LUU_PHUONG_TIEN',?)",actor,form.code());
        return id;
    }
    /**
     * Sơ đồ mẫu theo loại xe. Lưới 6 cột, cột 1 là phía ghế tài xế (bên trái), cửa lên xuống bên phải.
     * Xe giường nằm thường có 3 dãy giường (cột 1, 3, 5) và 2 lối đi; tầng dưới mã A, tầng trên mã B.
     * Một số xe có hàng cuối 5 giường liền nhau; xe limousine giường phòng có 2 dãy (cột 1 và 5).
     */
    public static List<Seat> templateSeats(String model){
        var list=new ArrayList<Seat>();
        switch(model){
            case "seat28"->grid(list,"GHE_NGOI",1,7,new int[]{1,2,4,5},0);
            case "sleep36"->{grid(list,"GIUONG_NAM",1,6,new int[]{1,3,5},0);grid(list,"GIUONG_NAM",2,6,new int[]{1,3,5},0);}
            case "sleep41"->{grid(list,"GIUONG_NAM",1,6,new int[]{1,3,5},0);backRow(list,1,7,5);grid(list,"GIUONG_NAM",2,6,new int[]{1,3,5},0);}
            case "limo24"->{grid(list,"GIUONG_NAM",1,6,new int[]{1,5},0);grid(list,"GIUONG_NAM",2,6,new int[]{1,5},0);}
            case "boat40"->grid(list,"PHO_THONG",1,10,new int[]{1,2,4,5},2);
            default->throw new IllegalArgumentException("Không có sơ đồ mẫu "+model);
        }
        return list;
    }
    private static void grid(List<Seat> list,String kind,int floor,int rows,int[] columns,int vipRows){
        String prefix=floor==1?"A":"B";
        for(int row=1;row<=rows;row++)for(int column:columns){
            boolean vip=row<=vipRows;
            list.add(new Seat(prefix+String.format("%02d",(int)list.stream().filter(x->x.floor()==floor).count()+1),vip?"VIP":kind,BigDecimal.valueOf(vip?100000:0),floor,row,column));
        }
    }
    /** Hàng cuối xe giường nằm: n giường liền nhau trên toàn bộ chiều ngang (không có lối đi). */
    private static void backRow(List<Seat> list,int floor,int row,int count){
        String prefix=floor==1?"A":"B";
        for(int column=1;column<=count;column++)
            list.add(new Seat(prefix+String.format("%02d",(int)list.stream().filter(x->x.floor()==floor).count()+1),"GIUONG_NAM",BigDecimal.ZERO,floor,row,column));
    }
    private record Template(String code,String name,String model,String type){}
    private static final List<Template> TEMPLATES=List.of(
        new Template("RVT-MAU-1","Xe ghế ngồi 28 chỗ · mẫu","seat28","XE_KHACH"),
        new Template("RVT-MAU-2","Xe giường nằm 36 chỗ · mẫu","sleep36","XE_KHACH"),
        new Template("RVT-MAU-3","Tàu 40 chỗ · mẫu","boat40","TAU_THUYEN"),
        new Template("RVT-MAU-4","Xe giường nằm 41 chỗ (hàng cuối 5 giường) · mẫu","sleep41","XE_KHACH"),
        new Template("RVT-MAU-5","Xe limousine giường phòng 24 chỗ · mẫu","limo24","XE_KHACH"));
    @Transactional(rollbackFor = Exception.class)
    public void seed() throws com.fasterxml.jackson.core.JsonProcessingException {
        int n=0;
        for(var t:TEMPLATES){
            n++;
            if(db.queryForObject("SELECT COUNT(*) FROM dbo.PhuongTien WHERE MaPhuongTien=?",Integer.class,t.code())>0)continue;
            save(null,new VehicleForm(t.code(),t.name(),"DEMO-"+n,"RVT mẫu",t.type(),true,json.writeValueAsString(templateSeats(t.model()))),"SYSTEM");
        }
    }
}
