package com.rvt.ticket.web;

import com.rvt.ticket.booking.*;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OperationsController {
    private final TicketService service;
    private final boolean allowDemo;
    public OperationsController(TicketService service,@org.springframework.beans.factory.annotation.Value("${rvt.payment.allow-demo:false}") boolean allowDemo){this.service=service;this.allowDemo=allowDemo;}
    @GetMapping("/staff")
    String staff(@RequestParam(defaultValue="") String q,Model m){if(q.length()>40)q=q.substring(0,40);m.addAttribute("q",q);m.addAttribute("orders",service.staffOrders(q).stream().map(ViewData::localTime).toList());return "staff";}
    @GetMapping({"/staff/don/{code}","/admin/don-ve/{code}"})
    String order(@PathVariable String code,Model m){m.addAttribute("allowDemo",allowDemo);m.addAttribute("order",ViewData.localTime(service.staffOrder(code)));return "staff-order";}
    @PostMapping({"/staff/don/{code}","/admin/don-ve/{code}"})
    String action(@PathVariable String code,@RequestParam String action,@RequestParam(required=false) Integer ticketId,@RequestParam(defaultValue="") String reason,Principal p,RedirectAttributes flash) {
        try{if("pay".equals(action)&&!allowDemo)throw new IllegalArgumentException("Xác nhận thanh toán thử đã tắt. Kết quả VNPay phải được xác nhận bằng IPN.");if("cancel".equals(action))service.adminCancelOrder(code,reason,p.getName());else service.staffAction(code,action,ticketId,p.getName());flash.addFlashAttribute("message","Đã xử lý đơn vé và ghi nhật ký thao tác.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/admin/don-ve/"+code;
    }
    @PostMapping("/admin/chuyen-di/{id}")
    String sale(@PathVariable int id,@RequestParam String status,Principal principal,RedirectAttributes flash) {
        try {service.changeSale(id,status,principal.getName());flash.addFlashAttribute("message","Đã cập nhật trạng thái mở bán.");}
        catch(IllegalArgumentException e) {flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/admin/chuyen-di";
    }
    @GetMapping("/admin/chinh-sach")
    String rules(Model m){m.addAttribute("rules",service.allRules());return "rules";}
    @PostMapping("/admin/chinh-sach")
    String rules(@Valid @ModelAttribute RuleForm form,BindingResult errors,Principal p,RedirectAttributes flash){
        if(errors.hasErrors())flash.addFlashAttribute("error","Chính sách không hợp lệ. Kiểm tra định mức, đơn giá và thời hạn hủy.");
        else try{service.saveRule(form,p.getName());flash.addFlashAttribute("message","Chính sách mới đã áp dụng cho các đơn đặt tiếp theo.");}catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/admin/chinh-sach";
    }
    @GetMapping("/admin/chuyen-di/{id}/thong-tin")
    String info(@PathVariable int id,Model m){m.addAttribute("trip",ViewData.localTime(service.trip(id)));return "trip-info";}
    @PostMapping("/admin/chuyen-di/{id}/thong-tin")
    String info(@PathVariable int id,@Valid @ModelAttribute TripInfo form,BindingResult errors,Principal p,RedirectAttributes flash) {
        if(errors.hasErrors()){flash.addFlashAttribute("error","Điểm đón/trả tối đa 150 ký tự; tiện ích 500 ký tự; mô tả 1.000 ký tự.");return "redirect:/admin/chuyen-di/"+id+"/thong-tin";}
        service.updateInfo(id,form.pickup(),form.dropoff(),form.amenities(),form.description(),p.getName());
        flash.addFlashAttribute("message","Đã cập nhật thông tin chuyến.");return "redirect:/admin/chuyen-di/"+id;
    }
    public record TripInfo(@NotBlank @Size(max=150) String pickup,@NotBlank @Size(max=150) String dropoff,@NotNull @Size(max=500) String amenities,@NotNull @Size(max=1000) String description){}
}
