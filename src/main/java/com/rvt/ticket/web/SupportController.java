package com.rvt.ticket.web;

import com.rvt.ticket.booking.*;
import java.security.Principal;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SupportController {
    private final SupportService support;
    public SupportController(SupportService support){this.support=support;}
    @GetMapping("/huong-dan") String help(){return "help";}
    @GetMapping("/bao-mat") String privacy(){return "privacy";}
    @GetMapping({"/tai-khoan/ho-tro","/admin/ho-tro"})
    String list(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="") String code,Principal p,Model m,jakarta.servlet.http.HttpServletRequest request){
        boolean admin=request.getRequestURI().startsWith("/admin/");
        var data=support.list(admin?null:p.getName(),status,page);
        m.addAttribute("supportAdmin",admin);m.addAttribute("status",status);m.addAttribute("code",code);m.addAttribute("topics",SupportService.TOPICS);
        m.addAttribute("listing",new SupportService.Page(data.rows().stream().map(ViewData::localTime).toList(),data.total(),data.page(),data.next()));return "support";
    }
    @PostMapping("/tai-khoan/ho-tro") String create(@RequestParam(defaultValue="") String code,@RequestParam String topic,@RequestParam String title,@RequestParam String content,Principal p,RedirectAttributes flash){
        try{int id=support.create(p.getName(),code,topic,title,content);flash.addFlashAttribute("message","Đã tiếp nhận yêu cầu. Bạn xem phản hồi tại trang này; không gửi email hoặc SMS.");return "redirect:/tai-khoan/ho-tro/"+id;}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());flash.addFlashAttribute("draft",Map.of("code",code,"topic",topic,"title",title,"content",content));return "redirect:/tai-khoan/ho-tro";}
    }
    @GetMapping({"/tai-khoan/ho-tro/{id}","/admin/ho-tro/{id}"})
    String detail(@PathVariable int id,Principal p,Model m,jakarta.servlet.http.HttpServletRequest request){
        boolean admin=request.getRequestURI().startsWith("/admin/");var detail=support.detail(id,admin?null:p.getName());
        @SuppressWarnings("unchecked") var messages=(List<Map<String,Object>>)detail.get("messages");
        m.addAttribute("thread",ViewData.localTime(detail));m.addAttribute("messages",messages.stream().map(ViewData::localTime).toList());m.addAttribute("supportAdmin",admin);m.addAttribute("topics",SupportService.TOPICS);return "support-detail";
    }
    @PostMapping({"/tai-khoan/ho-tro/{id}","/admin/ho-tro/{id}"})
    String reply(@PathVariable int id,@RequestParam String content,@RequestParam(defaultValue="DANG_XU_LY") String status,Principal p,RedirectAttributes flash,jakarta.servlet.http.HttpServletRequest request){
        boolean admin=request.getRequestURI().startsWith("/admin/");
        try{support.reply(id,p.getName(),admin,content,status);flash.addFlashAttribute("message",admin?"Đã lưu phản hồi và trạng thái xử lý.":"Đã gửi nội dung bổ sung.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());flash.addFlashAttribute("draftContent",content);}
        return "redirect:"+(admin?"/admin":"/tai-khoan")+"/ho-tro/"+id;
    }
}
