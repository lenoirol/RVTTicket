package com.rvt.ticket.booking;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminIdentityService {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder encoder;
    private final String initialPassword;
    private final String operatorPassword;
    public AdminIdentityService(JdbcTemplate db,BCryptPasswordEncoder encoder,@Value("${ADMIN_PASSWORD:123456}") String initialPassword,@Value("${OPERATOR_PASSWORD:}") String operatorPassword) {
        this.db=db;this.encoder=encoder;this.initialPassword=initialPassword;this.operatorPassword=operatorPassword;
    }
    @Transactional(rollbackFor = Exception.class)
    public void seed() {
        // Initial password only: a restart must never reset an existing password.
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanQuanTri WITH (UPDLOCK,HOLDLOCK) WHERE TenDangNhap='admin'",Integer.class)==0) {
            validatePassword(initialPassword);
            db.update("INSERT dbo.TaiKhoanQuanTri(TenDangNhap,MatKhau,HoTen) VALUES('admin',?,N'Quản trị RVT Ticket')",encoder.encode(initialPassword));
        }
        // Tài khoản ROLE_OPERATOR chỉ được tạo khi người vận hành chủ động đặt OPERATOR_PASSWORD; không có mật khẩu mặc định.
        if(operatorPassword!=null&&!operatorPassword.isBlank()
            &&db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanQuanTri WITH (UPDLOCK,HOLDLOCK) WHERE TenDangNhap='operator'",Integer.class)==0) {
            validatePassword(operatorPassword);
            db.update("INSERT dbo.TaiKhoanQuanTri(TenDangNhap,MatKhau,HoTen,VaiTro) VALUES('operator',?,N'Nhân viên điều hành RVT','OPERATOR')",encoder.encode(operatorPassword));
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String username,String oldPassword,String password,String confirmation) {
        validatePassword(password);
        if(!password.equals(confirmation))throw new IllegalArgumentException("Mật khẩu xác nhận chưa khớp.");
        var rows=db.queryForList("SELECT MatKhau FROM dbo.TaiKhoanQuanTri WITH (UPDLOCK,HOLDLOCK) WHERE TenDangNhap=?",username);
        if(rows.isEmpty()||!encoder.matches(oldPassword,(String)rows.getFirst().get("MatKhau")))throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        db.update("UPDATE dbo.TaiKhoanQuanTri SET MatKhau=?,PhienBanDangNhap=PhienBanDangNhap+1,DoiMatKhauLuc=SYSUTCDATETIME() WHERE TenDangNhap=?",encoder.encode(password),username);
        db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong) VALUES(?,'DOI_MAT_KHAU',?)",username,username);
    }
    private void validatePassword(String password) {
        if(password==null||password.length()<6||password.length()>64||password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Mật khẩu phải có 6–64 ký tự và tối đa 72 byte.");
    }
}
