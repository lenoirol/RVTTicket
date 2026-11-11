package com.rvt.ticket.web;

import com.rvt.ticket.booking.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.security.Principal;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tầng 2 / PaymentController: tiếp nhận và điều phối luồng thanh toán (VNPay hoặc số dư RVT), kết quả trả về bằng PRG. */
@Controller
public class PaymentController {
    private final TicketService service;
    private final com.rvt.ticket.payment.VnpayService vnpay;
    private final boolean allowDemo;
    public PaymentController(TicketService service,com.rvt.ticket.payment.VnpayService vnpay,@org.springframework.beans.factory.annotation.Value("${rvt.payment.allow-demo:false}") boolean allowDemo) {
        this.service=service;this.vnpay=vnpay;this.allowDemo=allowDemo;
    }
    private String authorizedPhone(String code,HttpSession s) {
        var phone=s.getAttribute("order:"+code);
        if(phone==null) throw new IllegalArgumentException("Vui lòng tra cứu bằng mã đơn và số điện thoại để xem vé.");
        return (String)phone;
    }
    @PostMapping("/don-ve/{code}/thanh-toan")
    String pay(@PathVariable String code,HttpSession session,@RequestParam(defaultValue="VNPAY") String method,Principal principal,RedirectAttributes flash,jakarta.servlet.http.HttpServletRequest request) {
        try {
            if("VNPAY".equals(method)) {
                String origin=request.getScheme()+"://"+request.getServerName()
                    +((request.getServerPort()==80&&"http".equals(request.getScheme())||request.getServerPort()==443&&"https".equals(request.getScheme()))?"":":"+request.getServerPort());
                return "redirect:"+vnpay.start(code,principal.getName(),request.getRemoteAddr(),origin);
            }
            if("DEMO".equals(method)&&!allowDemo)throw new IllegalArgumentException("Thanh toán thử trực tiếp đã được tắt. Vui lòng chọn VNPay hoặc số dư RVT.");
            service.pay(code,authorizedPhone(code,session),method,principal.getName());flash.addFlashAttribute("message","Vé đã được xác nhận. Bạn có thể xem chi tiết giao dịch bên dưới.");
        }
        catch(IllegalArgumentException e) {flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/don-ve/"+code;
    }
}
