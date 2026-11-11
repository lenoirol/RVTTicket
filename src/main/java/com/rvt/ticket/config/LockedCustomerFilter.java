package com.rvt.ticket.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class LockedCustomerFilter extends OncePerRequestFilter {
    private final JdbcTemplate db;
    public LockedCustomerFilter(JdbcTemplate db){this.db=db;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CUSTOMER"))) {
            var rows=db.queryForList("SELECT BiKhoa,PhienBanDangNhap FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",auth.getName());
            boolean locked=rows.isEmpty()?auth.getPrincipal() instanceof CustomerIdentity:Boolean.TRUE.equals(rows.getFirst().get("BiKhoa"));
            boolean changed=!rows.isEmpty()&&auth.getPrincipal() instanceof CustomerIdentity identity&&identity.version()!=((Number)rows.getFirst().get("PhienBanDangNhap")).intValue();
            if(locked||changed){
            SecurityContextHolder.clearContext();
            var session=request.getSession(false);if(session!=null)session.invalidate();
            response.sendRedirect(request.getContextPath()+"/login?"+(locked?"locked":"changed"));return;
            }
        }
        chain.doFilter(request,response);
    }
}
