package com.rvt.ticket;
import com.rvt.ticket.booking.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Isolated real vehicle fixture. Tests obey the same assignment and opening guard as operators. */
final class TestTrips {
    static int open(TicketService tickets,JdbcTemplate db,TripForm form){
        int id=tickets.createTrip(form);
        if(form.getVehicleId()!=null){tickets.changeSale(id,"MO_BAN");return id;}
        String key="QA-FLEET-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        int vehicle=tickets.insert("INSERT dbo.PhuongTien(MaPhuongTien,TenPhuongTien,SoDangKy,DonViVanChuyen,LoaiVe,HoatDong) VALUES(?,?,?,?,?,1)",key,"Phương tiện kiểm thử",key,form.getOperator(),tickets.trip(id).get("LoaiVe"));
        db.update("INSERT dbo.ChoPhuongTien(PhuongTienID,MaCho,LoaiCho,PhuThu,Tang,Hang,Cot) SELECT ?,s.MaCho,s.LoaiCho,s.PhuThu,m.Tang,m.Hang,m.Cot FROM dbo.ChoNgoi s JOIN dbo.SoDoCho m ON m.ChuyenDiID=s.ChuyenDiID AND m.MaCho=s.MaCho WHERE s.ChuyenDiID=?",vehicle,id);
        tickets.assignVehicle(id,vehicle,"QA");tickets.changeSale(id,"MO_BAN");return id;
    }
    static void cleanupVehicle(JdbcTemplate db,Integer vehicle){
        if(vehicle!=null&&db.queryForObject("SELECT COUNT(*) FROM dbo.PhuongTien WHERE PhuongTienID=? AND MaPhuongTien LIKE 'QA-FLEET-%'",Integer.class,vehicle)==1){
            db.update("DELETE dbo.ChoPhuongTien WHERE PhuongTienID=?",vehicle);db.update("DELETE dbo.PhuongTien WHERE PhuongTienID=?",vehicle);
        }
    }
}
