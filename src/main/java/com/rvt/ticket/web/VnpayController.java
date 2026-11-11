package com.rvt.ticket.web;

import com.rvt.ticket.booking.TicketService;
import com.rvt.ticket.payment.*;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@Controller
public class VnpayController {
    private final VnpayService gateway;
    private final TicketService tickets;
    private final org.springframework.beans.factory.ObjectProvider<VnpayQuery> queries;
    public VnpayController(VnpayService gateway,TicketService tickets,org.springframework.beans.factory.ObjectProvider<VnpayQuery> queries){this.gateway=gateway;this.tickets=tickets;this.queries=queries;}
    @GetMapping("/thanh-toan/vnpay/ipn") @ResponseBody
    ResponseEntity<Map<String,String>> ipn(@RequestParam MultiValueMap<String,String> params) {
        Map<String,String> reply;
        try {reply=gateway.ipn(VnpayCodec.parameters(params));}
        catch(IllegalArgumentException e){reply=Map.of("RspCode","97","Message","Invalid parameters");}
        catch(DataAccessException e){reply=Map.of("RspCode","99","Message","Please retry notification");}
        return ResponseEntity.ok().header("Cache-Control","no-store").body(reply);
    }
    @GetMapping("/thanh-toan/vnpay/return")
    String result(@RequestParam MultiValueMap<String,String> params,Model m,Principal principal,HttpSession session,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        Map<String,Object> result;
        try{result=gateway.result(VnpayCodec.parameters(params));}
        catch(IllegalArgumentException e){result=Map.of("state","INVALID");}
        m.addAttribute("result",result);m.addAttribute("orderUrl",null);
        if(principal!=null&&result.containsKey("code")) {
            String code=(String)result.get("code");
            try{session.setAttribute("order:"+code,tickets.ownerPhone(code,principal.getName()));m.addAttribute("orderUrl","/don-ve/"+code);}
            catch(IllegalArgumentException ignored){/* A signed receipt never grants access to another customer's order. */}
        }
        return "vnpay-result";
    }
    @GetMapping("/admin/vnpay")
    String attempts(@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="1") int page,Model m) {
        var rows=gateway.attempts(state,page);
        m.addAttribute("rows",rows.stream().limit(20).map(ViewData::localTime).toList());
        m.addAttribute("next",rows.size()>20);m.addAttribute("page",page);m.addAttribute("state",state);
        m.addAttribute("enabled",gateway.enabled());m.addAttribute("queryEnabled",queries.getIfAvailable()!=null);return "admin-vnpay";
    }
    @PostMapping("/admin/vnpay/{reference}/truy-van")
    String query(@PathVariable String reference,org.springframework.web.servlet.mvc.support.RedirectAttributes flash){
        try{var query=queries.getIfAvailable();if(query==null)throw new IllegalArgumentException("Chưa bật kết nối truy vấn VNPay.");query.queryReference(reference);flash.addFlashAttribute("message","Đã thực hiện lượt đối soát. Nếu chưa nhận kết quả xác minh, giao dịch vẫn chờ xử lý; xem thông tin cập nhật trong danh sách.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/admin/vnpay";
    }
    @PostMapping("/admin/vnpay/{reference}/hoan-rvt")
    String resolve(@PathVariable String reference,@RequestParam String reason,Principal principal,org.springframework.web.servlet.mvc.support.RedirectAttributes flash){
        try{gateway.resolveReview(reference,reason,principal.getName());flash.addFlashAttribute("message","Đã xử lý hoàn RVT; đơn chưa gắn tài khoản được lưu khoản hoàn chờ nhận. Vé và chỗ không đổi trạng thái.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());return "redirect:/admin/vnpay?state=UNRESOLVED";}
        return "redirect:/admin/vnpay?state=REFUNDED";
    }
}
