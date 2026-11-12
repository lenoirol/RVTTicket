package com.rvt.ticket.web;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

public final class ViewData {
    private ViewData() {}
    public static Map<String,Object> localTime(Map<String,Object> source) {
        var result=new LinkedHashMap<String,Object>(source);
        result.replaceAll((key,value)->value instanceof Timestamp t ? Timestamp.valueOf(t.toLocalDateTime().plusHours(7)) : value);
        // Mô tả cũ của dữ liệu mẫu ghi 20 phút; quy định hiện tại là có mặt tại bến trước 30 phút.
        if(result.get("MoTa") instanceof String text&&text.contains("trước giờ khởi hành 20 phút"))result.put("MoTa",text.replace("trước giờ khởi hành 20 phút","trước giờ khởi hành 30 phút"));
        result.put("TenHienThi",source.get("DonViVanChuyen"));
        result.put("TauThamKhao",false);
        result.put("MaChuyenHienThi",com.rvt.ticket.booking.TripIdentity.code(source));
        result.put("DaPhanCong",source.get("PhuongTienID")!=null);
        if("TAU_THUYEN".equals(source.get("LoaiVe"))){
            Object snapshot=source.get("TenPhuongTienSnapshot");
            String assigned=Objects.toString(snapshot!=null?snapshot:source.get("TenPhuongTien"),"");
            boolean physical=source.get("PhuongTienID")!=null&&!assigned.isBlank();
            if(physical)result.put("TenHienThi",assigned);
            else result.put("TenHienThi","Chờ phân công tàu / thuyền");
        }
        if(source.get("KhoiHanh") instanceof Timestamp start && source.get("DuKienDen") instanceof Timestamp end) {
            long minutes=Duration.between(start.toLocalDateTime(),end.toLocalDateTime()).toMinutes();
            result.put("ThoiLuong",(minutes/60)+" giờ"+(minutes%60==0?"":" "+minutes%60+" phút"));
            String state=(String)source.get(source.containsKey("MaDon")?"TrangThaiChuyen":"TrangThai");
            var now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
            String label="DA_HUY".equals(state)?"Chuyến đã hủy":source.get("PhuongTienID")==null?(!start.toLocalDateTime().isAfter(now)?"Đã qua giờ dự kiến · Chưa phân công phương tiện":"Chờ phân công phương tiện"):!end.toLocalDateTime().isAfter(now)?"Đã qua giờ đến dự kiến":!start.toLocalDateTime().isAfter(now)?"Đã đến giờ khởi hành dự kiến":"DONG_BAN".equals(state)?"Sắp khởi hành · Đóng bán":"Sắp khởi hành · Mở bán";
            result.put("HanhTrinhLabel",label);
        }
        return result;
    }
}
