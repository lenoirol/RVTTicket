package com.rvt.ticket.web;
import com.rvt.ticket.booking.*;
import java.util.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class PageDefaults {
    private final PlaceService places;private final FleetService fleet;
    public PageDefaults(PlaceService places,FleetService fleet){this.places=places;this.fleet=fleet;}
    @ModelAttribute("today") java.time.LocalDate today(){return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));}
    @ModelAttribute void defaults(Model m,jakarta.servlet.http.HttpServletRequest request,org.springframework.security.core.Authentication auth){
        m.addAttribute("fieldErrors",Map.of());
        String path=request.getRequestURI();boolean layout=auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")||a.getAuthority().equals("ROLE_OPERATOR"))&&(path.equals("/admin")||path.startsWith("/admin/")||path.equals("/staff")||path.startsWith("/staff/"));
        m.addAttribute("adminLayout",layout);m.addAttribute("adminPath",path);m.addAttribute("adminName",auth==null?"":auth.getName());
    }
    @ModelAttribute("placeGroups") List<Map<String,Object>> catalogue(jakarta.servlet.http.HttpServletRequest request){
        String path=request.getRequestURI();return path.equals("/")||path.equals("/chuyen-di")||Set.of("/admin/dia-diem","/admin/chuyen-di/moi","/admin/chuyen-di/xem-lai","/admin/chuyen-di/chinh-lai").contains(path)?places.groups():List.of();
    }
    @ModelAttribute("fleet") List<Map<String,Object>> fleet(jakarta.servlet.http.HttpServletRequest request){return Set.of("/admin/phuong-tien","/admin/chuyen-di/moi","/admin/chuyen-di/xem-lai","/admin/chuyen-di/chinh-lai").contains(request.getRequestURI())?fleet.all():List.of();}
    @ModelAttribute("seaRoutes") List<SeaRouteCatalog.Route> seaRoutes(){return SeaRouteCatalog.routes();}
}
