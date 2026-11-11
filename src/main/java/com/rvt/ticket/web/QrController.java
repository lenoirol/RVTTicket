package com.rvt.ticket.web;
import com.rvt.ticket.booking.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class QrController {
    private final QrService qr;private final TicketService tickets;
    public QrController(QrService qr,TicketService tickets){this.qr=qr;this.tickets=tickets;}
    @GetMapping(value="/don-ve/{code}/qr/{ticketId}.png",produces=MediaType.IMAGE_PNG_VALUE)
    @ResponseBody ResponseEntity<byte[]> image(@PathVariable String code,@PathVariable int ticketId,HttpSession session){
        var phone=session.getAttribute("order:"+code);
        if(phone==null)return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        var order=tickets.order(code,(String)phone);
        if(!Set.of("DA_THANH_TOAN","DA_SU_DUNG").contains((String)order.get("TrangThai")))return ResponseEntity.badRequest().build();
        @SuppressWarnings("unchecked") var list=(List<Map<String,Object>>)order.get("tickets");
        var ticket=list.stream().filter(t->((Number)t.get("DangKyID")).intValue()==ticketId).findFirst();
        if(ticket.isEmpty())return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(qr.image((String)ticket.get().get("QrToken")));
    }
    @GetMapping({"/staff/kiem-ve","/admin/kiem-ve"})
    String scan(@RequestParam(required=false) String token,Model m){
        if(token!=null&&!token.isBlank())try{
            var result=qr.verify(token);
            @SuppressWarnings("unchecked") var order=(Map<String,Object>)result.get("order");
            m.addAttribute("order",ViewData.localTime(order));m.addAttribute("verifiedTicket",result.get("ticketId"));m.addAttribute("verificationMessage",result.get("message"));return "staff-order";
        }catch(IllegalArgumentException e){m.addAttribute("error",e.getMessage());}
        return "scan";
    }
    @PostMapping({"/staff/kiem-ve/anh","/admin/kiem-ve/anh"})
    String upload(@RequestParam("photo") MultipartFile photo,RedirectAttributes flash) throws java.io.IOException {
        try{
            if(photo.getSize()>4*1024*1024)throw new IllegalArgumentException("Ảnh tối đa 4 MB.");
            return "redirect:/staff/kiem-ve?token="+qr.decode(photo.getBytes());
        }catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());return "redirect:/staff/kiem-ve";}
    }
}
