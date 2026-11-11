package com.rvt.ticket.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
public final class AdminSessionFilter extends OncePerRequestFilter {
    private final JdbcTemplate db;
    public AdminSessionFilter(JdbcTemplate db){this.db=db;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null&&auth.getPrincipal() instanceof AdminIdentity identity){
            var versions=db.queryForList("SELECT PhienBanDangNhap FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap=?",Integer.class,identity.getUsername());
            if(versions.isEmpty()||identity.version()!=versions.getFirst()){
                SecurityContextHolder.clearContext();var session=request.getSession(false);if(session!=null)session.invalidate();
                response.sendRedirect(request.getContextPath()+"/login?changed");return;
            }
        }
        chain.doFilter(request,response);
    }
}
