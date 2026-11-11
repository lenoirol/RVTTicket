package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rvt.ticket.strategy.TicketStrategyRegistry;

@Service
public class TicketService {
    private final JdbcTemplate db;
    private final PlaceService places;private final FleetService fleet;
    private final WalletService wallet;private final NotificationService notifications;
    private final LegacyOrderService legacy;
    private final TicketStrategyRegistry strategies;
    public TicketService(JdbcTemplate db,PlaceService places,FleetService fleet,WalletService wallet,NotificationService notifications,LegacyOrderService legacy,TicketStrategyRegistry strategies) { this.db=db;this.places=places;this.fleet=fleet;this.wallet=wallet;this.notifications=notifications;this.legacy=legacy;this.strategies=strategies; }

    /** Số liệu thật cho trang chủ: chuyến mở bán 7 ngày tới, số tuyến, tỉnh và địa điểm. */
    public Map<String,Object> homeStats() {
        return db.queryForMap("""
            SELECT (SELECT COUNT(*) FROM dbo.ChuyenDi WHERE TrangThai='MO_BAN' AND KhoiHanh>SYSUTCDATETIME() AND KhoiHanh<DATEADD(DAY,7,SYSUTCDATETIME())) AS SoChuyen,
                   (SELECT COUNT(*) FROM (SELECT DISTINCT NoiDi,NoiDen FROM dbo.ChuyenDi WHERE TrangThai='MO_BAN' AND KhoiHanh>SYSUTCDATETIME()) r) AS SoTuyen,
                   (SELECT COUNT(*) FROM dbo.TinhThanh) AS SoTinh,
                   (SELECT COUNT(*) FROM dbo.DiaDiem) AS SoDiaDiem
            """);
    }
    /** Các chuyến sắp chạy cho bảng giờ khởi hành ở trang chủ (giờ Việt Nam). */
    public List<Map<String,Object>> nextDepartures(int count) {
        // Mỗi mốc giờ lấy một chuyến để bảng không lặp lại cùng một giờ.
        return db.queryForList("SELECT TOP (?) * FROM (SELECT c.ChuyenDiID,c.LoaiVe,c.NoiDi,c.NoiDen,c.DonViVanChuyen,c.GiaCoBan,DATEADD(HOUR,7,c.KhoiHanh) AS GioDi,"
            +"ROW_NUMBER() OVER(PARTITION BY c.KhoiHanh ORDER BY CASE WHEN c.LoaiVe='TAU_THUYEN' THEN 0 ELSE 1 END,c.ChuyenDiID) AS Thu "
            +"FROM dbo.ChuyenDi c WHERE c.TrangThai='MO_BAN' AND c.KhoiHanh>DATEADD(MINUTE,30,SYSUTCDATETIME()) AND c.KhoiHanh<DATEADD(DAY,2,SYSUTCDATETIME()) AND "+TripReadiness.configured("c")+") x WHERE x.Thu=1 ORDER BY x.GioDi",count);
    }
    public List<Map<String,Object>> search(String type, String from, String to, LocalDate date) {
        var args=new ArrayList<Object>();args.add(type);args.add(type);
        String fromFilter=placeFilter("c.NoiDi",places.searchNames(from),args);
        String toFilter=placeFilter("c.NoiDen",places.searchNames(to),args);
        args.add(Timestamp.valueOf(date.atStartOfDay().minusHours(7)));args.add(Timestamp.valueOf(date.plusDays(1).atStartOfDay().minusHours(7)));
        return db.queryForList("""
            SELECT c.*, ISNULL(m.DiemDon,c.NoiDi) AS DiemDon,ISNULL(m.DiemTra,c.NoiDen) AS DiemTra,
              ISNULL(m.TienIch,N'') AS TienIch,ISNULL(m.MoTa,N'') AS MoTa,
              COALESCE(c.TenPhuongTienSnapshot,CASE WHEN c.LoaiVe='TAU_THUYEN' THEN N'Tàu / thuyền' WHEN EXISTS(SELECT 1 FROM dbo.ChoNgoi z WHERE z.ChuyenDiID=c.ChuyenDiID AND z.LoaiCho='GIUONG_NAM') THEN N'Xe giường nằm' ELSE N'Xe ghế ngồi' END) AS TenPhuongTien,
              (SELECT COUNT(*) FROM dbo.ChoNgoi s WHERE s.ChuyenDiID=c.ChuyenDiID
              AND s.TrangThai='SAN_SANG' AND NOT EXISTS (SELECT 1 FROM dbo.DangKyVe v
                WHERE v.ChuyenDiID=s.ChuyenDiID AND v.MaCho=s.MaCho AND (v.DangChiemCho=1 OR v.TrangThai='DA_SU_DUNG')
                  AND (v.TrangThai<>'GIU_CHO' OR v.HanThanhToan>SYSUTCDATETIME()))) AS ConCho
            FROM dbo.ChuyenDi c LEFT JOIN dbo.ThongTinChuyen m ON m.ChuyenDiID=c.ChuyenDiID
            WHERE c.TrangThai='MO_BAN' AND c.KhoiHanh>DATEADD(MINUTE,30,SYSUTCDATETIME())
            AND (?='' OR c.LoaiVe=?) AND %s AND %s AND %s
            AND c.KhoiHanh>=? AND c.KhoiHanh<? ORDER BY c.KhoiHanh
            """.formatted(fromFilter,toFilter,TripReadiness.configured("c")), args.toArray());
    }
    private String placeFilter(String column,List<String> names,List<Object> args){
        if(names.isEmpty())return "1=1";args.addAll(names);
        return column+" IN ("+String.join(",",Collections.nCopies(names.size(),"?"))+")";
    }
    public List<Map<String,Object>> nextDates(String type,String from,String to,LocalDate date,int count,String period){
        var args=new ArrayList<Object>();args.add(type);args.add(type);
        String a=placeFilter("c.NoiDi",places.searchNames(from),args),b=placeFilter("c.NoiDen",places.searchNames(to),args);
        args.add(java.sql.Date.valueOf(date));args.add(java.sql.Date.valueOf(date.plusDays(30)));args.add(count);
        String hours=switch(period){case "morning"->" AND DATEPART(HOUR,DATEADD(HOUR,7,c.KhoiHanh))<12";case "afternoon"->" AND DATEPART(HOUR,DATEADD(HOUR,7,c.KhoiHanh)) BETWEEN 12 AND 17";case "evening"->" AND DATEPART(HOUR,DATEADD(HOUR,7,c.KhoiHanh))>=18";default->"";};
        return db.queryForList("""
            SELECT TOP 7 CAST(DATEADD(HOUR,7,c.KhoiHanh) AS DATE) AS Ngay,COUNT(*) AS SoChuyen,MIN(c.GiaCoBan) AS GiaTu
            FROM dbo.ChuyenDi c WHERE c.TrangThai='MO_BAN' AND c.KhoiHanh>DATEADD(MINUTE,30,SYSUTCDATETIME())
            AND (?='' OR c.LoaiVe=?) AND %s AND %s AND %s
            AND CAST(DATEADD(HOUR,7,c.KhoiHanh) AS DATE)>=? AND CAST(DATEADD(HOUR,7,c.KhoiHanh) AS DATE)<?
            AND (SELECT COUNT(*) FROM dbo.ChoNgoi s WHERE s.ChuyenDiID=c.ChuyenDiID AND s.TrangThai='SAN_SANG'
              AND NOT EXISTS(SELECT 1 FROM dbo.DangKyVe v WHERE v.ChuyenDiID=s.ChuyenDiID AND v.MaCho=s.MaCho
                AND (v.DangChiemCho=1 OR v.TrangThai='DA_SU_DUNG') AND (v.TrangThai<>'GIU_CHO' OR v.HanThanhToan>SYSUTCDATETIME())))>=? %s
            GROUP BY CAST(DATEADD(HOUR,7,c.KhoiHanh) AS DATE) ORDER BY Ngay
            """.formatted(a,b,TripReadiness.configured("c"),hours),args.toArray());
    }
    public List<String> origins() { return db.queryForList("SELECT DISTINCT NoiDi FROM dbo.ChuyenDi ORDER BY NoiDi",String.class); }
    public List<String> destinations() { return db.queryForList("SELECT DISTINCT NoiDen FROM dbo.ChuyenDi ORDER BY NoiDen",String.class); }
    public Map<String,Object> trip(int id) {
        var rows=db.queryForList("SELECT c.*,ISNULL(m.DiemDon,c.NoiDi) AS DiemDon,ISNULL(m.DiemTra,c.NoiDen) AS DiemTra,ISNULL(m.TienIch,N'') AS TienIch,ISNULL(m.MoTa,N'') AS MoTa,COALESCE(c.TenPhuongTienSnapshot,CASE WHEN c.LoaiVe='TAU_THUYEN' THEN N'Tàu / thuyền' WHEN EXISTS(SELECT 1 FROM dbo.ChoNgoi z WHERE z.ChuyenDiID=c.ChuyenDiID AND z.LoaiCho='GIUONG_NAM') THEN N'Xe giường nằm' ELSE N'Xe ghế ngồi' END) AS TenPhuongTien FROM dbo.ChuyenDi c LEFT JOIN dbo.ThongTinChuyen m ON m.ChuyenDiID=c.ChuyenDiID WHERE c.ChuyenDiID=?",id);
        if(rows.isEmpty()) throw new IllegalArgumentException("Không tìm thấy chuyến đi.");
        return rows.getFirst();
    }
    public List<Map<String,Object>> seats(int id) {
        return db.queryForList("""
            SELECT s.*, m.Tang,m.Hang,m.Cot,
             CASE WHEN s.TrangThai<>'SAN_SANG' OR EXISTS(SELECT 1 FROM dbo.DangKyVe v
              WHERE v.ChuyenDiID=s.ChuyenDiID AND v.MaCho=s.MaCho AND (v.DangChiemCho=1 OR v.TrangThai='DA_SU_DUNG')
              AND (v.TrangThai<>'GIU_CHO' OR v.HanThanhToan>SYSUTCDATETIME())) THEN 1 ELSE 0 END AS DaDat
            FROM dbo.ChoNgoi s LEFT JOIN dbo.SoDoCho m ON m.ChuyenDiID=s.ChuyenDiID AND m.MaCho=s.MaCho
            WHERE s.ChuyenDiID=? ORDER BY m.Tang,m.Hang,m.Cot,s.MaCho
            """,id);
    }

    @Transactional(rollbackFor = Exception.class)
    public String book(int tripId, BookingForm form) {
        return book(tripId,form,null);
    }
    @Transactional(rollbackFor = Exception.class)
    public String book(int tripId, BookingForm form,String username) {
        if(username!=null&&db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",Integer.class,username)!=1)
            throw new IllegalArgumentException("Đăng nhập tài khoản khách hàng trước khi đặt vé.");
        if(username!=null&&db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanKhach WHERE TenDangNhap=? AND BiKhoa=1",Integer.class,username)>0)
            throw new IllegalArgumentException("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị.");
        // Serializes seat allocation on this trip, including concurrent requests.
        var trips=db.queryForList("SELECT * FROM dbo.ChuyenDi WITH (UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",tripId);
        if(trips.isEmpty()) throw new IllegalArgumentException("Không tìm thấy chuyến đi.");
        var trip=trips.getFirst();
        // A retried POST returns the original order, even if its hold has expired.
        // Trip lock serializes duplicate submissions; the filtered unique index is the final guard.
        if(form.getRequestKey()!=null){
            if(username==null||!form.getRequestKey().matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Yêu cầu đặt vé không hợp lệ.");
            var prior=db.queryForList("SELECT o.MaDon,o.ChuyenDiID,a.TenDangNhap FROM dbo.DonDatVe o JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=o.TaiKhoanID WHERE o.YeuCauDat=?",form.getRequestKey());
            if(!prior.isEmpty()){
                var original=prior.getFirst();
                if(!username.equals(original.get("TenDangNhap"))||!Integer.valueOf(tripId).equals(original.get("ChuyenDiID")))throw new IllegalArgumentException("Yêu cầu này đã được sử dụng cho một đơn khác.");
                return (String)original.get("MaDon");
            }
        }
        LocalDateTime now=databaseNow();
        LocalDateTime departure=((Timestamp)trip.get("KhoiHanh")).toLocalDateTime();
        if(!"MO_BAN".equals(trip.get("TrangThai")) || !departure.isAfter(now.plusMinutes(30)))
            throw new IllegalArgumentException("Chuyến đã đóng bán hoặc còn dưới 30 phút trước giờ khởi hành.");
        requireConfigured(tripId);
        expireTrip(tripId);
        if(form.getPassengers().isEmpty() || form.getPassengers().size()>8)
            throw new IllegalArgumentException("Mỗi đơn được đặt từ 1 đến 8 hành khách.");
        String type=(String)trip.get("LoaiVe");
        var rule=rules(type);
        Set<String> chosen=new HashSet<>();
        List<BigDecimal> prices=new ArrayList<>(),fees=new ArrayList<>();
        BigDecimal total=BigDecimal.ZERO;
        LocalDate travelDate=departure.plusHours(7).toLocalDate();
        // Strategy Pattern: quy trình, validation và chính sách phí riêng của từng loại vé nằm trong TicketStrategy.
        var strategy=strategies.of(type);
        strategy.validateOrder(form.getPassengers(),travelDate,now.plusHours(7).toLocalDate());
        for(var passenger:form.getPassengers()) {
            if(!chosen.add(passenger.getSeat())) throw new IllegalArgumentException("Mỗi hành khách phải có một chỗ riêng.");
            strategy.validateDocument(passenger);
            BigDecimal fee=strategy.luggageFee(passenger.getLuggage(),(BigDecimal)rule.get("MienPhiKg"),(BigDecimal)rule.get("ToiDaKg"),(BigDecimal)rule.get("GiaMoiKg"));
            var available=db.queryForList("""
                SELECT s.PhuThu,s.LoaiCho FROM dbo.ChoNgoi s WHERE s.ChuyenDiID=? AND s.MaCho=? AND s.TrangThai='SAN_SANG'
                AND NOT EXISTS (SELECT 1 FROM dbo.DangKyVe v WHERE v.ChuyenDiID=s.ChuyenDiID AND v.MaCho=s.MaCho AND (v.DangChiemCho=1 OR v.TrangThai='DA_SU_DUNG'))
                """,tripId,passenger.getSeat());
            if(available.isEmpty()) throw new IllegalArgumentException("Chỗ "+passenger.getSeat()+" vừa được đặt hoặc không hợp lệ. Hãy chọn lại.");
            String group=strategy.fareGroup(passenger,travelDate);
            strategy.validateIdentity(passenger,group);
            BigDecimal price=FarePolicy.passengerFare(group,(BigDecimal)trip.get("GiaCoBan"),(BigDecimal)trip.get("GiaTreEm"),(BigDecimal)trip.get("GiaCaoTuoi"),"VIP".equals(available.getFirst().get("LoaiCho"))).add((BigDecimal)available.getFirst().get("PhuThu"));
            prices.add(price);fees.add(fee);total=total.add(price).add(fee);
        }
        int customer=insert("INSERT dbo.KhachHang(HoTen,SoDienThoai,Email) VALUES(?,?,?)",form.getName().strip(),form.getPhone(),emptyNull(form.getEmail()));
        String code="RVT"+UUID.randomUUID().toString().replace("-", "").substring(0,12).toUpperCase();
        LocalDateTime deadline=now.plusMinutes(15);
        int order=insert("INSERT dbo.DonDatVe(MaDon,KhachHangID,ChuyenDiID,TongTien,NgayTao,HanThanhToan) VALUES(?,?,?,?,?,?)",code,customer,tripId,total,Timestamp.valueOf(now),Timestamp.valueOf(deadline));
        if(form.getRequestKey()!=null)db.update("UPDATE dbo.DonDatVe SET YeuCauDat=? WHERE DonDatVeID=?",form.getRequestKey(),order);
        db.update("UPDATE dbo.DonDatVe SET GioHanHuy=24,TaiKhoanID=(SELECT TaiKhoanID FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?) WHERE DonDatVeID=?",username,order);
        var profile=trip(tripId);
        db.update("UPDATE dbo.DonDatVe SET DiemDonSnapshot=?,DiemTraSnapshot=? WHERE DonDatVeID=?",profile.get("DiemDon"),profile.get("DiemTra"),order);
        for(int i=0;i<form.getPassengers().size();i++) {
            var p=form.getPassengers().get(i);
            int ticket=insert("INSERT dbo.DangKyVe(DangKyCode,KhachHangID,ChuyenDiID,LoaiVe,TenHanhKhach,MaCho,NgayDangKy,HanThanhToan,GiaVe) VALUES(?,?,?,?,?,?,?,?,?)",code+"-"+(i+1),customer,tripId,type,p.getName().strip(),p.getSeat(),Timestamp.valueOf(now),Timestamp.valueOf(deadline),prices.get(i));
            byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
            db.update("UPDATE dbo.DangKyVe SET QrToken=? WHERE DangKyID=?",HexFormat.of().formatHex(secret).toUpperCase(Locale.ROOT),ticket);
            db.update("UPDATE dbo.DangKyVe SET NgaySinh=?,NhomHanhKhach=?,LoaiGiayTo=? WHERE DangKyID=?",p.getBirthDate()==null?null:java.sql.Date.valueOf(p.getBirthDate()),strategy.fareGroup(p,travelDate),strategy.storedDocumentType(p),ticket);
            db.update("INSERT dbo.DonDatVeChiTiet VALUES(?,?)",order,ticket);
            strategy.insertDetails(db,ticket,p,profile);
            if(fees.get(i).signum()>0) db.update("INSERT dbo.ChiTietPhi(DangKyID,MaPhi,TenPhi,SoTien) VALUES(?,'HANH_LY',N'Phí hành lý vượt định mức',?)",ticket,fees.get(i));
        }
        notifications.order(order(code,form.getPhone()),"BOOK","Đã giữ chỗ","Đơn "+code+" đã được giữ chỗ 15 phút. Hoàn tất thanh toán để nhận vé.");
        return code;
    }

    public Map<String,Object> order(String code, String phone) {
        var rows=db.queryForList("""
            SELECT o.*,k.HoTen,k.SoDienThoai,k.Email,c.NoiDi,c.NoiDen,c.KhoiHanh,c.DuKienDen,c.LoaiVe,c.DonViVanChuyen,c.MaChuyen,c.TrangThai AS TrangThaiChuyen,c.LyDoHuy AS LyDoHuyChuyen,c.PhuongTienID,c.TenPhuongTienSnapshot,c.SoDangKySnapshot,
             COALESCE(o.DiemDonSnapshot,m.DiemDon,c.NoiDi) AS DiemDon,COALESCE(o.DiemTraSnapshot,m.DiemTra,c.NoiDen) AS DiemTra
            FROM dbo.DonDatVe o JOIN dbo.KhachHang k ON k.KhachHangID=o.KhachHangID
            JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID LEFT JOIN dbo.ThongTinChuyen m ON m.ChuyenDiID=c.ChuyenDiID WHERE o.MaDon=? AND k.SoDienThoai=?
            """,code,phone);
        if(rows.isEmpty()) throw new IllegalArgumentException("Mã đơn hoặc số điện thoại không đúng.");
        var result=rows.getFirst();
        if("GIU_CHO".equals(result.get("TrangThai")) && !((Timestamp)result.get("HanThanhToan")).toLocalDateTime().isAfter(databaseNow()))
            result.put("TrangThai","HET_HAN");
        var tickets=db.queryForList("SELECT v.*,ISNULL((SELECT SUM(p.SoTien) FROM dbo.ChiTietPhi p WHERE p.DangKyID=v.DangKyID),0) AS TongPhi,ISNULL(x.KhoiLuongHanhLy,t.KhoiLuongHanhLy) AS HanhLy,t.SoGiayTo FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID LEFT JOIN dbo.VeXeKhach x ON x.DangKyID=v.DangKyID LEFT JOIN dbo.VeTauThuyen t ON t.DangKyID=v.DangKyID WHERE d.DonDatVeID=? ORDER BY v.DangKyID",result.get("DonDatVeID"));
        result.put("tickets",tickets);
        var now=databaseNow();var departure=((Timestamp)result.get("KhoiHanh")).toLocalDateTime();
        result.put("DaCoHanhKhachLen",tickets.stream().anyMatch(t->"DA_SU_DUNG".equals(t.get("TrangThai"))));
        result.put("CoTheKhachHuy",("GIU_CHO".equals(result.get("TrangThai"))||("DA_THANH_TOAN".equals(result.get("TrangThai"))&&FarePolicy.refundAllowed(departure,now)))&&!Boolean.TRUE.equals(result.get("DaCoHanhKhachLen")));
        result.put("CoTheAdminHuy",Set.of("GIU_CHO","DA_THANH_TOAN").contains((String)result.get("TrangThai"))&&departure.isAfter(now)&&!Boolean.TRUE.equals(result.get("DaCoHanhKhachLen")));
        result.put("TienSeHoan",db.queryForObject("SELECT COALESCE(SUM(p.SoTien),0) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=? AND p.TrangThai='THANH_CONG'",BigDecimal.class,result.get("DonDatVeID")));
        result.put("CoTheCheckin","DA_THANH_TOAN".equals(result.get("TrangThai"))&&!Set.of("DA_HUY","HOAN_THANH").contains((String)result.get("TrangThaiChuyen"))
            &&!now.isBefore(departure.minusHours(2))&&!now.isAfter(departure.plusHours(2))
            &&((Timestamp)result.get("DuKienDen")).toLocalDateTime().isAfter(now));
        result.put("TongGiaVe",tickets.stream().map(t->(BigDecimal)t.get("GiaVe")).reduce(BigDecimal.ZERO,BigDecimal::add));
        result.put("TongPhi",tickets.stream().map(t->(BigDecimal)t.get("TongPhi")).reduce(BigDecimal.ZERO,BigDecimal::add));
        result.put("HanHuy",Timestamp.valueOf(departure.minusHours(24)));
        result.put("CoMatLuc",Timestamp.valueOf(departure.minusMinutes(strategies.of((String)result.get("LoaiVe")).boardingLeadMinutes())));
        result.put("payments",db.queryForList("SELECT p.MaGiaoDich,p.SoTien,p.PhuongThuc,p.TrangThai,p.ThoiGian,p.HoanLuc,v.MaCho FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID WHERE d.DonDatVeID=? ORDER BY p.ThanhToanID",result.get("DonDatVeID")));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void pay(String code,String phone) {
        pay(code,phone,"DEMO",null);
    }
    @Transactional(rollbackFor = Exception.class)
    public void pay(String code,String phone,String method,String username) {
        if(!Set.of("DEMO","RVT").contains(method))throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        var o=lockedOrder(code,phone);
        if(username!=null)ownerPhone(code,username);
        if(Set.of("DA_THANH_TOAN","DA_SU_DUNG").contains((String)o.get("TrangThai"))) return;
        if(!"GIU_CHO".equals(o.get("TrangThai")) || !((Timestamp)o.get("HanThanhToan")).toLocalDateTime().isAfter(databaseNow()))
            throw new IllegalArgumentException("Đơn đã hết hạn hoặc không còn có thể thanh toán.");
        ReservationState.require(o.get("TrangThai"),ReservationState.CONFIRMED); // CREATED → CONFIRMED
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.GiaoDichVNPay WHERE DonDatVeID=? AND TrangThai='PENDING' AND HetHanLuc>SYSUTCDATETIME()",Integer.class,o.get("DonDatVeID"))>0)
            throw new IllegalArgumentException("Đơn đang chờ kết quả VNPay. Kiểm tra giao dịch hoặc chờ hết hạn trước khi đổi phương thức.");
        var tickets=db.queryForList("SELECT v.* FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=?",o.get("DonDatVeID"));
        if("RVT".equals(method))wallet.debit(o,username,(BigDecimal)o.get("TongTien"));
        for(var t:tickets) {
            if(!"GIU_CHO".equals(t.get("TrangThai"))) throw new IllegalArgumentException("Vé không còn khả dụng.");
            BigDecimal fee=db.queryForObject("SELECT ISNULL(SUM(SoTien),0) FROM dbo.ChiTietPhi WHERE DangKyID=?",BigDecimal.class,t.get("DangKyID"));
            db.update("INSERT dbo.ThanhToan(DangKyID,MaGiaoDich,SoTien,PhuongThuc,TrangThai) VALUES(?,?,?,?,'THANH_CONG')",t.get("DangKyID"),("RVT".equals(method)?"RVT-":"DEMO-")+UUID.randomUUID(),((BigDecimal)t.get("GiaVe")).add(fee),"RVT".equals(method)?"SO_DU_RVT":"CHUYEN_KHOAN");
            db.update("UPDATE dbo.DangKyVe SET TrangThai='DA_THANH_TOAN' WHERE DangKyID=?",t.get("DangKyID"));
        }
        db.update("UPDATE dbo.DonDatVe SET TrangThai='DA_THANH_TOAN' WHERE DonDatVeID=?",o.get("DonDatVeID"));
        notifications.order(o,"PAY","Thanh toán thành công","Vé đã xác nhận. "+("RVT".equals(method)?"Đã trừ số dư RVT.":"Giao dịch thử, không thu tiền thật."));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(String code,String phone) {
        cancel(code,phone,"");
    }
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String code,String phone,String reason) {
        if(reason==null||reason.strip().length()>500)throw new IllegalArgumentException("Lý do hủy tối đa 500 ký tự.");
        var o=lockedOrder(code,phone);
        if("DA_HUY".equals(o.get("TrangThai"))) return;
        if(!"GIU_CHO".equals(o.get("TrangThai")) && !"DA_THANH_TOAN".equals(o.get("TrangThai")))
            throw new IllegalArgumentException("Đơn không còn có thể hủy.");
        ReservationState.require(o.get("TrangThai"),ReservationState.CANCELLED); // CREATED/CONFIRMED → CANCELLED
        if(!FarePolicy.refundAllowed(((Timestamp)o.get("KhoiHanh")).toLocalDateTime(),databaseNow()) && "DA_THANH_TOAN".equals(o.get("TrangThai")))
            throw new IllegalArgumentException("Vé đã thanh toán chỉ được hủy trước giờ khởi hành ít nhất 24 giờ.");
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=? AND v.TrangThai='DA_SU_DUNG'",Integer.class,o.get("DonDatVeID"))>0)
            throw new IllegalArgumentException("Đơn đã có hành khách lên xe/tàu, không thể hủy.");
        wallet.refund(o);
        db.update("UPDATE v SET TrangThai='DA_HUY',DangChiemCho=0 FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=? AND v.TrangThai IN ('GIU_CHO','DA_THANH_TOAN')",o.get("DonDatVeID"));
        db.update("UPDATE t SET TrangThai='DA_HOAN',HoanLuc=SYSUTCDATETIME() FROM dbo.ThanhToan t JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=t.DangKyID WHERE d.DonDatVeID=? AND t.TrangThai='THANH_CONG'",o.get("DonDatVeID"));
        db.update("UPDATE dbo.DonDatVe SET TrangThai='DA_HUY',HuyLuc=SYSUTCDATETIME(),LyDoHuy=? WHERE DonDatVeID=?",reason.isBlank()?"Khách hủy đơn":reason.strip(),o.get("DonDatVeID"));
        notifications.order(o,"CANCEL","Đơn vé đã hủy","Đã giải phóng chỗ. Số tiền đã thanh toán được hoàn 100% vào số dư RVT.");
    }
    private Map<String,Object> lockedOrder(String code,String phone) {
        // Lock trip before order: same lock ordering as allocator and expiry worker.
        var info=order(code,phone);
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH (UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",info.get("ChuyenDiID"));
        db.queryForList("SELECT DonDatVeID FROM dbo.DonDatVe WITH (UPDLOCK,HOLDLOCK) WHERE DonDatVeID=?",info.get("DonDatVeID"));
        return order(code,phone);
    }
    public List<Map<String,Object>> adminOrders() {
        return db.queryForList("SELECT TOP 100 o.*,k.HoTen,k.SoDienThoai,c.NoiDi,c.NoiDen,c.LoaiVe,(SELECT COUNT(*) FROM dbo.DonDatVeChiTiet d WHERE d.DonDatVeID=o.DonDatVeID) AS SoVe FROM dbo.DonDatVe o JOIN dbo.KhachHang k ON k.KhachHangID=o.KhachHangID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID ORDER BY o.NgayTao DESC");
    }
    public List<Map<String,Object>> adminTrips() { return db.queryForList("SELECT TOP 100 * FROM dbo.ChuyenDi ORDER BY KhoiHanh DESC"); }
    public Map<String,Object> rules(String type) {return db.queryForMap("SELECT * FROM dbo.QuyTacVe WHERE LoaiVe=?",type);}
    public List<Map<String,Object>> allRules() {return db.queryForList("SELECT * FROM dbo.QuyTacVe ORDER BY LoaiVe");}
    public String ownerPhone(String code,String username) {
        var rows=db.queryForList("SELECT k.SoDienThoai FROM dbo.DonDatVe o JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=o.TaiKhoanID JOIN dbo.KhachHang k ON k.KhachHangID=o.KhachHangID WHERE o.MaDon=? AND a.TenDangNhap=?",String.class,code,username);
        if(rows.isEmpty()) throw new IllegalArgumentException("Bạn không có quyền xem đơn vé này.");
        return rows.getFirst();
    }
    @Transactional(rollbackFor = Exception.class)
    public void saveRule(RuleForm f,String actor) {
        BigDecimal absoluteMax=strategies.of(f.type()).absoluteLuggageLimit();
        if(f.maximum().compareTo(absoluteMax)>0 || f.allowance().compareTo(f.maximum())>0) throw new IllegalArgumentException("Định mức miễn phí phải nhỏ hơn hoặc bằng giới hạn hành lý của phương tiện.");
        if(f.cancelHours()!=24)throw new IllegalArgumentException("Chính sách RVT: hủy trước ít nhất 24 giờ.");
        db.update("UPDATE dbo.QuyTacVe SET MienPhiKg=?,ToiDaKg=?,GiaMoiKg=?,GioHanHuy=24 WHERE LoaiVe=?",f.allowance(),f.maximum(),f.rate(),f.type());
        audit(actor,"CAP_NHAT_CHINH_SACH",f.type());
    }
    public void audit(String actor,String action,String target) {db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong) VALUES(?,?,?)",actor,action,target);}
    public List<Map<String,Object>> auditLog() {return db.queryForList("SELECT TOP 50 * FROM dbo.NhatKyThaoTac ORDER BY NhatKyID DESC");}
    @Transactional(rollbackFor = Exception.class)
    public void adminCancelOrder(String code,String reason,String actor) {
        if(reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("Nhập lý do hủy từ 1 đến 500 ký tự.");
        var info=staffOrder(code);var o=lockedOrder(code,(String)info.get("SoDienThoai"));
        if("DA_HUY".equals(o.get("TrangThai")))return;
        if(!Set.of("GIU_CHO","DA_THANH_TOAN").contains((String)o.get("TrangThai")))throw new IllegalArgumentException("Đơn đã hết hạn hoặc đã sử dụng, không thể hủy.");
        ReservationState.require(o.get("TrangThai"),ReservationState.CANCELLED);
        if(!((Timestamp)o.get("KhoiHanh")).toLocalDateTime().isAfter(databaseNow()))throw new IllegalArgumentException("Chuyến đã khởi hành, không thể hủy đơn.");
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=? AND v.TrangThai='DA_SU_DUNG'",Integer.class,o.get("DonDatVeID"))>0)
            throw new IllegalArgumentException("Đã có hành khách lên xe / tàu, không thể hủy đơn.");
        expireTrip(((Number)o.get("ChuyenDiID")).intValue());
        if("HET_HAN".equals(order(code,(String)info.get("SoDienThoai")).get("TrangThai")))throw new IllegalArgumentException("Đơn đã hết hạn.");
        wallet.refund(o);
        db.update("UPDATE v SET TrangThai='DA_HUY',DangChiemCho=0 FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=? AND v.TrangThai IN('GIU_CHO','DA_THANH_TOAN')",o.get("DonDatVeID"));
        db.update("UPDATE p SET TrangThai='DA_HOAN',HoanLuc=SYSUTCDATETIME() FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=? AND p.TrangThai='THANH_CONG'",o.get("DonDatVeID"));
        db.update("UPDATE dbo.DonDatVe SET TrangThai='DA_HUY',HuyLuc=SYSUTCDATETIME(),LyDoHuy=? WHERE DonDatVeID=?",reason.strip(),o.get("DonDatVeID"));
        notifications.order(o,"CANCEL","Quản trị đã hủy đơn",reason.strip()+". Số tiền đã thanh toán được hoàn vào số dư RVT.");
        audit(actor,"CANCEL",code);
    }
    public List<Map<String,Object>> staffOrders(String query) {
        String q="%"+query+"%";
        return db.queryForList("SELECT TOP 100 o.*,k.HoTen,k.SoDienThoai,c.NoiDi,c.NoiDen,c.KhoiHanh FROM dbo.DonDatVe o JOIN dbo.KhachHang k ON k.KhachHangID=o.KhachHangID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE o.MaDon LIKE ? OR k.SoDienThoai LIKE ? ORDER BY o.NgayTao DESC",q,q);
    }
    public Map<String,Object> staffOrder(String code) {
        String phone=db.queryForObject("SELECT k.SoDienThoai FROM dbo.DonDatVe o JOIN dbo.KhachHang k ON k.KhachHangID=o.KhachHangID WHERE o.MaDon=?",String.class,code);
        return order(code,phone);
    }
    @Transactional(rollbackFor = Exception.class)
    public void staffAction(String code,String action,Integer ticketId,String actor) {
        var info=staffOrder(code);String phone=(String)info.get("SoDienThoai");
        if("pay".equals(action)) pay(code,phone);
        else if("cancel".equals(action)) cancel(code,phone);
        else if("checkin".equals(action)) {
            var o=lockedOrder(code,phone);
            if(!"DA_THANH_TOAN".equals(o.get("TrangThai"))) throw new IllegalArgumentException("Chỉ kiểm tra lên xe/tàu cho đơn đã thanh toán.");
            ReservationState.require(o.get("TrangThai"),ReservationState.USED); // CONFIRMED → USED
            if(Set.of("DA_HUY","HOAN_THANH").contains((String)o.get("TrangThaiChuyen")))throw new IllegalArgumentException("Chuyến đã hủy hoặc đã hoàn thành, không thể xác nhận lên xe/tàu.");
            if(!((Timestamp)o.get("DuKienDen")).toLocalDateTime().isAfter(databaseNow()))throw new IllegalArgumentException("Chuyến đã kết thúc, không thể xác nhận lên xe/tàu.");
            var departure=((Timestamp)o.get("KhoiHanh")).toLocalDateTime();
            if(databaseNow().isBefore(departure.minusHours(2)) || databaseNow().isAfter(departure.plusHours(2)))
                throw new IllegalArgumentException("Kiểm tra lên xe/tàu được mở từ 2 giờ trước đến 2 giờ sau khởi hành.");
            int changed=db.update("UPDATE v SET TrangThai='DA_SU_DUNG',DangChiemCho=0 FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=? AND v.DangKyID=? AND v.TrangThai='DA_THANH_TOAN'",o.get("DonDatVeID"),ticketId);
            if(changed!=1) throw new IllegalArgumentException("Vé không thuộc đơn, chưa thanh toán hoặc đã được sử dụng.");
            if(db.queryForObject("SELECT COUNT(*) FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=? AND v.TrangThai<>'DA_SU_DUNG'",Integer.class,o.get("DonDatVeID"))==0)
                db.update("UPDATE dbo.DonDatVe SET TrangThai='DA_SU_DUNG' WHERE DonDatVeID=?",o.get("DonDatVeID"));
        } else throw new IllegalArgumentException("Thao tác không hợp lệ.");
        audit(actor,action.toUpperCase(),code+(ticketId==null?"":" / "+ticketId));
    }
    @Transactional(rollbackFor = Exception.class)
    public int createTrip(TripForm f,String actor) {
        int id=createTrip(f);audit(actor,"TAO_CHUYEN",String.valueOf(id));return id;
    }
    @Transactional(rollbackFor = Exception.class)
    public int createTrip(TripForm f) {
        if(f.getCode()!=null&&f.getCode().matches("RVT-[BT][0-9]+"))throw new IllegalArgumentException("Mã RVT-B / RVT-T theo số thứ tự được dành cho chuyến đã nhập. Hãy dùng mã theo tuyến, ví dụ RVT-DL-001.");
        if(!Set.of("BUS_SEAT","BUS_SLEEPER","BOAT").contains(f.getLayout())) throw new IllegalArgumentException("Sơ đồ không hợp lệ.");
        if(f.getFrom().strip().equalsIgnoreCase(f.getTo().strip())) throw new IllegalArgumentException("Điểm đi và điểm đến phải khác nhau.");
        if(!f.getArrival().isAfter(f.getDeparture())) throw new IllegalArgumentException("Giờ đến phải sau giờ khởi hành.");
        var departure=f.getDeparture().minusHours(7).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var arrival=f.getArrival().minusHours(7).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if(!arrival.isAfter(departure))throw new IllegalArgumentException("Giờ đến phải sau giờ khởi hành.");
        if(!departure.isAfter(databaseNow().plusMinutes(30))) throw new IllegalArgumentException("Khởi hành phải sau hiện tại ít nhất 30 phút.");
        Map<String,Object> vehicle=f.getVehicleId()==null?null:fleet.vehicle(f.getVehicleId(),true);
        var vehicleSeats=vehicle==null?List.<Map<String,Object>>of():fleet.seats(f.getVehicleId());
        if(vehicle!=null){
            if(!Boolean.TRUE.equals(vehicle.get("HoatDong"))||vehicleSeats.isEmpty())throw new IllegalArgumentException("Phương tiện chưa sẵn sàng hoặc chưa có sơ đồ.");
            fleet.requireAvailable(f.getVehicleId(),-1,f.getFrom(),f.getTo(),departure,arrival);
        }
        boolean boat=vehicle==null?"BOAT".equals(f.getLayout()):"TAU_THUYEN".equals(vehicle.get("LoaiVe")),sleeper="BUS_SLEEPER".equals(f.getLayout());
        int floors=sleeper?2:1,columns=sleeper?3:4,count=vehicle==null?f.getRows()*floors*columns:vehicleSeats.size();
        if(vehicle==null&&(f.getVipSeats()>count || (!boat && f.getVipSeats()>0))) throw new IllegalArgumentException("Chỗ VIP chỉ áp dụng cho tàu và không vượt sức chứa.");
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi c WHERE c.MaChuyen=? OR "+TripIdentity.sql("c")+"=?",Integer.class,f.getCode(),f.getCode())>0) throw new IllegalArgumentException("Mã chuyến đã tồn tại.");
        String type=boat?"TAU_THUYEN":"XE_KHACH";
        if(boat&&((f.getChildPrice()!=null&&f.getChildPrice().compareTo(f.getPrice())>0)||(f.getSeniorPrice()!=null&&f.getSeniorPrice().compareTo(f.getPrice())>0)))throw new IllegalArgumentException("Giá trẻ em / cao tuổi không vượt giá người lớn.");
        int id=insert("INSERT dbo.ChuyenDi(MaChuyen,LoaiVe,DonViVanChuyen,NoiDi,NoiDen,KhoiHanh,DuKienDen,TongSoCho,GiaCoBan) VALUES(?,?,?,?,?,?,?,?,?)",f.getCode(),type,vehicle==null?f.getOperator().strip():vehicle.get("DonViVanChuyen"),f.getFrom().strip(),f.getTo().strip(),Timestamp.valueOf(departure),Timestamp.valueOf(arrival),count,f.getPrice());
        db.update("UPDATE dbo.ChuyenDi SET MaDiemDi=?,MaDiemDen=? WHERE ChuyenDiID=?",places.destinationId(f.getFrom()),places.destinationId(f.getTo()),id);
        db.update("UPDATE dbo.ChuyenDi SET GiaTreEm=?,GiaCaoTuoi=? WHERE ChuyenDiID=?",boat?f.getChildPrice():null,boat?f.getSeniorPrice():null,id);
        if(vehicle!=null)db.update("UPDATE dbo.ChuyenDi SET PhuongTienID=?,TenPhuongTienSnapshot=?,SoDangKySnapshot=? WHERE ChuyenDiID=?",f.getVehicleId(),vehicle.get("TenPhuongTien"),vehicle.get("SoDangKy"),id);
        saveTripInfo(id,f);
        db.update("UPDATE dbo.ChuyenDi SET TrangThai='DONG_BAN' WHERE ChuyenDiID=?",id);
        if(vehicle!=null){
            for(var s:vehicleSeats){
                db.update("INSERT dbo.ChoNgoi(ChuyenDiID,MaCho,LoaiVe,LoaiCho,PhuThu) VALUES(?,?,?,?,?)",id,s.get("MaCho"),type,s.get("LoaiCho"),s.get("PhuThu"));
                db.update("INSERT dbo.SoDoCho(ChuyenDiID,MaCho,Tang,Hang,Cot) VALUES(?,?,?,?,?)",id,s.get("MaCho"),s.get("Tang"),s.get("Hang"),s.get("Cot"));
            }
        }else for(int i=0;i<count;i++) {
            int floor=i/(f.getRows()*columns)+1,index=i%(f.getRows()*columns);
            String seat=(floor==1?"A":"B")+String.format("%02d",index+1);
            boolean vip=boat && i<f.getVipSeats();
            db.update("INSERT dbo.ChoNgoi(ChuyenDiID,MaCho,LoaiVe,LoaiCho,PhuThu) VALUES(?,?,?,?,?)",id,seat,type,boat?(vip?"VIP":"PHO_THONG"):(sleeper?"GIUONG_NAM":"GHE_NGOI"),vip?f.getVipExtra():BigDecimal.ZERO);
            db.update("INSERT dbo.SoDoCho(ChuyenDiID,MaCho,Tang,Hang,Cot) VALUES(?,?,?,?,?)",id,seat,floor,index/columns+1,index%columns+1);
        }
        return id;
    }
    @Transactional(rollbackFor = Exception.class)
    public void saveTripInfo(int id,TripForm f) {
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH (UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",id);
        updateInfo(id,f.getPickup().isBlank()?f.getFrom():f.getPickup(),f.getDropoff().isBlank()?f.getTo():f.getDropoff(),f.getAmenities(),f.getDescription(),null);
    }
    @Transactional(rollbackFor = Exception.class)
    public void updateInfo(int id,String pickup,String dropoff,String amenities,String description,String actor) {
        trip(id);
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH (UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",id);
        if(db.update("UPDATE dbo.ThongTinChuyen SET DiemDon=?,DiemTra=?,TienIch=?,MoTa=? WHERE ChuyenDiID=?",pickup,dropoff,amenities,description,id)==0)
            db.update("INSERT dbo.ThongTinChuyen VALUES(?,?,?,?,?)",id,pickup,dropoff,amenities,description);
        if(actor!=null)audit(actor,"CAP_NHAT_CHUYEN",String.valueOf(id));
    }
    @Transactional(rollbackFor = Exception.class)
    public void changeSale(int id,String status,String actor) {
        changeSale(id,status);audit(actor,status,String.valueOf(id));
    }
    @Transactional(rollbackFor = Exception.class)
    public void changeSale(int id,String status) {
        if(!Set.of("MO_BAN","DONG_BAN").contains(status)) throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        Object expectedVehicle=trip(id).get("PhuongTienID");
        if("MO_BAN".equals(status)&&expectedVehicle instanceof Number v)fleet.vehicle(v.intValue(),true);
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH (UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",id);
        if("MO_BAN".equals(status)){
            if(!Objects.equals(expectedVehicle,trip(id).get("PhuongTienID")))throw new IllegalArgumentException("Phân công vừa thay đổi. Tải lại chuyến trước khi mở bán.");
            var issues=saleIssues(id);
            if(!issues.isEmpty())throw new IllegalArgumentException("Chưa thể mở bán: "+String.join(" ",issues));
        }
        if("DONG_BAN".equals(status) && db.queryForObject("SELECT COUNT(*) FROM dbo.DangKyVe WHERE ChuyenDiID=? AND TrangThai='GIU_CHO' AND HanThanhToan>SYSUTCDATETIME()",Integer.class,id)>0)
            throw new IllegalArgumentException("Chuyến đang có vé giữ chỗ. Hãy chờ khách thanh toán hoặc đơn hết hạn trước khi đóng bán.");
        if(db.update("UPDATE dbo.ChuyenDi SET TrangThai=? WHERE ChuyenDiID=? AND TrangThai IN ('MO_BAN','DONG_BAN')",status,id)!=1)
            throw new IllegalArgumentException("Chuyến không thể thay đổi trạng thái mở bán.");
    }
    public LocalDateTime databaseNow() { return db.queryForObject("SELECT SYSUTCDATETIME()",Timestamp.class).toLocalDateTime(); }
    public boolean configured(int id) {
        return db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi c WHERE c.ChuyenDiID=? AND "+TripReadiness.configured("c"),Integer.class,id)==1;
    }
    public void requireConfigured(int id) {
        if(!configured(id))throw new IllegalArgumentException("Chuyến chưa có phương tiện, sức chứa và sơ đồ chỗ hợp lệ. Vui lòng chọn chuyến khác.");
    }
    public List<String> saleIssues(int id) {
        var t=trip(id);var issues=new ArrayList<String>();var seats=seats(id);
        if(t.get("PhuongTienID")==null)issues.add("Chưa phân công phương tiện.");
        else {
            int vehicle=((Number)t.get("PhuongTienID")).intValue();
            if(!Boolean.TRUE.equals(fleet.vehicle(vehicle,false).get("HoatDong")))issues.add("Phương tiện không hoạt động.");
            try{fleet.requireAvailable(vehicle,id,(String)t.get("NoiDi"),(String)t.get("NoiDen"),((Timestamp)t.get("KhoiHanh")).toLocalDateTime(),((Timestamp)t.get("DuKienDen")).toLocalDateTime());
                fleet.requireConnection(vehicle,id,(String)t.get("NoiDi"),(String)t.get("NoiDen"),((Timestamp)t.get("KhoiHanh")).toLocalDateTime(),((Timestamp)t.get("DuKienDen")).toLocalDateTime());
            }catch(IllegalArgumentException e){issues.add(e.getMessage());}
        }
        if(!Set.of("MO_BAN","DONG_BAN").contains(t.get("TrangThai")))issues.add("Chuyến không còn trong giai đoạn mở bán.");
        if(!((Timestamp)t.get("KhoiHanh")).toLocalDateTime().isAfter(databaseNow().plusMinutes(30)))issues.add("Phải còn hơn 30 phút trước giờ khởi hành.");
        if(seats.size()!=((Number)t.get("TongSoCho")).intValue())issues.add("Sức chứa khai báo không khớp số chỗ trong dữ liệu.");
        if(seats.isEmpty()||seats.stream().anyMatch(s->s.get("Tang")==null))issues.add("Chưa có vị trí sơ đồ cho tất cả chỗ.");
        if(!configured(id)&&issues.isEmpty())issues.add("Kiểm tra hành trình, giá vé và loại chỗ trong sơ đồ.");
        return List.copyOf(issues);
    }
    /** Stop further sales, not existing holds/payments. No ticket, fare or seat is rewritten. */
    @Transactional(rollbackFor = Exception.class) public int closeInvalidTrips() {
        var invalid=db.queryForList("SELECT c.ChuyenDiID FROM dbo.ChuyenDi c WITH(UPDLOCK,HOLDLOCK) WHERE c.TrangThai='MO_BAN' AND NOT "+TripReadiness.configured("c"));
        for(var row:invalid){
            int id=((Number)row.get("ChuyenDiID")).intValue();
            db.update("UPDATE dbo.ChuyenDi SET TrangThai='DONG_BAN' WHERE ChuyenDiID=?",id);
            db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES('SYSTEM','DONG_BAN',?,?)",String.valueOf(id),"Tạm đóng bán do chưa phân công phương tiện hoặc sức chứa / sơ đồ chưa hợp lệ. Giữ nguyên toàn bộ vé, chỗ, giá và giao dịch; chỉ mở lại sau khi hoàn tất điều kiện khai thác.");
        }
        return invalid.size();
    }
    /** Retire only the older, system-seeded sea schedule when the newer catalogue has its exact sailing. */
    @Transactional(rollbackFor = Exception.class) public int closeSupersededReferenceTrips() {
        var rows=db.queryForList("SELECT c.ChuyenDiID FROM dbo.ChuyenDi c WITH(UPDLOCK,HOLDLOCK) WHERE c.LoaiVe='TAU_THUYEN' AND c.MaChuyen LIKE 'RVT-DEMO-%-8' AND c.TrangThai='MO_BAN' AND c.KhoiHanh>SYSUTCDATETIME()"
            +" AND EXISTS(SELECT 1 FROM dbo.ChuyenDi n WHERE n.MaChuyen LIKE 'RVT-SEA-%' AND n.LoaiVe=c.LoaiVe AND n.NoiDi=c.NoiDi AND n.NoiDen=c.NoiDen AND n.KhoiHanh=c.KhoiHanh AND n.DuKienDen=c.DuKienDen AND n.TrangThai='MO_BAN' AND "+TripReadiness.configured("n")+")");
        for(var row:rows){
            int id=((Number)row.get("ChuyenDiID")).intValue();db.update("UPDATE dbo.ChuyenDi SET TrangThai='DONG_BAN' WHERE ChuyenDiID=?",id);
            db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES('SYSTEM','DONG_BAN',?,?)",String.valueOf(id),"Tạm đóng lịch tham khảo khởi tạo cũ: danh mục tuyến bổ sung đã có chuyến cùng hành trình, giờ đi và giờ đến đang mở bán. Giữ nguyên mọi vé, giá và giao dịch đã phát sinh.");
        }
        return rows.size();
    }
    @Transactional(rollbackFor = Exception.class) public void assignVehicle(int id,int vehicleId,String actor) {
        // Same lock order as trip creation: vehicle before trip, preventing concurrent double allocation.
        var vehicle=fleet.vehicle(vehicleId,true);
        var locked=db.queryForList("SELECT * FROM dbo.ChuyenDi WITH(UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",id);
        if(locked.isEmpty())throw new IllegalArgumentException("Không tìm thấy chuyến đi.");
        var t=locked.getFirst();
        if(!"DONG_BAN".equals(t.get("TrangThai")))throw new IllegalArgumentException("Đóng bán chuyến trước khi phân công phương tiện.");
        if(!((Timestamp)t.get("KhoiHanh")).toLocalDateTime().isAfter(databaseNow().plusMinutes(30)))throw new IllegalArgumentException("Chỉ phân công khi còn hơn 30 phút trước khởi hành.");
        if(!Boolean.TRUE.equals(vehicle.get("HoatDong"))||!Objects.equals(t.get("LoaiVe"),vehicle.get("LoaiVe")))throw new IllegalArgumentException("Phương tiện phải đang hoạt động và đúng loại vé của chuyến.");
        var layout=fleet.seats(vehicleId);
        fleet.parse(fleet.form(vehicleId).layout(),(String)vehicle.get("LoaiVe"));
        fleet.requireAvailable(vehicleId,id,(String)t.get("NoiDi"),(String)t.get("NoiDen"),((Timestamp)t.get("KhoiHanh")).toLocalDateTime(),((Timestamp)t.get("DuKienDen")).toLocalDateTime());
        var old=seats(id);
        boolean hasTickets=db.queryForObject("SELECT COUNT(*) FROM dbo.DangKyVe WHERE ChuyenDiID=?",Integer.class,id)>0;
        if(hasTickets){
            boolean same=old.size()==layout.size()&&old.stream().allMatch(s->layout.stream().anyMatch(v->
                Objects.equals(s.get("MaCho"),v.get("MaCho"))&&Objects.equals(s.get("LoaiCho"),v.get("LoaiCho"))
                &&Objects.equals(s.get("Tang"),v.get("Tang"))&&Objects.equals(s.get("Hang"),v.get("Hang"))&&Objects.equals(s.get("Cot"),v.get("Cot"))));
            if(!same||old.size()!=((Number)t.get("TongSoCho")).intValue())throw new IllegalArgumentException("Chuyến đã phát sinh vé: chỉ chọn phương tiện có cùng mã chỗ, loại chỗ và vị trí. Không tự bổ sung hoặc chuyển chỗ; hãy tạo chuyến mới nếu cấu hình khác.");
        }else{
            // There is no ticket history referencing these seats; rebuilding this exact trip is safe.
            db.update("DELETE FROM dbo.SoDoCho WHERE ChuyenDiID=?",id);
            db.update("DELETE FROM dbo.ChoNgoi WHERE ChuyenDiID=?",id);
            // The capacity trigger must see the new limit before adding the replacement seats.
            db.update("UPDATE dbo.ChuyenDi SET TongSoCho=?,DonViVanChuyen=? WHERE ChuyenDiID=?",layout.size(),vehicle.get("DonViVanChuyen"),id);
            for(var seat:layout){
                var previous=old.stream().filter(s->Objects.equals(s.get("MaCho"),seat.get("MaCho"))&&Objects.equals(s.get("LoaiCho"),seat.get("LoaiCho"))).findFirst();
                Object extra=previous.map(s->s.get("PhuThu")).orElse(seat.get("PhuThu"));
                Object state=previous.map(s->s.get("TrangThai")).orElse("SAN_SANG");
                db.update("INSERT dbo.ChoNgoi(ChuyenDiID,MaCho,LoaiVe,LoaiCho,PhuThu,TrangThai) VALUES(?,?,?,?,?,?)",id,seat.get("MaCho"),t.get("LoaiVe"),seat.get("LoaiCho"),extra,state);
                db.update("INSERT dbo.SoDoCho(ChuyenDiID,MaCho,Tang,Hang,Cot) VALUES(?,?,?,?,?)",id,seat.get("MaCho"),seat.get("Tang"),seat.get("Hang"),seat.get("Cot"));
            }
        }
        db.update("UPDATE dbo.ChuyenDi SET PhuongTienID=?,TenPhuongTienSnapshot=?,SoDangKySnapshot=? WHERE ChuyenDiID=?",vehicleId,vehicle.get("TenPhuongTien"),vehicle.get("SoDangKy"),id);
        audit(actor,"PHAN_CONG_PHUONG_TIEN",id+" / "+vehicle.get("MaPhuongTien"));
    }
    @Transactional(rollbackFor = Exception.class)
    public void cancelTrip(int id,String reason,String actor) {
        if(reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("Nhập lý do hủy chuyến, tối đa 500 ký tự.");
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH (UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",id);
        var t=trip(id);
        if("DA_HUY".equals(t.get("TrangThai")))return;
        if(!((Timestamp)t.get("KhoiHanh")).toLocalDateTime().isAfter(databaseNow()))throw new IllegalArgumentException("Chỉ hủy chuyến chưa khởi hành.");
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.DangKyVe WHERE ChuyenDiID=? AND TrangThai='DA_SU_DUNG'",Integer.class,id)>0)
            throw new IllegalArgumentException("Chuyến đã có hành khách lên phương tiện, không thể hủy.");
        legacy.importTrip(id);
        expireTrip(id);
        for(var o:db.queryForList("SELECT * FROM dbo.DonDatVe WITH(UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=? AND TrangThai IN('GIU_CHO','DA_THANH_TOAN') ORDER BY TaiKhoanID,DonDatVeID",id)){
            wallet.refund(o);notifications.order(o,"CANCEL","Chuyến đi bị hủy",reason.strip()+". Đơn đã hủy; tiền đã thanh toán được hoàn vào số dư RVT.");
        }
        db.update("UPDATE dbo.DangKyVe SET TrangThai='DA_HUY',DangChiemCho=0 WHERE ChuyenDiID=? AND TrangThai IN('GIU_CHO','DA_THANH_TOAN')",id);
        db.update("UPDATE p SET TrangThai='DA_HOAN',HoanLuc=SYSUTCDATETIME() FROM dbo.ThanhToan p JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID WHERE v.ChuyenDiID=? AND p.TrangThai='THANH_CONG'",id);
        db.update("UPDATE dbo.DonDatVe SET TrangThai='DA_HUY',HuyLuc=SYSUTCDATETIME(),LyDoHuy=? WHERE ChuyenDiID=? AND TrangThai IN('GIU_CHO','DA_THANH_TOAN')",reason.strip(),id);
        db.update("UPDATE dbo.ChuyenDi SET TrangThai='DA_HUY',LyDoHuy=? WHERE ChuyenDiID=?",reason.strip(),id);
        audit(actor,"HUY_CHUYEN",String.valueOf(id));
    }
    @Transactional(rollbackFor = Exception.class)
    public void refreshLifecycle() {
        db.update("UPDATE c SET TrangThai='HOAN_THANH' FROM dbo.ChuyenDi c WHERE c.TrangThai IN('MO_BAN','DONG_BAN','DA_KHOI_HANH') AND c.DuKienDen<=SYSUTCDATETIME() AND "+TripReadiness.configured("c"));
        db.update("UPDATE c SET TrangThai='DA_KHOI_HANH' FROM dbo.ChuyenDi c WHERE c.TrangThai IN('MO_BAN','DONG_BAN') AND c.KhoiHanh<=SYSUTCDATETIME() AND c.DuKienDen>SYSUTCDATETIME() AND "+TripReadiness.configured("c"));
    }
    @Transactional(rollbackFor = Exception.class)
    public void expireTrip(int id) {
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH (UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",id);
        for(var o:db.queryForList("SELECT * FROM dbo.DonDatVe WHERE ChuyenDiID=? AND TrangThai='GIU_CHO' AND HanThanhToan<=SYSUTCDATETIME()",id))
            notifications.order(o,"EXPIRE","Đơn giữ chỗ đã hết hạn","Chỗ chưa thanh toán đã được giải phóng. Số dư RVT không bị trừ; bạn có thể đặt đơn mới.");
        db.update("UPDATE dbo.DangKyVe SET TrangThai='HET_HAN',DangChiemCho=0 WHERE ChuyenDiID=? AND TrangThai='GIU_CHO' AND HanThanhToan<=SYSUTCDATETIME()",id);
        db.update("UPDATE dbo.DonDatVe SET TrangThai='HET_HAN' WHERE ChuyenDiID=? AND TrangThai='GIU_CHO' AND HanThanhToan<=SYSUTCDATETIME()",id);
    }
    public int insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder();
        db.update(c -> { var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]); return s; },key);
        return Objects.requireNonNull(key.getKey()).intValue();
    }
    private static String emptyNull(String s) { return s==null || s.isBlank() ? null : s.strip(); }
}
