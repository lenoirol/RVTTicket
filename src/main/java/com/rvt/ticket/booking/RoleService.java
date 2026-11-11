package com.rvt.ticket.booking;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý vai trò (RBAC). Nhân sự nội bộ: ADMIN hoặc OPERATOR. Khách hàng: TRAVELER hoặc AGENT.
 * Mỗi lần đổi vai trò đều tăng PhienBanDangNhap để phiên đang mở phải đăng nhập lại và nhận quyền mới.
 */
@Service
public class RoleService {
    public static final Map<String,String> STAFF_ROLES=ordered("ADMIN","Quản trị viên (toàn quyền)","OPERATOR","Nhân viên vận hành");
    public static final Map<String,String> CUSTOMER_ROLES=ordered("TRAVELER","Khách đi lẻ","AGENT","Đại lý");
    private static Map<String,String> ordered(String... pairs){var map=new LinkedHashMap<String,String>();for(int i=0;i<pairs.length;i+=2)map.put(pairs[i],pairs[i+1]);return Collections.unmodifiableMap(map);}
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder encoder;
    public RoleService(JdbcTemplate db,BCryptPasswordEncoder encoder){this.db=db;this.encoder=encoder;}

    public List<Map<String,Object>> staff() {
        return db.queryForList("SELECT TaiKhoanID,TenDangNhap,HoTen,VaiTro,NgayTao FROM dbo.TaiKhoanQuanTri ORDER BY CASE VaiTro WHEN 'ADMIN' THEN 0 ELSE 1 END,TenDangNhap");
    }

    public AdminWorkspaceService.Page customers(String q,String role,int page) {
        if(!role.isBlank()&&!CUSTOMER_ROLES.containsKey(role))throw new IllegalArgumentException("Bộ lọc vai trò không hợp lệ.");
        if(q.length()>100)throw new IllegalArgumentException("Từ khóa tối đa 100 ký tự.");
        if(page<1||page>1000000)throw new IllegalArgumentException("Trang không hợp lệ.");
        var args=new ArrayList<Object>();String where=" WHERE 1=1";
        if(!q.isBlank()){
            String like="%"+q.strip().replace("\\","\\\\").replace("%","\\%").replace("_","\\_").replace("[","\\[")+"%";
            where+=" AND (TenDangNhap LIKE ? ESCAPE '\\' OR HoTen LIKE ? ESCAPE '\\' OR SoDienThoai LIKE ? ESCAPE '\\')";for(int i=0;i<3;i++)args.add(like);
        }
        if(!role.isBlank()){where+=" AND VaiTro=?";args.add(role);}
        int total=db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanKhach"+where,Integer.class,args.toArray());
        int pages=Math.max(1,(total+19)/20),current=Math.min(page,pages);
        args.add((current-1)*20);args.add(20);
        var rows=db.queryForList("SELECT TaiKhoanID,TenDangNhap,HoTen,SoDienThoai,VaiTro,BiKhoa FROM dbo.TaiKhoanKhach"+where+" ORDER BY NgayTao DESC,TaiKhoanID DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray());
        return new AdminWorkspaceService.Page(rows,total,current,pages);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStaffRole(int id,String role,String actor) {
        if(!STAFF_ROLES.containsKey(role))throw new IllegalArgumentException("Vai trò nhân sự không hợp lệ.");
        var rows=db.queryForList("SELECT TenDangNhap,VaiTro FROM dbo.TaiKhoanQuanTri WITH (UPDLOCK,HOLDLOCK) WHERE TaiKhoanID=?",id);
        if(rows.isEmpty())throw new IllegalArgumentException("Không tìm thấy tài khoản nhân sự.");
        String username=(String)rows.getFirst().get("TenDangNhap"),current=(String)rows.getFirst().get("VaiTro");
        if(current.equals(role))return;
        if(username.equalsIgnoreCase(actor))throw new IllegalArgumentException("Không thể tự đổi vai trò của tài khoản đang đăng nhập.");
        if("ADMIN".equals(current)&&db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanQuanTri WITH (UPDLOCK,HOLDLOCK) WHERE VaiTro='ADMIN'",Integer.class)<=1)
            throw new IllegalArgumentException("Hệ thống phải còn ít nhất một quản trị viên.");
        db.update("UPDATE dbo.TaiKhoanQuanTri SET VaiTro=?,PhienBanDangNhap=PhienBanDangNhap+1 WHERE TaiKhoanID=?",role,id);
        log(actor,"DOI_VAI_TRO_NHAN_SU",username,current+" → "+role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createStaff(String username,String name,String role,String password,String confirmation,String actor) {
        username=username==null?"":username.strip().toLowerCase(Locale.ROOT);name=name==null?"":name.strip();
        if(!username.matches("[a-z0-9_]{4,40}"))throw new IllegalArgumentException("Tên đăng nhập có 4–40 ký tự thường, số hoặc dấu gạch dưới.");
        if(name.isEmpty()||name.length()>100)throw new IllegalArgumentException("Nhập họ tên từ 1 đến 100 ký tự.");
        if(!STAFF_ROLES.containsKey(role))throw new IllegalArgumentException("Vai trò nhân sự không hợp lệ.");
        if(password==null||password.length()<10||password.length()>64||password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Mật khẩu nhân sự phải có 10–64 ký tự và tối đa 72 byte.");
        if(!password.equals(confirmation))throw new IllegalArgumentException("Mật khẩu xác nhận chưa khớp.");
        // Tên đăng nhập dùng chung một không gian với tài khoản khách, vì đăng nhập tìm bảng nhân sự trước.
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanQuanTri WITH (UPDLOCK,HOLDLOCK) WHERE TenDangNhap=?",Integer.class,username)>0
            ||db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanKhach WITH (UPDLOCK,HOLDLOCK) WHERE TenDangNhap=?",Integer.class,username)>0)
            throw new IllegalArgumentException("Tên đăng nhập đã được sử dụng.");
        db.update("INSERT dbo.TaiKhoanQuanTri(TenDangNhap,MatKhau,HoTen,VaiTro) VALUES(?,?,?,?)",username,encoder.encode(password),name,role);
        log(actor,"TAO_NHAN_SU",username,role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeCustomerRole(int id,String role,String actor) {
        if(!CUSTOMER_ROLES.containsKey(role))throw new IllegalArgumentException("Vai trò khách hàng không hợp lệ.");
        var rows=db.queryForList("SELECT TenDangNhap,VaiTro FROM dbo.TaiKhoanKhach WITH (UPDLOCK,HOLDLOCK) WHERE TaiKhoanID=?",id);
        if(rows.isEmpty())throw new IllegalArgumentException("Không tìm thấy tài khoản khách hàng.");
        String current=(String)rows.getFirst().get("VaiTro");
        if(current.equals(role))return;
        db.update("UPDATE dbo.TaiKhoanKhach SET VaiTro=?,PhienBanDangNhap=PhienBanDangNhap+1 WHERE TaiKhoanID=?",role,id);
        log(actor,"DOI_VAI_TRO_KHACH",(String)rows.getFirst().get("TenDangNhap"),current+" → "+role);
    }

    private void log(String actor,String action,String target,String detail) {
        db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES(?,?,?,?)",actor,action,target,detail);
    }
}
