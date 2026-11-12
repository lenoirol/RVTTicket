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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Bến xe có thật: địa chỉ, tìm bến gần nhất, giờ có mặt trước 30 phút. */
@SpringBootTest(properties={"ADMIN_PASSWORD=123456"}) @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class StationIntegrationTests {
    static final String PHONE="0900000000";
    @Autowired JdbcTemplate db;@Autowired StationService stations;@Autowired TicketService tickets;@Autowired StopService stops;@Autowired AccountService accounts;@Autowired MockMvc mvc;

    int trip(){var f=new TripForm();f.setCode("STA-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());f.setOperator("RVT kiểm thử");f.setFrom("TP. Hồ Chí Minh");f.setTo("Đà Lạt");f.setLayout("BUS_SEAT");f.setRows(2);
        f.setPickup("Bến xe Miền Đông");f.setDropoff("Bến xe Liên tỉnh Đà Lạt");f.setPrice(BigDecimal.valueOf(300000));f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(3).withNano(0));f.setArrival(f.getDeparture().plusHours(7));return TestTrips.open(tickets,db,f);}
    BookingForm form(){var f=new BookingForm();f.setName("Khách bến xe");f.setPhone(PHONE);var p=new BookingForm.Passenger();p.setName("Hành khách");p.setSeat("A01");p.setLuggage(BigDecimal.ZERO);f.getPassengers().add(p);return f;}

    @Test void realStationsAreLoadedWithAddressesAndCoordinates(){
        assertEquals(62,stations.all().size());
        var west=stations.byName("Bến xe Miền Tây").orElseThrow();assertTrue(((String)west.get("DiaChi")).contains("Kinh Dương Vương"));assertEquals("OSM",west.get("DoChinhXac"));
        assertTrue(((String)stations.byName("Bến xe Mỹ Đình").orElseThrow().get("DiaChi")).contains("Phạm Hùng"));
        assertTrue(stations.all().stream().filter(s->"GAN_DUNG".equals(s.get("DoChinhXac"))).allMatch(s->((String)s.get("DiaChi")).contains("cần kiểm tra")));
    }
    @Test void nearestStationIsByStraightLineDistance(){
        assertEquals("Bến xe Miền Tây",stations.nearest(10.7405,106.6190,3).getFirst().get("TenBen"));
        assertEquals("Bến xe Mỹ Đình",stations.nearest(21.0300,105.7800,1).getFirst().get("TenBen"));
        var near=stations.nearest(11.94,108.44,2);assertEquals("Bến xe Liên tỉnh Đà Lạt",near.getFirst().get("TenBen"));assertTrue((Double)near.getFirst().get("km")<3);
        assertEquals(1144,StationService.distanceKm(21.0285,105.8542,10.7769,106.7009),2); // Hà Nội – TP.HCM đường chim bay
    }
    @Test void stationPageAndApiAndArrivalTimes() throws Exception{
        mvc.perform(get("/api/ben-xe")).andExpect(status().isOk()).andExpect(content().string(containsString("Bến xe Mỹ Đình"))).andExpect(content().string(containsString("\"lat\"")));
        mvc.perform(get("/ben-xe")).andExpect(status().isOk()).andExpect(content().string(containsString("Vị trí của bạn"))).andExpect(content().string(containsString("OpenStreetMap")))
            .andExpect(content().string(containsString("Vị trí gần đúng")));
        int id=trip();var raw=tickets.trip(id);String date=((Timestamp)raw.get("KhoiHanh")).toLocalDateTime().plusHours(7).toLocalDate().toString();
        mvc.perform(get("/chuyen-di").param("type","XE_KHACH").param("from","TP. Hồ Chí Minh").param("to","Đà Lạt").param("date",date))
            .andExpect(content().string(containsString("Có mặt tại bến trước 30 phút"))).andExpect(content().string(containsString("Đinh Bộ Lĩnh")));
        String name="sta_"+UUID.randomUUID().toString().substring(0,8);accounts.register(new RegistrationForm(name,"CustomerTest_123","Khách bến xe",PHONE));
        String code=tickets.book(id,form(),name);var order=tickets.order(code,PHONE);
        assertEquals(((Timestamp)raw.get("KhoiHanh")).toLocalDateTime().minusMinutes(30),((Timestamp)order.get("CoMatLuc")).toLocalDateTime());
        assertTrue(((String)order.get("DiaChiDon")).contains("Đinh Bộ Lĩnh"));assertNotNull(order.get("BanDoDon"));
        var session=new MockHttpSession();session.setAttribute("order:"+code,PHONE);
        mvc.perform(get("/don-ve/"+code).session(session).with(user(name).roles("CUSTOMER","TRAVELER"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Giờ có mặt"))).andExpect(content().string(containsString("trước giờ khởi hành 30 phút"))).andExpect(content().string(containsString("Đinh Bộ Lĩnh")));
        var shuttle=stops.pickups(raw).stream().filter(s->s.id()!=null).findFirst().orElseThrow();
        var f=form();f.getPassengers().getFirst().setSeat("A02");f.setPickupId(shuttle.id());String second=tickets.book(id,f,name);var o2=tickets.order(second,PHONE);
        assertEquals(Boolean.TRUE,o2.get("DonTrungChuyen"));assertEquals(o2.get("GioDonDuKien"),o2.get("CoMatLuc"));assertTrue(((String)o2.get("DiaChiDon")).contains("văn phòng minh họa"));
        mvc.perform(get("/dat-ve/"+id).with(user(name).roles("CUSTOMER","TRAVELER"))).andExpect(content().string(containsString("Có mặt tại bến trước giờ khởi hành 30 phút"))).andExpect(content().string(containsString("Bến đi: Bến xe Miền Đông")));
        mvc.perform(get("/")).andExpect(content().string(containsString("/js/nearby")));
    }
}
