package com.rvt.ticket.web;

import com.rvt.ticket.booking.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DuplicateKeyException;

@Controller
public class AccountController {
    private final AccountService accounts;
    private final TicketService tickets;
    private final WalletService wallet;private final NotificationService notifications;
    private final com.rvt.ticket.config.RegistrationSignIn signIn;
    public AccountController(AccountService accounts,TicketService tickets,WalletService wallet,NotificationService notifications,com.rvt.ticket.config.RegistrationSignIn signIn){this.accounts=accounts;this.tickets=tickets;this.wallet=wallet;this.notifications=notifications;this.signIn=signIn;}
    @GetMapping("/workspace")
    String workspace(Authentication auth) {
        if(auth==null)return "redirect:/login";
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")))return "redirect:/admin";
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_OPERATOR")))return "redirect:/admin/lich-hen";
        return "redirect:/tai-khoan";
    }
    @GetMapping("/dang-ky") String registration(Model m){m.addAttribute("registration",new RegistrationForm("","","",""));return "register";}
    @PostMapping("/dang-ky")
    String registration(@Valid @ModelAttribute("registration") RegistrationForm form,BindingResult errors,Model m,RedirectAttributes flash,jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) {
        if(errors.hasErrors()){m.addAttribute("error","Kiểm tra tên đăng nhập, mật khẩu (10–64 ký tự), họ tên và số điện thoại.");m.addAttribute("fieldErrors",InputErrors.collect(errors));return "register";}
        try{accounts.register(form);String pending=com.rvt.ticket.config.BookingLoginSuccessHandler.pending(request,response);signIn.signIn(form.username(),request,response);if(pending!=null)request.getSession().setAttribute("bookingContinue",pending);flash.addFlashAttribute("message","Tài khoản đã sẵn sàng. Bạn có thể bổ sung thông tin hành khách để điền nhanh khi đặt vé.");return "redirect:/tai-khoan/ho-so";}
        catch(IllegalArgumentException|DuplicateKeyException e){m.addAttribute("error","Tên đăng nhập đã có người sử dụng hoặc thông tin chưa hợp lệ.");return "register";}
    }
    @GetMapping("/tai-khoan")
    String account(Principal p,@RequestParam(defaultValue="all") String filter,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,Model m) {
        var history=accounts.history(p.getName(),filter,q,page);
        m.addAttribute("profile",accounts.profile(p.getName()));m.addAttribute("walletBalance",wallet.balance(p.getName()));m.addAttribute("orders",history.rows().stream().map(ViewData::localTime).toList());
        m.addAttribute("history",history);m.addAttribute("filter",filter);m.addAttribute("q",q);return "account";
    }
    @GetMapping("/tai-khoan/ho-so") String profile(Principal p,Model m,HttpSession session){m.addAttribute("profile",accounts.profile(p.getName()));m.addAttribute("today",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));m.addAttribute("bookingContinue",session.getAttribute("bookingContinue"));return "customer-profile";}
    @PostMapping("/tai-khoan/ho-so") String profile(@RequestParam String name,@RequestParam String phone,@RequestParam(defaultValue="") String email,@RequestParam(required=false) java.time.LocalDate birthDate,@RequestParam(defaultValue="CCCD") String documentType,@RequestParam(defaultValue="") String document,@RequestParam(defaultValue="Việt Nam") String nationality,Principal p,RedirectAttributes flash){
        try{accounts.updatePassengerProfile(p.getName(),name,phone,email,birthDate,documentType,document,nationality);flash.addFlashAttribute("message","Đã lưu hồ sơ. Thông tin trên các vé đã đặt vẫn được giữ nguyên.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());flash.addFlashAttribute("draft",java.util.Map.of("HoTen",name,"SoDienThoai",phone,"Email",email,"NgaySinh",birthDate==null?"":birthDate.toString(),"LoaiGiayTo",documentType,"SoGiayTo",document,"QuocTich",nationality));}
        return "redirect:/tai-khoan/ho-so";
    }
    @PostMapping("/tai-khoan/mat-khau") String password(@RequestParam String oldPassword,@RequestParam String password,@RequestParam String confirmation,Principal p,HttpSession session,RedirectAttributes flash){
        try{accounts.changePassword(p.getName(),oldPassword,password,confirmation);org.springframework.security.core.context.SecurityContextHolder.clearContext();session.invalidate();return "redirect:/login?changed";}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());return "redirect:/tai-khoan/ho-so";}
    }
    @GetMapping("/tai-khoan/so-du") String wallet(Principal p,@RequestParam(defaultValue="1") int page,Model m){
        var entries=wallet.history(p.getName(),page);m.addAttribute("walletBalance",wallet.balance(p.getName()));m.addAttribute("page",page);m.addAttribute("hasNext",entries.size()>20);m.addAttribute("entries",entries.stream().limit(20).map(ViewData::localTime).toList());return "wallet";
    }
    @PostMapping("/tai-khoan/don/{code}/gan") String claim(@PathVariable String code,@RequestParam String phone,Principal p,HttpSession session,RedirectAttributes flash){
        try{wallet.claimLegacy(code,phone,p.getName());session.setAttribute("order:"+code,phone);flash.addFlashAttribute("message","Đã gắn đơn cũ vào tài khoản. Tiền hoàn chờ nhận (nếu có) đã vào số dư RVT.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/don-ve/"+code;
    }
    @GetMapping("/tai-khoan/thong-bao") @ResponseBody java.util.Map<String,Object> notifications(Principal p){return notifications.inbox(p.getName());}
    @PostMapping("/tai-khoan/thong-bao/{id}/doc") @ResponseBody java.util.Map<String,Object> read(@PathVariable long id,Principal p){notifications.read(p.getName(),id);return java.util.Map.of("ok",true);}
    @PostMapping("/tai-khoan/thong-bao/doc-tat-ca") @ResponseBody java.util.Map<String,Object> readAll(Principal p){notifications.readAll(p.getName());return java.util.Map.of("ok",true);}
    @GetMapping("/tai-khoan/don/{code}")
    String order(@PathVariable String code,Principal p,HttpSession session) {
        session.setAttribute("order:"+code,tickets.ownerPhone(code,p.getName()));return "redirect:/don-ve/"+code;
    }
}
