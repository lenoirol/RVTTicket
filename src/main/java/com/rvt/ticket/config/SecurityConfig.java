package com.rvt.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
public class SecurityConfig {
    @Bean
    BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean
    SecurityFilterChain security(HttpSecurity http,JdbcTemplate db) throws Exception {
        var csrfHandler = new XorCsrfTokenRequestAttributeHandler();
        // Resolve the token/session before Thymeleaf flushes a large page response.
        csrfHandler.setCsrfRequestAttributeName(null);
        return http.csrf(c -> c.csrfTokenRequestHandler(csrfHandler)).authorizeHttpRequests(a -> a
                // RBAC: ROLE_ADMIN toàn quyền; ROLE_OPERATOR chỉ các khu vực vận hành; ROLE_TRAVELER / ROLE_AGENT là khách đặt vé.
                .requestMatchers("/admin/lich-hen", "/admin/lich-hen/**", "/admin/don-ve", "/admin/don-ve/**", "/admin/kiem-ve", "/admin/kiem-ve/**",
                        "/admin/ho-tro", "/admin/ho-tro/**", "/admin/tai-khoan", "/admin/tai-khoan/**", "/staff", "/staff/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/tai-khoan", "/tai-khoan/**", "/lich-hen", "/lich-hen/**", "/api/don-ve/**").hasAnyRole("CUSTOMER", "TRAVELER", "AGENT")
                .requestMatchers("/dat-ve/**", "/don-ve/*/thanh-toan", "/don-ve/*/huy").hasAnyRole("CUSTOMER", "TRAVELER", "AGENT")
                .anyRequest().permitAll())
            .formLogin(f -> f.loginPage("/login").successHandler(new BookingLoginSuccessHandler()).permitAll())
            .logout(l -> l.logoutSuccessUrl("/"))
            .headers(h->h.referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .addFilterBefore(new RequestThrottle(),org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new LockedCustomerFilter(db),org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .addFilterBefore(new AdminSessionFilter(db),org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .build();
    }

    @Bean
    UserDetailsService users(JdbcTemplate db) {
        return username -> {
            var admins=db.queryForList("SELECT TenDangNhap,MatKhau,PhienBanDangNhap,VaiTro FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap=?",username);
            if(!admins.isEmpty()) {
                var a=admins.getFirst();return new AdminIdentity((String)a.get("TenDangNhap"),(String)a.get("MatKhau"),((Number)a.get("PhienBanDangNhap")).intValue(),(String)a.get("VaiTro"));
            }
            var rows=db.queryForList("SELECT TenDangNhap,MatKhau,BiKhoa,PhienBanDangNhap,VaiTro FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",username);
            if(rows.isEmpty()) throw new UsernameNotFoundException("Không tìm thấy tài khoản.");
            var row=rows.getFirst();
            return new CustomerIdentity((String)row.get("TenDangNhap"),(String)row.get("MatKhau"),!Boolean.TRUE.equals(row.get("BiKhoa")),((Number)row.get("PhienBanDangNhap")).intValue(),(String)row.get("VaiTro"));
        };
    }
}
