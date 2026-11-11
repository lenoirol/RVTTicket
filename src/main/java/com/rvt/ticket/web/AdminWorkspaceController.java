package com.rvt.ticket.web;

import com.rvt.ticket.booking.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminWorkspaceController {
    private final AdminWorkspaceService workspace;private final TripBatchService batches;private final AdminIdentityService identity;private final FleetService fleet;private final TicketService tickets;
    public AdminWorkspaceController(AdminWorkspaceService workspace,TripBatchService batches,AdminIdentityService identity,FleetService fleet,TicketService tickets){this.workspace=workspace;this.batches=batches;this.identity=identity;this.fleet=fleet;this.tickets=tickets;}
    @GetMapping("/admin") String dashboard(@RequestParam(required=false) LocalDate date,Model m) {
        if(date==null)date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));var data=workspace.dashboard(date);
        m.addAttribute("date",date);m.addAttribute("summary",data.get("summary"));m.addAttribute("cash",data.get("cash"));
        @SuppressWarnings("unchecked") var trips=(List<Map<String,Object>>)data.get("trips");m.addAttribute("trips",trips.stream().map(ViewData::localTime).toList());return "admin";
    }
    @GetMapping("/admin/chuyen-di") String trips(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String status,@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,@RequestParam(defaultValue="1") int page,Model m) {
        filters(m,q,type,status,start,end);page(m,workspace.trips(q,type,status,start,end,page));return "admin-trips";
    }
    @GetMapping("/admin/chuyen-di/{id}") String detail(@PathVariable int id,Model m){
        var trip=workspace.tripDetail(id);m.addAttribute("trip",ViewData.localTime(trip));
        m.addAttribute("assignmentFleet",fleet.all().stream().filter(v->Boolean.TRUE.equals(v.get("HoatDong"))&&Objects.equals(v.get("LoaiVe"),trip.get("LoaiVe"))).toList());return "admin-trip";
    }
    @PostMapping("/admin/chuyen-di/{id}/phuong-tien") String assign(@PathVariable int id,@RequestParam int vehicleId,Principal p,RedirectAttributes flash){
        try{tickets.assignVehicle(id,vehicleId,p.getName());flash.addFlashAttribute("message","Đã phân công phương tiện. Chuyến vẫn đóng bán; kiểm tra cấu hình trước khi mở bán.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/admin/chuyen-di/"+id;
    }
    @GetMapping("/admin/don-ve") String orders(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String status,@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,@RequestParam(required=false) Integer tripId,@RequestParam(defaultValue="1") int page,Model m) {
        filters(m,q,type,status,start,end);m.addAttribute("tripId",tripId);page(m,workspace.orders(q,type,status,start,end,tripId,page));return "admin-orders";
    }
    @GetMapping("/admin/khach-hang") String customers(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="1") int page,Model m) {
        m.addAttribute("q",q);m.addAttribute("state",state);page(m,workspace.customers(q,state,page));return "admin-customers";
    }
    @GetMapping("/admin/khach-hang/{id}") String customer(@PathVariable int id,@RequestParam(defaultValue="1") int page,Model m) {
        m.addAttribute("customer",ViewData.localTime(workspace.customer(id)));page(m,workspace.customerOrders(id,page));return "admin-customer";
    }
    @PostMapping("/admin/khach-hang/{id}/khoa") String lock(@PathVariable int id,@RequestParam boolean locked,@RequestParam(defaultValue="") String reason,Principal p,RedirectAttributes flash) {
        try{workspace.lockCustomer(id,locked,reason,p.getName());flash.addFlashAttribute("message",locked?"Đã khóa tài khoản. Lịch sử vé được giữ nguyên.":"Đã mở khóa tài khoản.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}return "redirect:/admin/khach-hang/"+id;
    }
    @GetMapping("/admin/nhat-ky") String logs(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String action,@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,@RequestParam(defaultValue="1") int page,Model m) {
        filters(m,q,"","",start,end);m.addAttribute("action",action);m.addAttribute("actionLabels",AdminWorkspaceService.ACTIONS);page(m,workspace.logs(q,action,start,end,page));return "admin-logs";
    }
    @GetMapping("/admin/tai-khoan") String profile(){return "admin-profile";}
    @PostMapping("/admin/tai-khoan/mat-khau") String password(@RequestParam String oldPassword,@RequestParam String password,@RequestParam String confirmation,Principal p,RedirectAttributes flash,jakarta.servlet.http.HttpSession session) {
        try{identity.changePassword(p.getName(),oldPassword,password,confirmation);org.springframework.security.core.context.SecurityContextHolder.clearContext();session.invalidate();return "redirect:/login?changed";}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}return "redirect:/admin/tai-khoan";
    }
    @GetMapping("/admin/chuyen-di/moi") String newTrip(@RequestParam(required=false) Integer copy,@RequestParam(defaultValue="") String route,Model m) {
        var f=copy==null?new TripForm():batches.duplicate(copy);
        if(!route.isBlank()&&copy==null){
            var r=SeaRouteCatalog.routes().stream().filter(x->x.code().equals(route)).findFirst().orElseThrow(()->new IllegalArgumentException("Tuyến tham khảo không hợp lệ."));
            f.setCode("RVT-"+r.code()+"-"+UUID.randomUUID().toString().substring(0,6).toUpperCase());f.setLayout("BOAT");f.setFrom(r.from());f.setTo(r.to());f.setPickup(r.portFrom());f.setDropoff(r.portTo());f.setPrice(java.math.BigDecimal.valueOf(r.adult()));f.setChildPrice(java.math.BigDecimal.valueOf(r.child()));f.setSeniorPrice(java.math.BigDecimal.valueOf(r.senior()));
            f.setDescription("Giá ngày thường tham khảo 03/10/2026, cần rà soát cuối tuần/lễ và phụ thu VIP của phương tiện. "+r.note());
        }
        m.addAttribute("tripForm",f);m.addAttribute("days",1);return "trip-form";
    }
    @PostMapping("/admin/chuyen-di/xem-lai") String preview(@Valid @ModelAttribute("tripForm") TripForm form,BindingResult errors,@RequestParam(defaultValue="1") int days,Model m) {
        m.addAttribute("days",days);
        if(errors.hasErrors()){m.addAttribute("error","Kiểm tra thông tin chuyến trước khi xem lại.");m.addAttribute("fieldErrors",InputErrors.collect(errors));return "trip-form";}
        try{m.addAttribute("batch",batches.preview(form,days));m.addAttribute("selectedVehicle",fleet.all().stream().filter(v->form.getVehicleId().equals(v.get("PhuongTienID"))).findFirst().orElseThrow());return "trip-preview";}catch(IllegalArgumentException e){m.addAttribute("error",e.getMessage());return "trip-form";}
    }
    @PostMapping("/admin/chuyen-di/chinh-lai") String revise(@ModelAttribute("tripForm") TripForm form,@RequestParam(defaultValue="1") int days,Model m){m.addAttribute("days",days);return "trip-form";}
    @PostMapping("/admin/chuyen-di/moi") String create(@Valid @ModelAttribute("tripForm") TripForm form,BindingResult errors,@RequestParam(defaultValue="1") int days,@RequestParam(defaultValue="false") boolean reviewed,Model m,Principal p,RedirectAttributes flash) {
        m.addAttribute("days",days);
        if(!reviewed)return preview(form,errors,days,m);
        if(errors.hasErrors()){m.addAttribute("error","Thông tin chuyến chưa hợp lệ.");m.addAttribute("fieldErrors",InputErrors.collect(errors));return "trip-form";}
        try{var ids=batches.create(form,days,p.getName());flash.addFlashAttribute("message","Đã tạo "+ids.size()+" chuyến ở trạng thái đóng bán. Kiểm tra thông tin rồi mở bán từng chuyến.");return "redirect:/admin/chuyen-di";}
        catch(IllegalArgumentException e){m.addAttribute("error",e.getMessage());}
        catch(DataAccessException e){m.addAttribute("error","Không tạo chuyến nào. Có thể mã chuyến đã tồn tại hoặc dữ liệu không hợp lệ; hãy kiểm tra lại.");}
        return "trip-form";
    }
    private void page(Model m,AdminWorkspaceService.Page data){m.addAttribute("listing",new AdminWorkspaceService.Page(data.rows().stream().map(ViewData::localTime).toList(),data.total(),data.page(),data.pages()));}
    private void filters(Model m,String q,String type,String status,LocalDate start,LocalDate end){m.addAttribute("q",q);m.addAttribute("type",type);m.addAttribute("status",status);m.addAttribute("start",start);m.addAttribute("end",end);}
}
