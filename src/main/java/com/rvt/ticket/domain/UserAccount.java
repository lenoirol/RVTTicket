package com.rvt.ticket.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** Users: tài khoản khách hàng/đại lý (bảng dbo.TaiKhoanKhach), chỉ đọc qua JPA. */
@Entity
@Immutable
@Table(name = "TaiKhoanKhach")
public class UserAccount {
    @Id @Column(name = "TaiKhoanID") private Integer id;
    @Column(name = "TenDangNhap") private String username;
    @Column(name = "BiKhoa") private Boolean locked;
    @Column(name = "VaiTro") private String role;
    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public Boolean getLocked() { return locked; }
    public String getRole() { return role; }
}
