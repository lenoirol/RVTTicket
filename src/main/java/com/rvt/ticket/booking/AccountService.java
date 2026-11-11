package com.rvt.ticket.booking;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final JdbcTemplate db;
    public AccountService(JdbcTemplate db) {this.db=db;}
    @Transactional(rollbackFor = Exception.class)
    public void register(RegistrationForm f) {
        if(f.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Mật khẩu quá dài.");
        if(Set.of("admin","staff").contains(f.username().toLowerCase(Locale.ROOT)) || db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",Integer.class,f.username())>0
                ||db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap=?",Integer.class,f.username())>0)
            throw new IllegalArgumentException("Tên đăng nhập này đã được sử dụng.");
        db.update("INSERT dbo.TaiKhoanKhach(TenDangNhap,MatKhau,HoTen,SoDienThoai) VALUES(?,?,?,?)",f.username(),new BCryptPasswordEncoder().encode(f.password()),f.name().strip(),f.phone());
    }
    public List<Map<String,Object>> history(String username) {
        return db.queryForList("SELECT o.*,c.NoiDi,c.NoiDen,c.KhoiHanh,c.LoaiVe FROM dbo.DonDatVe o JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=o.TaiKhoanID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE a.TenDangNhap=? ORDER BY o.NgayTao DESC",username);
    }
    public Map<String,Object> profile(String username) {
        return db.queryForMap("SELECT TaiKhoanID,TenDangNhap,HoTen,SoDienThoai,Email,NgaySinh,LoaiGiayTo,SoGiayTo,QuocTich FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",username);
    }
    public Map<String,String> passengerDefaults(String username){
        var p=profile(username);var defaults=new LinkedHashMap<String,String>();
        defaults.put("name",Objects.toString(p.get("HoTen"),""));defaults.put("birthDate",Objects.toString(p.get("NgaySinh"),""));
        defaults.put("documentType",Objects.toString(p.get("LoaiGiayTo"),"CCCD"));defaults.put("document",Objects.toString(p.get("SoGiayTo"),""));
        defaults.put("nationality",Objects.toString(p.get("QuocTich"),"Việt Nam"));return defaults;
    }
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(String username,String name,String phone,String email){
        if(name==null||name.strip().length()<2||name.strip().length()>100||phone==null||!phone.matches("[0-9]{10,15}"))throw new IllegalArgumentException("Họ tên cần 2–100 ký tự; số điện thoại cần 10–15 chữ số.");
        if(email==null)email="";
        email=email.strip();
        if(email.length()>254||(!email.isEmpty()&&!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw new IllegalArgumentException("Email chưa đúng định dạng.");
        db.update("UPDATE dbo.TaiKhoanKhach SET HoTen=?,SoDienThoai=?,Email=? WHERE TenDangNhap=?",name.strip(),phone,email.isEmpty()?null:email,username);
    }
    @Transactional(rollbackFor = Exception.class)
    public void updatePassengerProfile(String username,String name,String phone,String email,java.time.LocalDate birth,String documentType,String document,String nationality){
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        if(birth!=null&&(birth.isAfter(today)||java.time.Period.between(birth,today).getYears()>120))throw new IllegalArgumentException("Ngày sinh trong hồ sơ chưa hợp lệ.");
        if(!Set.of("CCCD","HO_CHIEU","KHAI_SINH").contains(documentType))throw new IllegalArgumentException("Chọn loại giấy tờ hợp lệ.");
        document=document==null?"":document.strip();nationality=nationality==null?"":nationality.strip();
        if(!document.isEmpty())FarePolicy.validateDocument("TAU_THUYEN",documentType,document);
        if(nationality.length()>50)throw new IllegalArgumentException("Quốc tịch tối đa 50 ký tự.");
        updateProfile(username,name,phone,email);
        db.update("UPDATE dbo.TaiKhoanKhach SET NgaySinh=?,LoaiGiayTo=?,SoGiayTo=?,QuocTich=? WHERE TenDangNhap=?",birth==null?null:java.sql.Date.valueOf(birth),documentType,document.isEmpty()?null:document,nationality.isEmpty()?null:nationality,username);
    }
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String username,String oldPassword,String password,String confirmation){
        var hash=db.queryForObject("SELECT MatKhau FROM dbo.TaiKhoanKhach WITH (UPDLOCK,HOLDLOCK) WHERE TenDangNhap=?",String.class,username);
        var encoder=new BCryptPasswordEncoder();
        if(oldPassword==null||!encoder.matches(oldPassword,hash))throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        if(password==null||password.length()<10||password.length()>64||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Mật khẩu mới cần 10–64 ký tự, không quá 72 byte UTF-8.");
        if(!password.equals(confirmation))throw new IllegalArgumentException("Hai lần nhập mật khẩu mới chưa khớp.");
        if(encoder.matches(password,hash))throw new IllegalArgumentException("Chọn mật khẩu mới khác mật khẩu hiện tại.");
        db.update("UPDATE dbo.TaiKhoanKhach SET MatKhau=?,PhienBanDangNhap=PhienBanDangNhap+1 WHERE TenDangNhap=?",encoder.encode(password),username);
    }
    public record HistoryPage(List<Map<String,Object>> rows,long total,int page,int pages){}
    public HistoryPage history(String username,String filter,String q,int page){
        if(!Set.of("all","upcoming","pending","past","cancelled").contains(filter)||q.length()>80||page<1||page>100000)throw new IllegalArgumentException("Bộ lọc lịch sử không hợp lệ.");
        String state="CASE WHEN o.TrangThai='GIU_CHO' AND o.HanThanhToan<=SYSUTCDATETIME() THEN 'HET_HAN' ELSE o.TrangThai END";
        String extra=switch(filter){
            case "upcoming"->" AND o.TrangThai='DA_THANH_TOAN' AND c.KhoiHanh>SYSUTCDATETIME()";
            case "pending"->" AND o.TrangThai='GIU_CHO' AND o.HanThanhToan>SYSUTCDATETIME()";
            case "past"->" AND (o.TrangThai='DA_SU_DUNG' OR (o.TrangThai='DA_THANH_TOAN' AND c.KhoiHanh<=SYSUTCDATETIME()))";
            case "cancelled"->" AND "+state+" IN('DA_HUY','HET_HAN')";
            default->"";
        };
        String joins=" FROM dbo.DonDatVe o JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=o.TaiKhoanID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE a.TenDangNhap=? AND (?='' OR CHARINDEX(?,o.MaDon)>0 OR CHARINDEX(?,c.NoiDi)>0 OR CHARINDEX(?,c.NoiDen)>0)"+extra;
        long total=db.queryForObject("SELECT COUNT_BIG(*)"+joins,Long.class,username,q.strip(),q.strip(),q.strip(),q.strip());
        int pages=Math.max(1,(int)((total+9)/10));page=Math.min(page,pages);
        var rows=db.queryForList("SELECT o.MaDon,o.TongTien,o.NgayTao,c.NoiDi,c.NoiDen,c.KhoiHanh,c.LoaiVe,"+state+" AS TrangThai,(SELECT COUNT(*) FROM dbo.DonDatVeChiTiet d WHERE d.DonDatVeID=o.DonDatVeID) AS SoKhach"+joins+" ORDER BY o.NgayTao DESC,o.DonDatVeID DESC OFFSET ? ROWS FETCH NEXT 10 ROWS ONLY",username,q.strip(),q.strip(),q.strip(),q.strip(),(page-1)*10);
        return new HistoryPage(rows,total,page,pages);
    }
}
