package com.rvt.ticket.booking;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final JdbcTemplate db;
    public NotificationService(JdbcTemplate db){this.db=db;}
    // Called inside the booking/payment transaction, so failed operations produce no notification.
    public void order(Map<String,Object> order,String event,String title,String text){
        if(order.get("TaiKhoanID")==null)return;
        String key=event+":"+order.get("DonDatVeID");
        db.update("INSERT dbo.ThongBaoKhach(TaiKhoanID,MaSuKien,MaDon,TieuDe,NoiDung) SELECT ?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.ThongBaoKhach WHERE MaSuKien=?)",order.get("TaiKhoanID"),key,order.get("MaDon"),title,text,key);
    }
    public Map<String,Object> inbox(String username){
        var rows=db.queryForList("SELECT TOP 30 n.* FROM dbo.ThongBaoKhach n JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=n.TaiKhoanID WHERE a.TenDangNhap=? ORDER BY n.ThongBaoID DESC",username);
        int unread=db.queryForObject("SELECT COUNT(*) FROM dbo.ThongBaoKhach n JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=n.TaiKhoanID WHERE a.TenDangNhap=? AND n.DaDoc=0",Integer.class,username);
        return Map.of("items",rows,"unread",unread,"account",username);
    }
    public void read(String username,long id){
        db.update("UPDATE n SET DaDoc=1 FROM dbo.ThongBaoKhach n JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=n.TaiKhoanID WHERE a.TenDangNhap=? AND n.ThongBaoID=?",username,id);
    }
    public void readAll(String username){
        db.update("UPDATE n SET DaDoc=1 FROM dbo.ThongBaoKhach n JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=n.TaiKhoanID WHERE a.TenDangNhap=? AND n.DaDoc=0",username);
    }
    public void support(Map<String,Object> request){
        db.update("INSERT dbo.ThongBaoKhach(TaiKhoanID,MaSuKien,MaDon,TieuDe,NoiDung,LienKet) VALUES(?,?,?,?,?,?)",request.get("TaiKhoanID"),"SUPPORT:"+UUID.randomUUID(),request.get("MaDon")==null?"":request.get("MaDon"),"Yêu cầu hỗ trợ có phản hồi","Quản trị đã trả lời "+request.get("MaYeuCau")+". Mở yêu cầu để đọc nội dung.","/tai-khoan/ho-tro/"+request.get("YeuCauID"));
    }
}
