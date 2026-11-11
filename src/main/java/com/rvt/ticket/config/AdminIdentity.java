package com.rvt.ticket.config;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
public final class AdminIdentity extends User {
    private static final long serialVersionUID=1L;
    private final int version;
    public AdminIdentity(String username,String password,int version){this(username,password,version,"ADMIN");}
    /** ADMIN toàn quyền; OPERATOR chỉ xử lý đơn vé, quét QR, lịch hẹn và hỗ trợ. */
    public AdminIdentity(String username,String password,int version,String role){super(username,password,List.of(new SimpleGrantedAuthority("OPERATOR".equals(role)?"ROLE_OPERATOR":"ROLE_ADMIN")));this.version=version;}
    public int version(){return version;}
}
