package com.rvt.ticket.web;

import com.rvt.ticket.booking.AppointmentForm;
import com.rvt.ticket.booking.AppointmentService;
import com.rvt.ticket.booking.AppointmentState;
import com.rvt.ticket.strategy.TicketStrategyRegistry;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tiếp nhận và điều phối các yêu cầu đặt lịch hẹn tư vấn (Handle consultation bookings). Không chứa nghiệp vụ. */
@Controller
public class AppointmentController {
    private final AppointmentService service;
    private final TicketStrategyRegistry strategies;
    private final com.rvt.ticket.booking.AccountService accounts;
    public AppointmentController(AppointmentService service, TicketStrategyRegistry strategies, com.rvt.ticket.booking.AccountService accounts) { this.service = service; this.strategies = strategies; this.accounts = accounts; }

    @GetMapping("/lich-hen")
    String page(@RequestParam(defaultValue = "XE_KHACH") String type, @RequestParam(required = false) LocalDate date,
                @RequestParam(defaultValue = "") String code, Principal p, Model m) {
        if (!Set.of("XE_KHACH", "TAU_THUYEN").contains(type)) throw new IllegalArgumentException("Loại vé không hợp lệ.");
        LocalDate day = date == null ? LocalDate.now(AppointmentService.VN).plusDays(1) : date;
        String name = "", phone = "";
        try { var profile = accounts.profile(p.getName()); name = String.valueOf(profile.getOrDefault("HoTen", "")); phone = String.valueOf(profile.getOrDefault("SoDienThoai", "")); }
        catch (RuntimeException ignored) { /* tài khoản nhân viên: để trống */ }
        m.addAttribute("form", new AppointmentForm(type, name, phone, day, "", "", code));
        fill(m, type, day, p.getName());
        return "appointment";
    }

    @PostMapping("/lich-hen")
    String create(@Valid @ModelAttribute("form") AppointmentForm form, BindingResult errors, Principal p, Model m, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                var a = service.book(p.getName(), form);
                flash.addFlashAttribute("message", "Đã đặt lịch hẹn " + a.getCode() + ". Nhân viên sẽ xác nhận trong trang này; không gửi email hoặc SMS.");
                return "redirect:/lich-hen"; // Post/Redirect/Get: chống gửi trùng khi tải lại trang
            } catch (IllegalArgumentException | IllegalStateException e) {
                m.addAttribute("error", e.getMessage());
            } catch (DataAccessException e) {
                m.addAttribute("error", "Chưa lưu được lịch hẹn. Vui lòng kiểm tra thông tin và thử lại.");
            }
        } else {
            m.addAttribute("error", "Hãy kiểm tra họ tên, số điện thoại, ngày và khung giờ.");
            m.addAttribute("fieldErrors", InputErrors.collect(errors));
        }
        String type = Set.of("XE_KHACH", "TAU_THUYEN").contains(form.type()) ? form.type() : "XE_KHACH";
        fill(m, type, form.date() == null ? LocalDate.now(AppointmentService.VN).plusDays(1) : form.date(), p.getName());
        return "appointment";
    }

    @PostMapping("/lich-hen/{code}/huy")
    String cancel(@PathVariable String code, Principal p, RedirectAttributes flash) {
        try { service.cancelByOwner(p.getName(), code); flash.addFlashAttribute("message", "Đã hủy lịch hẹn " + code + "."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lich-hen";
    }

    private void fill(Model m, String type, LocalDate day, String username) {
        m.addAttribute("type", type);
        m.addAttribute("day", day);
        m.addAttribute("strategies", strategies.all());
        m.addAttribute("slots", service.slots(type, day));
        m.addAttribute("mine", service.mine(username));
        m.addAttribute("minDate", LocalDate.now(AppointmentService.VN));
    }

    // ---- Khu vực nhân viên điều hành / quản trị ----
    @GetMapping("/admin/lich-hen")
    String staffList(@RequestParam(defaultValue = "") String status, Model m) {
        m.addAttribute("status", status);
        m.addAttribute("appointments", service.forStaff(status));
        return "admin-appointments";
    }

    @PostMapping("/admin/lich-hen/{code}/{action}")
    String staffMove(@PathVariable String code, @PathVariable String action, Principal p, RedirectAttributes flash) {
        var target = switch (action) {
            case "xac-nhan" -> AppointmentState.CONFIRMED;
            case "hoan-thanh" -> AppointmentState.DONE;
            case "huy" -> AppointmentState.CANCELLED;
            default -> null;
        };
        if (target == null) { flash.addFlashAttribute("error", "Thao tác không hợp lệ."); return "redirect:/admin/lich-hen"; }
        try { service.staffMove(code, target, p.getName()); flash.addFlashAttribute("message", "Đã cập nhật lịch hẹn " + code + "."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/lich-hen";
    }
}
