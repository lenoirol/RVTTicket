package com.rvt.ticket.web;
import com.rvt.ticket.booking.ReportService;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ReportController {
    private final ReportService reports;public ReportController(ReportService reports){this.reports=reports;}
    @GetMapping("/admin/thong-ke")
    String report(@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,@RequestParam(defaultValue="") String type,Model m){
        var today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));if(start==null)start=today.withDayOfMonth(1);if(end==null)end=today;
        m.addAttribute("start",start);m.addAttribute("end",end);m.addAttribute("type",type);m.addAttribute("report",reports.report(start,end,type));return "report";
    }
    @GetMapping("/admin/thong-ke.csv")
    @ResponseBody ResponseEntity<byte[]> export(@RequestParam LocalDate start,@RequestParam LocalDate end,@RequestParam(defaultValue="") String type){
        var report=reports.report(start,end,type);var csv=new StringBuilder("\uFEFFNgày,Vé thanh toán,Giá trị ghi nhận,Giá trị đã hoàn,Chênh lệch\r\n");
        @SuppressWarnings("unchecked") var daily=(Collection<Map<String,Object>>)report.get("daily");
        for(var row:daily)csv.append(row.get("label")).append(',').append(row.get("tickets")).append(',').append(row.get("gross")).append(',').append(row.get("refund")).append(',').append(row.get("net")).append("\r\n");
        csv.append("\r\nPhương thức,Giá trị ghi nhận,Giá trị đã hoàn,Chênh lệch\r\n");
        @SuppressWarnings("unchecked") var methods=(Collection<Map<String,Object>>)report.get("byMethod");
        for(var row:methods)csv.append(row.get("label")).append(',').append(row.get("gross")).append(',').append(row.get("refund")).append(',').append(row.get("net")).append("\r\n");
        @SuppressWarnings("unchecked") var money=(Map<String,Object>)report.get("money");
        csv.append("\r\nChỉ tiêu,Giá trị\r\nVNPay đã xác minh trong kỳ,").append(money.get("vnpayVerified"))
            .append("\r\nHoàn ngoại lệ VNPay vào RVT trong kỳ,").append(money.get("vnpayResolvedRvt"))
            .append("\r\nCộng số dư RVT trong kỳ,").append(money.get("walletCredits"))
            .append("\r\nThanh toán bằng RVT trong kỳ,").append(money.get("walletDebits"))
            .append("\r\nSố dư khách hàng toàn hệ thống hiện tại,").append(money.get("walletOutstanding"))
            .append("\r\nKhoản hoàn chờ chủ đơn nhận hiện tại,").append(money.get("unclaimedRefunds"))
            .append("\r\nNgoại lệ VNPay chưa xử lý hiện tại,").append(money.get("exceptions")).append("\r\n");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"rvt-report-"+start+"-"+end+".csv\"").contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
