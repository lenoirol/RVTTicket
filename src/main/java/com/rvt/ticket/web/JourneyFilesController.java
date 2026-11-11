package com.rvt.ticket.web;

import com.rvt.ticket.booking.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class JourneyFilesController {
    private final TicketService tickets;private final AdminWorkspaceService workspace;
    public JourneyFilesController(TicketService tickets,AdminWorkspaceService workspace){this.tickets=tickets;this.workspace=workspace;}
    @GetMapping("/tai-khoan/don/{code}/lich.ics") @ResponseBody
    ResponseEntity<byte[]> calendar(@PathVariable String code,Principal p){
        var order=tickets.order(code,tickets.ownerPhone(code,p.getName()));
        if(!Set.of("DA_THANH_TOAN","DA_SU_DUNG").contains(order.get("TrangThai")))throw new IllegalArgumentException("Chỉ lưu lịch cho đơn đã xác nhận.");
        var format=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        String start=((Timestamp)order.get("KhoiHanh")).toLocalDateTime().format(format),end=((Timestamp)order.get("DuKienDen")).toLocalDateTime().format(format);
        String content="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//RVT Ticket//Journey//VI\r\nCALSCALE:GREGORIAN\r\nBEGIN:VEVENT\r\nUID:"+code+"@rvt-ticket.local\r\nDTSTAMP:"+LocalDateTime.now(ZoneOffset.UTC).format(format)+"\r\nDTSTART:"+start+"\r\nDTEND:"+end+"\r\nSUMMARY:"+ics(order.get("NoiDi")+" → "+order.get("NoiDen"))+"\r\nLOCATION:"+ics((String)order.get("DiemDon"))+"\r\nDESCRIPTION:"+ics("Kiểm tra trạng thái vé tại RVT trước ngày đi. Đến bến xe trước 30 phút / cảng tàu trước 45 phút. Lịch này không tự cập nhật khi chuyến bị hủy.")+"\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        // Fold at UTF-8 boundaries: calendar clients accept Vietnamese without splitting a code point.
        return file(fold(content),"text","calendar","rvt-journey.ics");
    }
    @GetMapping("/admin/chuyen-di/{id}/hanh-khach.csv") @ResponseBody
    ResponseEntity<byte[]> manifest(@PathVariable int id){
        var trip=workspace.tripDetail(id);var csv=new StringBuilder("\uFEFFMã chuyến,Khởi hành (giờ Việt Nam),Chỗ,Họ tên,Mã vé,Mã đơn,Trạng thái\r\n");
        @SuppressWarnings("unchecked") var passengers=(List<Map<String,Object>>)trip.get("passengers");
        String departure=((Timestamp)trip.get("KhoiHanh")).toLocalDateTime().plusHours(7).format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
        for(var guest:passengers)csv.append(String.join(",",List.of(csvCell(TripIdentity.code(trip)),csvCell(departure),csvCell(guest.get("MaCho")),csvCell(guest.get("TenHanhKhach")),csvCell(guest.get("DangKyCode")),csvCell(guest.get("MaDon")),csvCell(guest.get("TrangThai"))))).append("\r\n");
        return file(csv.toString(),"text","csv","rvt-passengers-"+id+".csv");
    }
    public static String csvCell(Object value){
        String text=value==null?"":value.toString().replace('\r',' ').replace('\n',' ').replace('\t',' ');
        if(text.stripLeading().matches("^[=+@-].*"))text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
    static String ics(String text){return text.replace("\\","\\\\").replace("\r","").replace("\n","\\n").replace(",","\\,").replace(";","\\;");}
    static String fold(String content){
        var output=new StringBuilder();
        for(String line:content.split("\r\n")){int bytes=0;for(int codePoint:line.codePoints().toArray()){
            String c=new String(Character.toChars(codePoint));int size=c.getBytes(StandardCharsets.UTF_8).length;
            if(bytes+size>74){output.append("\r\n ");bytes=1;}output.append(c);bytes+=size;
        }output.append("\r\n");}return output.toString();
    }
    private ResponseEntity<byte[]> file(String content,String type,String subtype,String name){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").cacheControl(CacheControl.noStore()).contentType(new MediaType(type,subtype,StandardCharsets.UTF_8)).body(content.getBytes(StandardCharsets.UTF_8));}
}
