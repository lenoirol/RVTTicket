package com.rvt.ticket.web;

import com.rvt.ticket.booking.*;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Màn hình quản lý vai trò (chỉ ROLE_ADMIN, theo quy tắc /admin/** trong SecurityConfig). */
@Controller
public class RoleController {
    private final RoleService roles;
    public RoleController(RoleService roles){this.roles=roles;}

    @GetMapping("/admin/vai-tro") String page(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="1") int page,Model m) {
        m.addAttribute("staff",roles.staff());m.addAttribute("staffRoles",RoleService.STAFF_ROLES);m.addAttribute("customerRoles",RoleService.CUSTOMER_ROLES);
        m.addAttribute("q",q);m.addAttribute("state",state);m.addAttribute("listing",roles.customers(q,state,page));
        return "admin-roles";
    }
    @PostMapping("/admin/vai-tro/nhan-su/{id}") String staffRole(@PathVariable int id,@RequestParam String role,Principal p,RedirectAttributes flash) {
        try{roles.changeStaffRole(id,role,p.getName());flash.addFlashAttribute("message","Đã đổi vai trò nhân sự. Tài khoản này cần đăng nhập lại để nhận quyền mới.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/admin/vai-tro";
    }
    @PostMapping("/admin/vai-tro/nhan-su") String createStaff(@RequestParam String username,@RequestParam String name,@RequestParam String role,@RequestParam String password,@RequestParam String confirmation,Principal p,RedirectAttributes flash) {
        try{roles.createStaff(username,name,role,password,confirmation,p.getName());flash.addFlashAttribute("message","Đã tạo tài khoản nhân sự.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/admin/vai-tro";
    }
    @PostMapping("/admin/vai-tro/khach/{id}") String customerRole(@PathVariable int id,@RequestParam String role,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="1") int page,Principal p,RedirectAttributes flash) {
        try{roles.changeCustomerRole(id,role,p.getName());flash.addFlashAttribute("message","Đã đổi vai trò khách hàng. Khách cần đăng nhập lại để nhận quyền mới.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        flash.addAttribute("q",q);flash.addAttribute("state",state);flash.addAttribute("page",page);
        return "redirect:/admin/vai-tro";
    }
}
