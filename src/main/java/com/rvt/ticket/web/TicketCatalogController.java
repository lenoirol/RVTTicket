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

/** Tầng 2 / TicketCatalogController: hiển thị danh mục vé còn khả dụng theo tuyến, ngày giờ, loại phương tiện. */
@Controller
public class TicketCatalogController {
    private final TicketService service;
    public TicketCatalogController(TicketService service) {this.service=service;}
    @GetMapping("/")
    String home(Model m) {
        m.addAttribute("destinations",DestinationGuide.featured());
        // Trang chủ vẫn hiển thị khi truy vấn số liệu lỗi; các khối dữ liệu tự ẩn.
        try{m.addAttribute("stats",service.homeStats());m.addAttribute("departures",service.nextDepartures(8));}
        catch(DataAccessException e){org.slf4j.LoggerFactory.getLogger(TicketCatalogController.class).warn("Home data unavailable ({})",e.getClass().getSimpleName());}
        return "home";
    }
    @GetMapping("/chuyen-di")
    String search(@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to,@RequestParam(required=false) LocalDate date,@RequestParam(defaultValue="early") String sort,@RequestParam(defaultValue="1") int places,@RequestParam(defaultValue="all") String period,@RequestParam(required=false) LocalDate returnDate,@RequestParam(required=false) Integer outbound,Model m) {
        if(!Set.of("","XE_KHACH","TAU_THUYEN").contains(type)) throw new IllegalArgumentException("Loại vé không hợp lệ.");
        if(date==null) date=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        if(places<1||places>8||!Set.of("early","price","duration").contains(sort)||!Set.of("all","morning","afternoon","evening").contains(period))throw new IllegalArgumentException("Bộ lọc không hợp lệ.");
        if(returnDate!=null&&returnDate.isBefore(date)) throw new IllegalArgumentException("Ngày về không được trước ngày đi.");
        // Khứ hồi: bước 1 chọn chuyến đi (có returnDate); bước 2 tìm chiều ngược lại (có outbound) rồi đặt lần lượt hai đơn.
        final Map<String,Object> outboundTrip=outbound==null?null:vietnamTime(service.trip(outbound));
        if(outbound!=null) service.requireConfigured(outbound);
        m.addAttribute("returnDate",returnDate);m.addAttribute("outbound",outboundTrip);
        m.addAttribute("rtQuery",outbound!=null?"&outbound="+outbound:returnDate!=null?"&returnDate="+returnDate:"");
        m.addAttribute("sort",sort);m.addAttribute("places",places);m.addAttribute("period",period);
        m.addAttribute("type",type);m.addAttribute("from",from);m.addAttribute("to",to);m.addAttribute("date",date);
        m.addAttribute("nearbyDates",service.nextDates(type,from,to,date,places,period));
        var trips=service.search(type,from,to,date).stream().map(this::vietnamTime).filter(t->((Number)t.get("ConCho")).intValue()>=places).filter(t->outboundTrip==null||((java.sql.Timestamp)t.get("KhoiHanh")).after((java.sql.Timestamp)outboundTrip.get("DuKienDen"))).filter(t->{int hour=((java.sql.Timestamp)t.get("KhoiHanh")).toLocalDateTime().getHour();return period.equals("all")||(period.equals("morning")&&hour<12)||(period.equals("afternoon")&&hour>=12&&hour<18)||(period.equals("evening")&&hour>=18);}).sorted(sort.equals("price")?Comparator.comparing(t->(BigDecimal)t.get("GiaCoBan")):sort.equals("duration")?Comparator.comparing(t->java.time.Duration.between(((java.sql.Timestamp)t.get("KhoiHanh")).toLocalDateTime(),((java.sql.Timestamp)t.get("DuKienDen")).toLocalDateTime())):Comparator.comparing(t->(java.sql.Timestamp)t.get("KhoiHanh"))).toList();
        m.addAttribute("trips",trips);return "search";
    }
    @GetMapping("/dat-ve/{id}/cho-trong") @ResponseBody
    Map<String,Object> availability(@PathVariable int id){
        var trip=service.trip(id);
        boolean open="MO_BAN".equals(trip.get("TrangThai"))&&service.configured(id)&&((java.sql.Timestamp)trip.get("KhoiHanh")).toLocalDateTime().isAfter(service.databaseNow().plusMinutes(30));
        return Map.of("open",open,"seats",service.seats(id).stream().map(s->Map.of("seat",s.get("MaCho"),"unavailable",((Number)s.get("DaDat")).intValue()==1)).toList());
    }
    @GetMapping("/chinh-sach") String policy(Model m) {m.addAttribute("rules",service.allRules());return "policy";}
    @GetMapping("/login") String login() {return "login";}
    @GetMapping("/nguon-anh") String credits(Model m) {m.addAttribute("places",DestinationGuide.ALL);return "credits";}
    @GetMapping("/diem-den") String destinations(Model m) {m.addAttribute("regions",DestinationGuide.byRegion());m.addAttribute("total",DestinationGuide.ALL.size());return "destinations";}
    @GetMapping("/tuyen-tau") String seaRoutes() {return "sea-routes";}
    private Map<String,Object> vietnamTime(Map<String,Object> source) {
        return ViewData.localTime(source);
    }
}
