package com.rvt.ticket.web;

import com.rvt.ticket.booking.StationService;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Danh sách bến xe có thật và dữ liệu cho nút "Vị trí của bạn" (khoảng cách tính ngay trên trình duyệt). */
@Controller
public class StationController {
    private final StationService stations;
    public StationController(StationService stations){this.stations=stations;}

    @GetMapping("/ben-xe") String page(Model m){
        var groups=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var s:stations.all())groups.computeIfAbsent((String)s.get("ThanhPho"),k->new ArrayList<>()).add(view(s));
        m.addAttribute("groups",groups);m.addAttribute("total",stations.all().size());return "stations";
    }
    /** Tất cả bến (62 dòng) để trình duyệt tự tính bến gần nhất; vị trí của khách không gửi lên máy chủ. */
    @GetMapping("/api/ben-xe") @ResponseBody List<Map<String,Object>> all(){return stations.all().stream().map(StationController::view).toList();}
    static Map<String,Object> view(Map<String,Object> s){
        var m=new LinkedHashMap<String,Object>();
        m.put("ten",s.get("TenBen"));m.put("thanhPho",s.get("ThanhPho"));m.put("diaChi",s.get("DiaChi"));
        m.put("lat",((java.math.BigDecimal)s.get("ViDo")).doubleValue());m.put("lng",((java.math.BigDecimal)s.get("KinhDo")).doubleValue());
        m.put("doChinhXac",s.get("DoChinhXac"));m.put("banDo",StationService.mapUrl(s));return m;
    }
}
