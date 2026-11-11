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

/** Tầng 2 / ReservationController: tiếp nhận form đặt chỗ, tra cứu và hủy đơn. Mọi POST đều Redirect rồi GET (PRG). */
@Controller
public class ReservationController {
    private final TicketService service;
    private final AccountService accounts;
    private final WalletService wallet;
    private final com.rvt.ticket.payment.VnpayService vnpay;
    private final boolean allowDemo;
    public ReservationController(TicketService service,AccountService accounts,WalletService wallet,
            com.rvt.ticket.payment.VnpayService vnpay,@org.springframework.beans.factory.annotation.Value("${rvt.payment.allow-demo:false}") boolean allowDemo) {
        this.service=service;this.accounts=accounts;this.wallet=wallet;this.vnpay=vnpay;this.allowDemo=allowDemo;
    }
    @InitBinder("booking")
    void bookingFields(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(8);
        binder.setAllowedFields("requestKey","name","phone","email","passengers[*].name","passengers[*].seat","passengers[*].luggage","passengers[*].birthDate","passengers[*].document","passengers[*].documentType","passengers[*].nationality");
    }
    @GetMapping("/dat-ve/{id}")
    String booking(@PathVariable int id,@RequestParam(required=false) Integer returnTrip,@RequestParam(required=false) String outboundOrder,Model m,org.springframework.security.core.Authentication auth) {
        service.requireConfigured(id);
        var form=new BookingForm();
        form.setRequestKey(UUID.randomUUID().toString());
        if(auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CUSTOMER"))){
            var p=accounts.profile(auth.getName());form.setName((String)p.get("HoTen"));form.setPhone((String)p.get("SoDienThoai"));form.setEmail((String)p.get("Email"));
            m.addAttribute("passengerProfile",accounts.passengerDefaults(auth.getName()));
        }
        m.addAttribute("booking",form);roundTripModel(returnTrip,outboundOrder,m);bookingModel(id,m);return "booking";
    }
    /** Khứ hồi: giữ chuyến về / mã đơn chiều đi để hiện nhắc nhở và chuyển tiếp sau khi giữ chỗ chiều đi. */
    private void roundTripModel(Integer returnTrip,String outboundOrder,Model m) {
        if(returnTrip!=null&&service.configured(returnTrip))m.addAttribute("returnTrip",vietnamTime(service.trip(returnTrip)));
        if(outboundOrder!=null&&outboundOrder.matches("[A-Za-z0-9-]{3,40}"))m.addAttribute("outboundOrder",outboundOrder);
    }
    private void bookingModel(int id,Model m) {
        var trip=service.trip(id);trip.computeIfAbsent("GiaTreEm",k->FarePolicy.discounted((BigDecimal)trip.get("GiaCoBan")));trip.computeIfAbsent("GiaCaoTuoi",k->FarePolicy.discounted((BigDecimal)trip.get("GiaCoBan")));m.addAttribute("trip",vietnamTime(trip));m.addAttribute("rule",service.rules((String)trip.get("LoaiVe")));var seats=service.seats(id);m.addAttribute("seats",seats);
        m.addAttribute("hasLayout",seats.stream().allMatch(s->s.get("Tang")!=null));
    }
    @PostMapping("/dat-ve/{id}")
    String book(@PathVariable int id,@Valid @ModelAttribute("booking") BookingForm form,BindingResult errors,@RequestParam(required=false) Integer returnTrip,@RequestParam(required=false) String outboundOrder,Model m,HttpSession session,Principal principal) {
        if(!errors.hasErrors()) {
            try {
                String code=service.book(id,form,principal==null?null:principal.getName());session.setAttribute("order:"+code,service.ownerPhone(code,principal.getName()));if(returnTrip!=null&&service.configured(returnTrip))return "redirect:/dat-ve/"+returnTrip+"?outboundOrder="+code;return "redirect:/don-ve/"+code;
            } catch(IllegalArgumentException e) {m.addAttribute("error",e.getMessage());}
            catch(DataAccessException e) {
                org.slf4j.LoggerFactory.getLogger(ReservationController.class).warn("Booking could not be saved ({})",e.getClass().getSimpleName());
                m.addAttribute("error","Chưa lưu được đơn. Vui lòng kiểm tra thông tin hành khách và tình trạng chỗ, rồi thử lại; không có khoản thanh toán nào được tạo ở bước này.");
            }
        } else {m.addAttribute("error","Hãy kiểm tra họ tên, số điện thoại, email và thông tin của từng hành khách.");m.addAttribute("fieldErrors",InputErrors.collect(errors));}
        m.addAttribute("passengerProfile",accounts.passengerDefaults(principal.getName()));roundTripModel(returnTrip,outboundOrder,m);bookingModel(id,m);return "booking";
    }
    @GetMapping("/tra-cuu") String lookup() {return "lookup";}
    @PostMapping("/tra-cuu")
    String lookup(@RequestParam String code,@RequestParam String phone,HttpSession session,Model m) {
        code=code.strip().toUpperCase();phone=phone.strip();
        try {service.order(code,phone);session.setAttribute("order:"+code,phone);return "redirect:/don-ve/"+code;}
        catch(IllegalArgumentException e) {m.addAttribute("error",e.getMessage());return "lookup";}
    }
    private String authorizedPhone(String code,HttpSession s) {
        var phone=s.getAttribute("order:"+code);
        if(phone==null) throw new IllegalArgumentException("Vui lòng tra cứu bằng mã đơn và số điện thoại để xem vé.");
        return (String)phone;
    }
    @GetMapping("/don-ve/{code}")
    String order(@PathVariable String code,HttpSession session,Model m,org.springframework.security.core.Authentication auth) {
        var order=service.order(code,authorizedPhone(code,session));
        boolean customer=auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CUSTOMER"));
        boolean owner=customer&&Objects.equals(order.get("TaiKhoanID"),accounts.profile(auth.getName()).get("TaiKhoanID"));
        m.addAttribute("ownsOrder",owner);m.addAttribute("canClaim",customer&&order.get("TaiKhoanID")==null);m.addAttribute("walletBalance",customer?wallet.balance(auth.getName()):BigDecimal.ZERO);
        m.addAttribute("vnpayEnabled",vnpay.enabled());m.addAttribute("allowDemo",allowDemo);
        @SuppressWarnings("unchecked") var payments=(List<Map<String,Object>>)order.get("payments");m.addAttribute("payments",payments.stream().map(ViewData::localTime).toList());
        m.addAttribute("order",vietnamTime(order));return "order";
    }
    @PostMapping("/don-ve/{code}/huy")
    String cancel(@PathVariable String code,@RequestParam(defaultValue="") String reason,HttpSession session,Principal principal,RedirectAttributes flash) {
        try {
            if(reason==null||reason.isBlank())throw new IllegalArgumentException("Chọn lý do hủy trước khi xác nhận.");
            service.ownerPhone(code,principal.getName());service.cancel(code,authorizedPhone(code,session),reason);flash.addFlashAttribute("message","Đơn đã hủy. Nếu đã thanh toán, tiền được hoàn 100% vào số dư RVT của bạn.");
        }
        catch(IllegalArgumentException e) {flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/don-ve/"+code;
    }
    private Map<String,Object> vietnamTime(Map<String,Object> source) {
        return ViewData.localTime(source);
    }
}
