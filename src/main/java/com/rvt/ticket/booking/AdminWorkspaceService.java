package com.rvt.ticket.booking;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminWorkspaceService {
    private final JdbcTemplate db;
    private final TicketService tickets;
    private final ReportService reports;
    public AdminWorkspaceService(JdbcTemplate db,TicketService tickets,ReportService reports){this.db=db;this.tickets=tickets;this.reports=reports;}
    public record Page(List<Map<String,Object>> rows,int total,int page,int pages) {}
    public static final Map<String,String> ACTIONS=Map.ofEntries(
        Map.entry("PAY","Ghi nhận thanh toán"),Map.entry("CANCEL","Hủy đơn"),Map.entry("CHECKIN","Xác nhận lên xe / tàu"),
        Map.entry("CAP_NHAT_CHINH_SACH","Cập nhật chính sách"),Map.entry("CAP_NHAT_CHUYEN","Cập nhật thông tin chuyến"),
        Map.entry("TAO_CHUYEN","Tạo chuyến"),Map.entry("HUY_CHUYEN","Hủy chuyến & hoàn"),Map.entry("LUU_PHUONG_TIEN","Lưu phương tiện"),
        Map.entry("THEM_DIA_DIEM","Thêm địa điểm"),Map.entry("MO_BAN","Mở bán"),Map.entry("DONG_BAN","Đóng bán"),
        Map.entry("PHAN_CONG_PHUONG_TIEN","Phân công phương tiện"),
        Map.entry("VNPAY_REVIEW","VNPay cần đối chiếu"),Map.entry("VNPAY_PAY","VNPay xác nhận thanh toán"),
        Map.entry("VNPAY_HOAN_RVT","Hoàn ngoại lệ VNPay vào RVT"),Map.entry("NHAP_VE_GOC","Gắn vé gốc vào đơn"),
        Map.entry("KHOA_KHACH","Khóa khách hàng"),Map.entry("MO_KHOA_KHACH","Mở khóa khách hàng"),Map.entry("DOI_MAT_KHAU","Đổi mật khẩu"),Map.entry("TRA_LOI_HO_TRO","Trả lời hỗ trợ"));
    private static final String TRIP_COLUMNS="""
        c.*,COALESCE(c.TenPhuongTienSnapshot,N'Chưa phân công phương tiện') AS TenPhuongTien,
        (SELECT COUNT(*) FROM dbo.ChoNgoi s WHERE s.ChuyenDiID=c.ChuyenDiID) AS SoChoKhaiBao,
        (SELECT COUNT(*) FROM dbo.SoDoCho s WHERE s.ChuyenDiID=c.ChuyenDiID) AS SoChoCoViTri,
        (SELECT COUNT(DISTINCT v.MaCho) FROM dbo.DangKyVe v WHERE v.ChuyenDiID=c.ChuyenDiID AND v.TrangThai IN('DA_THANH_TOAN','DA_SU_DUNG')) AS DaBan,
        (SELECT COUNT(*) FROM dbo.DangKyVe v WHERE v.ChuyenDiID=c.ChuyenDiID AND v.TrangThai='DA_SU_DUNG') AS DaLen,
        (SELECT COUNT(*) FROM dbo.DangKyVe v WHERE v.ChuyenDiID=c.ChuyenDiID AND v.TrangThai='GIU_CHO' AND v.HanThanhToan>SYSUTCDATETIME()) AS DangGiu,
        (SELECT COUNT(*) FROM dbo.DangKyVe v WHERE v.ChuyenDiID=c.ChuyenDiID AND v.TrangThai='DA_THANH_TOAN') AS ChuaLen
        """+",CASE WHEN "+TripReadiness.configured("c")+" THEN 1 ELSE 0 END AS CauHinhHopLe";
    public Map<String,Object> dashboard(LocalDate date) {
        if(date==null)throw new IllegalArgumentException("Chọn ngày điều hành.");
        var from=utc(date);var to=utc(date.plusDays(1));
        var rows=db.queryForList("SELECT "+TRIP_COLUMNS+" FROM dbo.ChuyenDi c WHERE c.KhoiHanh>=? AND c.KhoiHanh<? ORDER BY c.KhoiHanh,c.ChuyenDiID",from,to);
        var summary=new LinkedHashMap<String,Object>();
        summary.put("trips",rows.stream().filter(t->!"DA_HUY".equals(t.get("TrangThai"))).count());
        summary.put("waiting",rows.stream().filter(t->!Set.of("DA_HUY","HOAN_THANH").contains(t.get("TrangThai"))).mapToInt(t->((Number)t.get("ChuaLen")).intValue()).sum());
        summary.put("orders",db.queryForObject("SELECT COUNT(*) FROM dbo.DonDatVe WHERE NgayTao>=? AND NgayTao<?",Integer.class,from,to));
        summary.put("held",db.queryForObject("SELECT COUNT(*) FROM dbo.DonDatVe WHERE TrangThai='GIU_CHO' AND HanThanhToan>SYSUTCDATETIME()",Integer.class));
        summary.put("supportWaiting",db.queryForObject("SELECT COUNT(*) FROM dbo.YeuCauHoTro WHERE TrangThai='MOI'",Integer.class));
        var now=tickets.databaseNow();
        summary.put("unassigned",rows.stream().filter(t->t.get("PhuongTienID")==null&&!"DA_HUY".equals(t.get("TrangThai"))&&((Timestamp)t.get("KhoiHanh")).toLocalDateTime().isAfter(now)).count());
        return Map.of("summary",summary,"trips",rows,"cash",reports.report(date,date,"").get("summary"));
    }
    public Page trips(String q,String type,String status,LocalDate start,LocalDate end,int page) {
        type(type);choice(status,Set.of("","MO_BAN","DONG_BAN","DA_KHOI_HANH","HOAN_THANH","DA_HUY"));
        var args=new ArrayList<Object>();String where=" WHERE 1=1";
        if(!q.isBlank()){where+=" AND (c.MaChuyen LIKE ? ESCAPE '\\' OR "+TripIdentity.sql("c")+" LIKE ? ESCAPE '\\' OR c.NoiDi LIKE ? ESCAPE '\\' OR c.NoiDen LIKE ? ESCAPE '\\' OR c.DonViVanChuyen LIKE ? ESCAPE '\\' OR c.TenPhuongTienSnapshot LIKE ? ESCAPE '\\')";for(int i=0;i<6;i++)args.add(like(q));}
        if(!type.isBlank()){where+=" AND c.LoaiVe=?";args.add(type);}
        if(!status.isBlank()){where+=" AND c.TrangThai=?";args.add(status);}
        where+=dates("c.KhoiHanh",start,end,args);
        return page(TRIP_COLUMNS,"dbo.ChuyenDi c",where,"c.KhoiHanh,c.ChuyenDiID",args,page);
    }
    public Page orders(String q,String type,String status,LocalDate start,LocalDate end,Integer tripId,int page) {
        type(type);choice(status,Set.of("","GIU_CHO","DA_THANH_TOAN","DA_SU_DUNG","HET_HAN","DA_HUY"));
        var args=new ArrayList<Object>();String where=" WHERE 1=1";
        if(!q.isBlank()){where+=" AND (o.MaDon LIKE ? ESCAPE '\\' OR k.HoTen LIKE ? ESCAPE '\\' OR k.SoDienThoai LIKE ? ESCAPE '\\')";for(int i=0;i<3;i++)args.add(like(q));}
        if(!type.isBlank()){where+=" AND c.LoaiVe=?";args.add(type);}
        if(!status.isBlank()){where+=" AND o.TrangThai=?";args.add(status);}
        if(tripId!=null){where+=" AND o.ChuyenDiID=?";args.add(tripId);}
        where+=dates("o.NgayTao",start,end,args);
        return page("o.*,k.HoTen,k.SoDienThoai,c.MaChuyen,c.NoiDi,c.NoiDen,c.KhoiHanh,c.LoaiVe,(SELECT COUNT(*) FROM dbo.DonDatVeChiTiet d WHERE d.DonDatVeID=o.DonDatVeID) AS SoVe",
            "dbo.DonDatVe o JOIN dbo.KhachHang k ON k.KhachHangID=o.KhachHangID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID",where,"o.NgayTao DESC,o.DonDatVeID DESC",args,page);
    }
    public Map<String,Object> tripDetail(int id) {
        var result=new LinkedHashMap<>(tickets.trip(id));
        result.put("saleIssues",tickets.saleIssues(id));
        var seats=tickets.seats(id);result.put("seats",seats);
        result.put("SoChoKhaiBao",seats.size());
        result.put("SoChoCoViTri",seats.stream().filter(s->s.get("Tang")!=null).count());
        result.put("DonBiAnhHuong",db.queryForObject("SELECT COUNT(*) FROM dbo.DonDatVe WHERE ChuyenDiID=? AND TrangThai IN('GIU_CHO','DA_THANH_TOAN') AND (TrangThai<>'GIU_CHO' OR HanThanhToan>SYSUTCDATETIME())",Integer.class,id));
        result.put("TienSeHoan",db.queryForObject("SELECT COALESCE(SUM(p.SoTien),0) FROM dbo.ThanhToan p JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID WHERE v.ChuyenDiID=? AND p.TrangThai='THANH_CONG'",java.math.BigDecimal.class,id));
        result.put("passengers",db.queryForList("""
            SELECT v.DangKyCode,v.TenHanhKhach,v.MaCho,v.TrangThai,v.DangKyID,o.MaDon,k.SoDienThoai
            FROM dbo.DangKyVe v LEFT JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID
            LEFT JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID JOIN dbo.KhachHang k ON k.KhachHangID=v.KhachHangID
            WHERE v.ChuyenDiID=? AND v.TrangThai IN('GIU_CHO','DA_THANH_TOAN','DA_SU_DUNG')
             AND (v.TrangThai<>'GIU_CHO' OR v.HanThanhToan>SYSUTCDATETIME()) ORDER BY v.MaCho,v.DangKyID
            """,id));return result;
    }
    public Page customers(String q,String state,int page) {
        choice(state,Set.of("","active","locked"));var args=new ArrayList<Object>();String where=" WHERE 1=1";
        if(!q.isBlank()){where+=" AND (a.TenDangNhap LIKE ? ESCAPE '\\' OR a.HoTen LIKE ? ESCAPE '\\' OR a.SoDienThoai LIKE ? ESCAPE '\\')";for(int i=0;i<3;i++)args.add(like(q));}
        if(!state.isBlank()){where+=" AND a.BiKhoa=?";args.add(state.equals("locked"));}
        return page("a.TaiKhoanID,a.TenDangNhap,a.HoTen,a.SoDienThoai,a.NgayTao,a.BiKhoa,a.LyDoKhoa,(SELECT COUNT(*) FROM dbo.DonDatVe o WHERE o.TaiKhoanID=a.TaiKhoanID) AS SoDon",
            "dbo.TaiKhoanKhach a",where,"a.NgayTao DESC,a.TaiKhoanID DESC",args,page);
    }
    public Map<String,Object> customer(int id) {
        var rows=db.queryForList("SELECT TaiKhoanID,TenDangNhap,HoTen,SoDienThoai,NgayTao,BiKhoa,LyDoKhoa,KhoaLuc,KhoaBoi,SoDuRVT FROM dbo.TaiKhoanKhach WHERE TaiKhoanID=?",id);
        if(rows.isEmpty())throw new IllegalArgumentException("Không tìm thấy tài khoản khách hàng.");return rows.getFirst();
    }
    public Page customerOrders(int id,int page) {
        customer(id);
        return page("o.*,c.NoiDi,c.NoiDen,c.KhoiHanh,(SELECT COUNT(*) FROM dbo.DonDatVeChiTiet d WHERE d.DonDatVeID=o.DonDatVeID) AS SoVe",
            "dbo.DonDatVe o JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID"," WHERE o.TaiKhoanID=?","o.NgayTao DESC,o.DonDatVeID DESC",new ArrayList<>(List.of(id)),page);
    }
    @Transactional(rollbackFor = Exception.class) public void lockCustomer(int id,boolean locked,String reason,String actor) {
        if(locked&&(reason==null||reason.isBlank()||reason.length()>500))throw new IllegalArgumentException("Nhập lý do khóa từ 1 đến 500 ký tự.");
        var rows=db.queryForList("SELECT BiKhoa,TenDangNhap FROM dbo.TaiKhoanKhach WITH (UPDLOCK,HOLDLOCK) WHERE TaiKhoanID=?",id);
        if(rows.isEmpty())throw new IllegalArgumentException("Không tìm thấy tài khoản khách hàng.");
        if(Boolean.TRUE.equals(rows.getFirst().get("BiKhoa"))==locked)return;
        db.update("UPDATE dbo.TaiKhoanKhach SET BiKhoa=?,LyDoKhoa=?,KhoaLuc=CASE WHEN ?=1 THEN SYSUTCDATETIME() ELSE NULL END,KhoaBoi=? WHERE TaiKhoanID=?",locked,locked?reason.strip():null,locked,locked?actor:null,id);
        db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES(?,?,?,?)",actor,locked?"KHOA_KHACH":"MO_KHOA_KHACH",String.valueOf(id),locked?reason.strip():"Mở khóa tài khoản");
    }
    public Page logs(String q,String action,LocalDate start,LocalDate end,int page) {
        if(!action.isBlank()&&!ACTIONS.containsKey(action))throw new IllegalArgumentException("Loại thao tác không hợp lệ.");
        var args=new ArrayList<Object>();String where=" WHERE 1=1";
        if(!q.isBlank()){where+=" AND (n.NguoiThucHien LIKE ? ESCAPE '\\' OR n.DoiTuong LIKE ? ESCAPE '\\')";args.add(like(q));args.add(like(q));}
        if(!action.isBlank()){where+=" AND n.HanhDong=?";args.add(action);}
        where+=dates("n.NgayTao",start,end,args);
        return page("n.*","dbo.NhatKyThaoTac n",where,"n.NhatKyID DESC",args,page);
    }
    private Page page(String columns,String tables,String where,String order,List<Object> args,int requested) {
        if(requested<1||requested>1000000)throw new IllegalArgumentException("Trang không hợp lệ.");
        int total=db.queryForObject("SELECT COUNT(*) FROM "+tables+where,Integer.class,args.toArray());int pages=Math.max(1,(total+19)/20),current=Math.min(requested,pages);
        args.add((current-1)*20);args.add(20);
        var rows=db.queryForList("SELECT "+columns+" FROM "+tables+where+" ORDER BY "+order+" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray());
        return new Page(rows,total,current,pages);
    }
    private String dates(String column,LocalDate start,LocalDate end,List<Object> args) {
        if(start!=null&&end!=null&&end.isBefore(start))throw new IllegalArgumentException("Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        String result="";if(start!=null){result+=" AND "+column+">=?";args.add(utc(start));}if(end!=null){result+=" AND "+column+"<?";args.add(utc(end.plusDays(1)));}return result;
    }
    private Timestamp utc(LocalDate date){return Timestamp.valueOf(date.atStartOfDay().minusHours(7));}
    private String like(String q){if(q.length()>100)throw new IllegalArgumentException("Từ khóa tối đa 100 ký tự.");return "%"+q.strip().replace("\\","\\\\").replace("%","\\%").replace("_","\\_").replace("[","\\[")+"%";}
    private void type(String type){choice(type,Set.of("","XE_KHACH","TAU_THUYEN"));}
    private void choice(String value,Set<String> values){if(!values.contains(value))throw new IllegalArgumentException("Bộ lọc không hợp lệ.");}
}
