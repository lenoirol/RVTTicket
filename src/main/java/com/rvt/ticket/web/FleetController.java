package com.rvt.ticket.web;
import com.rvt.ticket.booking.*;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FleetController {
    private final FleetService fleet;private final PlaceService places;private final TicketService tickets;
    public FleetController(FleetService fleet,PlaceService places,TicketService tickets){this.fleet=fleet;this.places=places;this.tickets=tickets;}
    @GetMapping("/admin/phuong-tien") String vehicles(){return "fleet";}
    @GetMapping("/admin/phuong-tien/moi") String create(Model m){m.addAttribute("vehicleForm",new VehicleForm("","","","","XE_KHACH",true,"[]"));m.addAttribute("vehicleId",null);return "vehicle-form";}
    @GetMapping("/admin/phuong-tien/{id}") String edit(@PathVariable int id,Model m){m.addAttribute("vehicleForm",fleet.form(id));m.addAttribute("vehicleId",id);return "vehicle-form";}
    @PostMapping({"/admin/phuong-tien/moi","/admin/phuong-tien/{id}"})
    String save(@PathVariable(required=false) Integer id,@Valid @ModelAttribute("vehicleForm") VehicleForm form,BindingResult errors,Model m,Principal p,RedirectAttributes flash){
        if(!errors.hasErrors())try{
            fleet.save(id,form,p.getName());flash.addFlashAttribute("message","Đã lưu phương tiện và sơ đồ. Sơ đồ trên chuyến cũ không bị thay đổi.");return "redirect:/admin/phuong-tien";
        }catch(IllegalArgumentException|DuplicateKeyException e){m.addAttribute("error",e instanceof DuplicateKeyException?"Mã phương tiện hoặc số đăng ký đã tồn tại.":e.getMessage());}
        else m.addAttribute("error","Kiểm tra thông tin phương tiện và sơ đồ.");
        m.addAttribute("fieldErrors",InputErrors.collect(errors));m.addAttribute("vehicleId",id);return "vehicle-form";
    }
    @GetMapping("/admin/dia-diem") String places(){return "places";}
    @PostMapping("/admin/dia-diem") String addPlace(@RequestParam String province,@RequestParam String name,Principal p,RedirectAttributes flash){
        try{places.add(province,name,p.getName());flash.addFlashAttribute("message","Đã thêm địa điểm vào danh mục. Cần tạo chuyến mới để có vé bán cho địa điểm này.");}
        catch(IllegalArgumentException|DuplicateKeyException e){flash.addFlashAttribute("error",e instanceof DuplicateKeyException?"Tên địa điểm đã có trong danh mục.":e.getMessage());}
        return "redirect:/admin/dia-diem";
    }
    @PostMapping("/admin/chuyen-di/{id}/huy") String cancel(@PathVariable int id,@RequestParam String reason,Principal p,RedirectAttributes flash){
        try{tickets.cancelTrip(id,reason,p.getName());flash.addFlashAttribute("message","Đã hủy chuyến, giải phóng chỗ và hoàn các giao dịch mô phỏng liên quan.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}return "redirect:/admin/chuyen-di/"+id;
    }
}
