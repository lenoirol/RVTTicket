package com.rvt.ticket.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Địa chỉ trang có đuôi ".html" (ví dụ /chuyen-di.html?type=XE_KHACH).
 *
 * <ul>
 *   <li>Vào: bỏ đuôi ".html" trước khi tới Spring Security và Spring MVC, nên phân quyền, CSRF và controller
 *       vẫn thấy đường dẫn gốc (/chuyen-di). Địa chỉ không có ".html" vẫn dùng được (VNPay, liên kết cũ).</li>
 *   <li>Ra: mọi liên kết do Thymeleaf (@{...}) và mọi redirect sinh ra được thêm ".html" cho đường dẫn trang.
 *       Tệp tĩnh, tệp có đuôi (.png/.csv/.ics), JSON/AJAX và callback VNPay giữ nguyên.</li>
 * </ul>
 * Chạy trước Spring Security (order cao nhất) để quy tắc phân quyền khớp đúng đường dẫn đã bỏ ".html".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HtmlSuffixFilter extends OncePerRequestFilter {
    private static final String SUFFIX = ".html";
    private static final Pattern URI_WITH_SUFFIX = Pattern.compile("^(.+?)\\.html((?:;[^/]*)?)$");
    /** Không thêm .html cho tài nguyên tĩnh, API/AJAX và callback bên ngoài. */
    private static final Pattern EXCLUDED = Pattern.compile(
        "^/(css|js|images|fonts|api|error|actuator|thanh-toan/vnpay)(/.*)?$"
        + "|^/tai-khoan/thong-bao(/.*)?$|/cho-trong$");
    private static final Pattern ABSOLUTE = Pattern.compile("^(https?://([^/:?#\\[]+)(?::\\d+)?)(/.*)?$", Pattern.CASE_INSENSITIVE);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(stripped(request), new SuffixResponse(response, request.getServerName()));
    }

    private static HttpServletRequest stripped(HttpServletRequest request) {
        String uri = request.getRequestURI();
        Matcher m = URI_WITH_SUFFIX.matcher(uri == null ? "" : uri);
        if (!m.matches()) return request;
        String path = m.group(1).equals("/index") ? "/" : m.group(1);
        String strippedUri = path + m.group(2);
        String servletPath = request.getServletPath();
        String strippedServlet = servletPath != null && servletPath.endsWith(SUFFIX)
            ? (servletPath.equals("/index.html") ? "/" : servletPath.substring(0, servletPath.length() - SUFFIX.length()))
            : servletPath;
        return new HttpServletRequestWrapper(request) {
            @Override public String getRequestURI() { return strippedUri; }
            @Override public String getServletPath() { return strippedServlet; }
            @Override public StringBuffer getRequestURL() {
                var url = super.getRequestURL();
                int end = url.indexOf(SUFFIX);
                return end < 0 ? url : url.delete(end, end + SUFFIX.length());
            }
        };
    }

    /** Thêm ".html" cho đường dẫn trang nội bộ; giữ nguyên mọi URL khác. Gọi lại nhiều lần vẫn cho cùng kết quả. */
    public static String withSuffix(String url, String host) {
        if (url == null || url.isEmpty() || url.startsWith("//")) return url;
        String prefix = "", rest = url;
        if (!url.startsWith("/")) {
            Matcher m = ABSOLUTE.matcher(url);
            if (!m.matches() || m.group(3) == null || !m.group(2).equalsIgnoreCase(host)) return url;
            prefix = m.group(1);
            rest = m.group(3);
        }
        int cut = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '?' || c == '#') { cut = i; break; }
        }
        String path = rest.substring(0, cut), tail = rest.substring(cut);
        if (path.length() < 2 || path.endsWith("/") || EXCLUDED.matcher(path).find()) return url;
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (last.indexOf('.') >= 0 || last.indexOf(';') >= 0) return url;
        return prefix + path + SUFFIX + tail;
    }

    private static final class SuffixResponse extends HttpServletResponseWrapper {
        private final String host;
        SuffixResponse(HttpServletResponse response, String host) { super(response); this.host = host; }
        @Override public String encodeURL(String url) { return super.encodeURL(withSuffix(url, host)); }
        @Override public String encodeRedirectURL(String url) { return super.encodeRedirectURL(withSuffix(url, host)); }
        @Override public void sendRedirect(String location) throws IOException { super.sendRedirect(withSuffix(location, host)); }
        // RedirectView (HTTP 303) ghi thẳng tiêu đề Location thay vì gọi sendRedirect.
        @Override public void setHeader(String name, String value) { super.setHeader(name, "Location".equalsIgnoreCase(name) ? withSuffix(value, host) : value); }
        @Override public void addHeader(String name, String value) { super.addHeader(name, "Location".equalsIgnoreCase(name) ? withSuffix(value, host) : value); }
    }
}
