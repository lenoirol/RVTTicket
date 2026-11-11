package com.rvt.ticket.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cache-Control cho tệp tĩnh. URL có mã phiên bản theo nội dung (/css/app-&lt;md5&gt;.css, sinh bởi resource chain)
 * không bao giờ đổi nội dung nên được lưu 1 năm. URL không có mã (import trong JS, liên kết cũ) chỉ lưu 1 giờ.
 * Đặt trước Spring Security: khi đã có Cache-Control, Security không ghi đè bằng "no-store".
 * Trang HTML và API không bị ảnh hưởng.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class StaticCacheFilter extends OncePerRequestFilter {
    private static final Pattern STATIC = Pattern.compile("^/(css|js|images|fonts|vendor)/.+\\.[A-Za-z0-9]{2,5}$");
    private static final Pattern VERSIONED = Pattern.compile("-[0-9a-f]{32}\\.[A-Za-z0-9]{2,5}$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())
            || !STATIC.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", VERSIONED.matcher(request.getRequestURI()).find()
            ? "public, max-age=31536000, immutable" : "public, max-age=3600");
        chain.doFilter(request, response);
    }

    static boolean isStatic(String uri) { return STATIC.matcher(uri).matches(); }
    static boolean isVersioned(String uri) { return VERSIONED.matcher(uri).find(); }
}
