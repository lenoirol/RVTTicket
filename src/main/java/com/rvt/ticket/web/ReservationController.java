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
    private final StopService stops;
    private final PromotionService promotions;
    private final ExchangeService exchanges;
    private final InvoiceService invoices;
    public ReservationController(TicketService service,AccountService accounts,WalletService wallet,
            com.rvt.ticket.payment.VnpayService vnpay,@org.springframework.beans.factory.annotation.Value("${rvt.payment.allow-demo:false}") boolean allowDemo,
            StopService stops,PromotionService promotions,ExchangeService exchanges,InvoiceService invoices) {
        this.service=service;this.accounts=accounts;this.wallet=wallet;this.vnpay=vnpay;this.allowDemo=allowDemo;
        this.stops=stops;this.promotions=promotions;this.exchanges=exchanges;this.invoices=invoices;
    }
    /** Điểm đón / trả cho giao diện, giờ đổi sang giờ Việt Nam. */
    static List<Map<String,Object>> stopView(List<StopService.Stop> list){
        return list.stream().map(s->{var m=new LinkedHashMap<String,Object>();m.put("id",s.id());m.put("name",s.name());m.put("address",s.address());m.put("transfer",s.transfer());
            m.put("time",java.sql.Timestamp.valueOf(s.time().toLocalDateTime().plusHours(7)));m.put("offset",s.offsetMinutes());return (Map<String,Object>)m;}).toList();
    }
    @InitBinder("booking")
    void bookingFields(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(8);
        binder.setAllowedFields("requestKey","name","phone","email","passengers[*].name","passengers[*].seat","passengers[*].luggage","passengers[*].birthDate","passengers[*].document","passengers[*].documentType","passengers[*].nationality","pickupId","dropoffId");
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
        m.addAttribute("maxFloor",seats.stream().map(s->s.get("Tang")).filter(Objects::nonNull).mapToInt(v->((Number)v).intValue()).max().orElse(1));
        m.addAttribute("pickups",stopView(stops.pickups(trip)));m.addAttribute("dropoffs",stopView(stops.dropoffs(trip)));
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
        var view=vietnamTime(order);
        @SuppressWarnings("unchecked") var tiers=(List<Map<String,Object>>)order.get("cancellationTiers");
        view.put("cancellationTiers",tiers.stream().map(ViewData::localTime).toList());
        m.addAttribute("order",view);
        m.addAttribute("promotions",owner&&"GIU_CHO".equals(order.get("TrangThai"))?promotions.active():List.of());
        m.addAttribute("invoice",invoices.forOrder(order.get("DonDatVeID")).orElse(null));
        return "order";
    }
    // ---- Mã khuyến mãi (áp dụng khi đơn đang giữ chỗ) ----
    @PostMapping("/don-ve/{code}/khuyen-mai")
    String applyPromotion(@PathVariable String code,@RequestParam(defaultValue="") String promo,HttpSession session,Principal principal,RedirectAttributes flash){
        try{var amount=promotions.apply(code,authorizedPhone(code,session),principal.getName(),promo);flash.addFlashAttribute("message","Đã áp dụng mã, giảm "+money(amount)+".");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/don-ve/"+code;
    }
    @PostMapping("/don-ve/{code}/khuyen-mai/go")
    String removePromotion(@PathVariable String code,HttpSession session,Principal principal,RedirectAttributes flash){
        try{promotions.remove(code,authorizedPhone(code,session),principal.getName());flash.addFlashAttribute("message","Đã gỡ mã khuyến mãi.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/don-ve/"+code;
    }
    // ---- Đổi vé sang chuyến khác cùng tuyến ----
    @GetMapping("/don-ve/{code}/doi-ve")
    String exchangeTrips(@PathVariable String code,@RequestParam(required=false) LocalDate date,HttpSession session,Principal principal,Model m){
        String phone=authorizedPhone(code,session);service.ownerPhone(code,principal.getName());
        var order=service.order(code,phone);
        if(!Boolean.TRUE.equals(order.get("CoTheDoiVe")))throw new IllegalArgumentException("Đơn không còn đổi được. Chỉ đổi đơn đã thanh toán, chưa đổi lần nào và trước hạn đổi vé.");
        var departure=((java.sql.Timestamp)order.get("KhoiHanh")).toLocalDateTime().plusHours(7).toLocalDate();
        if(date==null)date=departure;
        var today=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        if(date.isBefore(today)||date.isAfter(today.plusDays(60)))throw new IllegalArgumentException("Chọn ngày đi trong 60 ngày tới.");
        m.addAttribute("order",vietnamTime(order));m.addAttribute("date",date);m.addAttribute("today",today);
        m.addAttribute("trips",exchanges.candidates(code,phone,date).stream().map(this::vietnamTime).toList());
        return "exchange";
    }
    @GetMapping("/don-ve/{code}/doi-ve/{tripId}")
    String exchangeSeats(@PathVariable String code,@PathVariable int tripId,HttpSession session,Principal principal,Model m){
        String phone=authorizedPhone(code,session);service.ownerPhone(code,principal.getName());
        var order=service.order(code,phone);
        if(!Boolean.TRUE.equals(order.get("CoTheDoiVe")))throw new IllegalArgumentException("Đơn không còn đổi được.");
        service.requireConfigured(tripId);
        m.addAttribute("order",vietnamTime(order));
        var trip=service.trip(tripId);m.addAttribute("trip",vietnamTime(trip));
        var seats=service.seats(tripId);m.addAttribute("seats",seats);
        m.addAttribute("maxFloor",seats.stream().map(s->s.get("Tang")).filter(Objects::nonNull).mapToInt(v->((Number)v).intValue()).max().orElse(1));
        m.addAttribute("needed",((List<?>)order.get("tickets")).size());
        m.addAttribute("walletBalance",wallet.balance(principal.getName()));
        return "exchange-seats";
    }
    @PostMapping("/don-ve/{code}/doi-ve/{tripId}")
    String exchange(@PathVariable String code,@PathVariable int tripId,@RequestParam(name="seat",required=false) List<String> seats,HttpSession session,Principal principal,RedirectAttributes flash){
        try{
            String phone=authorizedPhone(code,session);
            String fresh=exchanges.exchange(code,phone,principal.getName(),tripId,seats==null?List.of():seats);
            session.setAttribute("order:"+fresh,phone);
            flash.addFlashAttribute("message","Đổi vé thành công. Đơn cũ "+code+" đã hủy và trả chỗ; tiền đã trả được chuyển sang đơn này.");
            return "redirect:/don-ve/"+fresh;
        }catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());return "redirect:/don-ve/"+code+"/doi-ve/"+tripId;}
    }
    // ---- Hóa đơn điện tử ----
    @GetMapping("/don-ve/{code}/hoa-don")
    String invoice(@PathVariable String code,HttpSession session,Principal principal,Model m){
        String phone=authorizedPhone(code,session);service.ownerPhone(code,principal.getName());
        var order=service.order(code,phone);
        m.addAttribute("order",vietnamTime(order));
        invoices.forOrder(order.get("DonDatVeID")).ifPresent(i->m.addAttribute("invoice",ViewData.localTime(i)));
        if(!m.containsAttribute("invoice")){var p=accounts.profile(principal.getName());m.addAttribute("buyerName",p.get("HoTen"));m.addAttribute("buyerEmail",p.get("Email"));}
        return "invoice";
    }
    @PostMapping("/don-ve/{code}/hoa-don")
    String issueInvoice(@PathVariable String code,@RequestParam(defaultValue="CA_NHAN") String kind,@RequestParam(defaultValue="") String name,@RequestParam(defaultValue="") String taxCode,
            @RequestParam(defaultValue="") String address,@RequestParam(defaultValue="") String email,HttpSession session,Principal principal,RedirectAttributes flash){
        try{invoices.issue(code,authorizedPhone(code,session),principal.getName(),new InvoiceService.Buyer(kind,name,taxCode,address,email));flash.addFlashAttribute("message","Đã xuất hóa đơn điện tử. Lưu lại mã tra cứu để xem hóa đơn sau này.");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/don-ve/"+code+"/hoa-don";
    }
    @GetMapping("/hoa-don") String invoiceLookup(){return "invoice-lookup";}
    @PostMapping("/hoa-don")
    String invoiceLookup(@RequestParam(defaultValue="") String lookup,Model m){
        try{m.addAttribute("invoice",ViewData.localTime(invoices.lookup(lookup)));}
        catch(IllegalArgumentException e){m.addAttribute("error",e.getMessage());}
        m.addAttribute("lookup",lookup);return "invoice-lookup";
    }
    private static String money(java.math.BigDecimal v){return String.format(java.util.Locale.ROOT,"%,d",v.longValue()).replace(',','.')+" đồng";}
    @PostMapping("/don-ve/{code}/huy")
    String cancel(@PathVariable String code,@RequestParam(defaultValue="") String reason,HttpSession session,Principal principal,RedirectAttributes flash) {
        try {
            if(reason==null||reason.isBlank())throw new IllegalArgumentException("Chọn lý do hủy trước khi xác nhận.");
            service.ownerPhone(code,principal.getName());service.cancel(code,authorizedPhone(code,session),reason);var after=service.order(code,authorizedPhone(code,session));
            flash.addFlashAttribute("message",after.get("TienHoan")==null?"Đơn đã hủy và trả chỗ.":"Đơn đã hủy. Hoàn "+money((java.math.BigDecimal)after.get("TienHoan"))+" vào số dư RVT"+(((java.math.BigDecimal)after.get("PhiHuy")).signum()>0?", phí hủy "+money((java.math.BigDecimal)after.get("PhiHuy"))+".":"."));
        }
        catch(IllegalArgumentException e) {flash.addFlashAttribute("error",e.getMessage());}
        return "redirect:/don-ve/"+code;
    }
    private Map<String,Object> vietnamTime(Map<String,Object> source) {
        return ViewData.localTime(source);
    }
}
