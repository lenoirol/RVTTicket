package com.rvt.ticket.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Public error pages never echo exception messages, submitted fields or SQL. */
@Controller
public class ProductErrorController implements ErrorController {
    @RequestMapping("/error")
    String error(HttpServletRequest request,HttpServletResponse response,Model model) {
        Object value=request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status=value instanceof Number number?number.intValue():500;
        response.setStatus(status);
        model.addAttribute("errorStatus",status);
        model.addAttribute("error",switch(status) {
            case 404 -> "Đường dẫn này không còn tồn tại hoặc chưa được tạo. Bạn có thể về trang chủ để tìm chuyến, hoặc mở lại đơn trong tài khoản.";
            case 403 -> "Bạn chưa có quyền thực hiện thao tác này, hoặc phiên biểu mẫu đã hết hạn. Hãy mở lại trang và đăng nhập đúng tài khoản; dữ liệu vé đã lưu không bị thay đổi.";
            case 405 -> "Đường dẫn không hỗ trợ thao tác này. Hãy sử dụng các nút trên website để tiếp tục.";
            case 400 -> "Thông tin gửi lên chưa đúng định dạng. Hãy mở lại trang và kiểm tra biểu mẫu.";
            default -> "Hệ thống đang gặp gián đoạn. Không gửi lại thao tác thanh toán liên tục; hãy kiểm tra trạng thái đơn trước khi thử lại.";
        });
        return "error";
    }
}
