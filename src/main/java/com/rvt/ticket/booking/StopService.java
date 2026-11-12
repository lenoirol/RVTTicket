package com.rvt.ticket.booking;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Điểm đón / trả của chuyến xe khách. Lựa chọn đầu tiên luôn là bến mặc định của chuyến (id = null);
 * các lựa chọn sau là văn phòng trung chuyển ở thành phố đi (đón) hoặc thành phố đến (trả).
 */
@Service
public class StopService {
    public record Stop(Integer id,String name,String address,boolean transfer,Timestamp time,int offsetMinutes){}
    public record Choice(Stop pickup,Stop dropoff){}
    private final JdbcTemplate db;
    public StopService(JdbcTemplate db){this.db=db;}

    public List<Stop> pickups(Map<String,Object> trip){return options(trip,true);}
    public List<Stop> dropoffs(Map<String,Object> trip){return options(trip,false);}

    private List<Stop> options(Map<String,Object> trip,boolean pickup){
        Timestamp base=(Timestamp)trip.get(pickup?"KhoiHanh":"DuKienDen");
        String station=(String)trip.get(pickup?"DiemDon":"DiemTra");
        var list=new ArrayList<Stop>();
        list.add(new Stop(null,station,pickup?"Bến đi của chuyến":"Bến đến của chuyến",false,base,0));
        if(!"XE_KHACH".equals(trip.get("LoaiVe")))return list;
        String column=pickup?"PhutKhiDon":"PhutKhiTra";
        for(var r:db.queryForList("SELECT DiemID,TenDiem,DiaChi,LaTrungChuyen,"+column+" AS Phut FROM dbo.DiemDonTra WHERE HoatDong=1 AND "+column+" IS NOT NULL AND ThanhPho=? ORDER BY TenDiem",trip.get(pickup?"NoiDi":"NoiDen"))){
            int minutes=((Number)r.get("Phut")).intValue();
            list.add(new Stop(((Number)r.get("DiemID")).intValue(),(String)r.get("TenDiem"),(String)r.get("DiaChi"),Boolean.TRUE.equals(r.get("LaTrungChuyen")),
                Timestamp.valueOf(base.toLocalDateTime().plusMinutes(minutes)),minutes));
        }
        return list;
    }
    /** Kiểm tra id điểm thuộc đúng chuyến; null = bến mặc định. */
    public Choice choose(Map<String,Object> trip,Integer pickupId,Integer dropoffId){
        return new Choice(find(pickups(trip),pickupId,"đón"),find(dropoffs(trip),dropoffId,"trả"));
    }
    private Stop find(List<Stop> stops,Integer id,String label){
        return stops.stream().filter(s->Objects.equals(s.id(),id)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Điểm "+label+" không thuộc chuyến này. Hãy chọn lại."));
    }
    public static String label(Stop s){return s.transfer()?s.name()+" (trung chuyển)":s.name();}
}
