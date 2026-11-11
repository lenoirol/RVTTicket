package com.rvt.ticket.booking;

import com.rvt.ticket.web.ViewData;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipReferencesTests {
    @Test void referencesMatchKnownRoutesInBothDirections(){
        assertEquals("Thăng Long",ShipReferences.route("Vũng Tàu","Côn Đảo").orElseThrow().name());
        assertEquals("Côn Đảo Express 36",ShipReferences.route("Côn Đảo","Trần Đề").orElseThrow().name());
        assertEquals("Phú Quốc Express 7",ShipReferences.route("Rạch Giá","Phú Quốc").orElseThrow().name());
        assertEquals("Trưng Trắc",ShipReferences.route("Phú Quý","Phan Thiết").orElseThrow().name());
        assertEquals("Phú Quốc Express 27",ShipReferences.route("Sa Kỳ","Lý Sơn").orElseThrow().name());
        assertEquals("Côn Đảo Express 36",ShipReferences.route("Cảng Trần Đề","Côn Đảo").orElseThrow().name());
        assertEquals("Phú Quốc Express 27",ShipReferences.route("Lý Sơn","Cảng Sa Kỳ").orElseThrow().name());
        assertTrue(ShipReferences.route("Hạ Long","Phú Quốc").isEmpty());assertTrue(ShipReferences.route(null,null).isEmpty());
    }
    @Test void routeReferencesDoNotPretendToBeAnActualVehicleAssignment(){
        var source=Map.<String,Object>of("LoaiVe","TAU_THUYEN","DonViVanChuyen","RVT Sea · mẫu đồ án","NoiDi","Rạch Giá","NoiDen","Phú Quốc");
        var reference=ViewData.localTime(source);assertEquals("Chờ phân công tàu / thuyền",reference.get("TenHienThi"));assertEquals(false,reference.get("TauThamKhao"));
        var assigned=new java.util.HashMap<>(source);assigned.put("PhuongTienID",1);assigned.put("TenPhuongTienSnapshot","Tàu được phân công");assertEquals("Tàu được phân công",ViewData.localTime(assigned).get("TenHienThi"));assertEquals(false,ViewData.localTime(assigned).get("TauThamKhao"));
        assigned=new java.util.HashMap<>(source);assigned.put("DonViVanChuyen","Hãng khác");assertEquals("Chờ phân công tàu / thuyền",ViewData.localTime(assigned).get("TenHienThi"));
    }
}
