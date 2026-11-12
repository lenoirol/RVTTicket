package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Khu vực con trong tỉnh/thành: tìm chuyến theo quận / huyện cũ, chọn sẵn điểm trung chuyển. */
@SpringBootTest(properties={"ADMIN_PASSWORD=123456"}) @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class LocalAreaIntegrationTests {
    @Autowired JdbcTemplate db;@Autowired PlaceService places;@Autowired TicketService tickets;@Autowired StopService stops;@Autowired AccountService accounts;@Autowired MockMvc mvc;

    int trip(){var f=new TripForm();f.setCode("AREA-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());f.setOperator("RVT kiểm thử");f.setFrom("TP. Hồ Chí Minh");f.setTo("Đà Lạt");f.setLayout("BUS_SEAT");f.setRows(2);
        f.setPrice(BigDecimal.valueOf(300000));f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(3).withNano(0));f.setArrival(f.getDeparture().plusHours(7));return TestTrips.open(tickets,db,f);}

    @Test void areasMapToTheCityThatHasTripsAndKeepTheirAliases(){
        var q7=places.area("Quận 7 (cũ)").orElseThrow();assertEquals("TP. Hồ Chí Minh",q7.get("PhucVuBoi"));assertEquals("ho-chi-minh",q7.get("MaTinh"));
        assertEquals("Đà Lạt",places.area("Đức Trọng (cũ)").orElseThrow().get("PhucVuBoi"));
        assertEquals(List.of("TP. Hồ Chí Minh"),places.searchNames("Quận 7 (cũ)"));
        assertEquals(List.of("TP. Hồ Chí Minh"),places.searchNames("nha be (cu)"));
        assertEquals(List.of("TP. Hồ Chí Minh"),places.searchNames("Phú Mỹ Hưng"));
        var lamDong=places.searchNames("province:lam-dong");
        assertTrue(lamDong.contains("Đà Lạt"));assertTrue(lamDong.contains("Bảo Lộc"));assertFalse(lamDong.contains("Đức Trọng (cũ)"));
        assertTrue(places.area("Đà Lạt").isEmpty());
    }
    @Test void eachAreaHasOneShuttleStopAndSeedingIsRepeatable() throws Exception{
        int areas=db.queryForObject("SELECT COUNT(*) FROM dbo.DiaDiem WHERE LaKhuVuc=1",Integer.class),linked=db.queryForObject("SELECT COUNT(*) FROM dbo.DiemDonTra WHERE MaDiem IS NOT NULL",Integer.class);
        assertTrue(areas>=50);assertEquals(areas,linked);
        places.seedAreas();
        assertEquals(areas,db.queryForObject("SELECT COUNT(*) FROM dbo.DiaDiem WHERE LaKhuVuc=1",Integer.class));assertEquals(linked,db.queryForObject("SELECT COUNT(*) FROM dbo.DiemDonTra WHERE MaDiem IS NOT NULL",Integer.class));
        // Văn phòng trung chuyển có từ V13 được gắn vào khu vực, không tạo trùng.
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.DiemDonTra WHERE TenDiem=N'Văn phòng RVT Quận 1'",Integer.class));
        assertNotNull(db.queryForObject("SELECT MaDiem FROM dbo.DiemDonTra WHERE TenDiem=N'Văn phòng RVT Quận 1'",String.class));
    }
    @Test void searchingFromAnAreaShowsCityTripsAndPreselectsTheShuttle() throws Exception{
        int id=trip();var raw=tickets.trip(id);String date=((Timestamp)raw.get("KhoiHanh")).toLocalDateTime().plusHours(7).toLocalDate().toString();
        String q7=(String)places.area("Quận 7 (cũ)").orElseThrow().get("MaDiem"),dt=(String)places.area("Đức Trọng (cũ)").orElseThrow().get("MaDiem");
        mvc.perform(get("/chuyen-di").param("type","XE_KHACH").param("from","Quận 7 (cũ)").param("to","Đức Trọng (cũ)").param("date",date))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Đi từ Quận 7 (cũ)"))).andExpect(content().string(containsString("Đến Đức Trọng (cũ)")))
            .andExpect(content().string(containsString(TripIdentity.code(raw)))).andExpect(content().string(containsString("pickupArea="+q7)));
        Integer pickup=stops.areaStop(raw,q7,true),dropoff=stops.areaStop(raw,dt,false);assertNotNull(pickup);assertNotNull(dropoff);
        assertNull(stops.areaStop(raw,dt,true)); // Đức Trọng không phục vụ chiều đi từ TP. Hồ Chí Minh
        String name="area_"+UUID.randomUUID().toString().substring(0,8);accounts.register(new RegistrationForm(name,"CustomerTest_123","Khách khu vực","0900000000"));
        mvc.perform(get("/dat-ve/"+id).param("pickupArea",q7).param("dropoffArea",dt).with(user(name).roles("CUSTOMER","TRAVELER"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\""+pickup+"\" selected=\"selected\""))).andExpect(content().string(containsString("value=\""+dropoff+"\" selected=\"selected\"")))
            .andExpect(content().string(containsString("Điểm đón Quận 7 (cũ) (trung chuyển)")));
    }
    @Test void placePickerListsAreasUnderTheirProvince() throws Exception{
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Quận 7 (cũ) · TP. Hồ Chí Minh · xe trung chuyển")))
            .andExpect(content().string(containsString("Đức Trọng (cũ) · Lâm Đồng · xe trung chuyển"))).andExpect(content().string(containsString("Toàn tỉnh/thành · Lâm Đồng")));
    }
}
