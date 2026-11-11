package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import com.rvt.ticket.web.ViewData;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"ADMIN_PASSWORD=123456"}) @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class TripUiDataIntegrationTests {
    @Autowired TicketService tickets;
    @Autowired FleetService fleet;
    @Autowired AdminWorkspaceService workspace;
    @Autowired ReportService reports;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    String unique(){return UUID.randomUUID().toString().substring(0,8).toUpperCase();}
    TripForm form(){
        var f=new TripForm();f.setCode("QA-UI-"+unique());f.setOperator("RVT kiểm thử");
        f.setFrom("TP. Hồ Chí Minh");f.setTo("Đà Lạt");f.setLayout("BUS_SEAT");f.setRows(7);
        f.setPrice(BigDecimal.valueOf(320000));f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(20).withNano(0));f.setArrival(f.getDeparture().plusHours(6));return f;
    }
    int vehicle(String layout){String code="QA-UI-"+unique();return fleet.save(null,new VehicleForm(code,"Xe kiểm thử",code,"Đơn vị kiểm thử","XE_KHACH",true,layout),"QA");}
    String busLayout(){return fleet.form(((Number)fleet.all().stream().filter(v->"XE_KHACH".equals(v.get("LoaiVe"))&&((Number)v.get("SucChua")).intValue()==28).findFirst().orElseThrow().get("PhuongTienID")).intValue()).layout();}
    BookingForm booking(){var f=new BookingForm();f.setName("Khách kiểm thử");f.setPhone("0900000000");var p=new BookingForm.Passenger();p.setSeat("A01");p.setName("Hành khách kiểm thử");p.setLuggage(BigDecimal.ZERO);f.getPassengers().add(p);return f;}

    @Test void elapsedOrNearDepartureCannotBeOpenedWithoutWaitingForScheduler(){
        int id=TestTrips.open(tickets,db,form());tickets.changeSale(id,"DONG_BAN");
        for(int minutes:List.of(-120,0,20,30)){
            db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=?,DuKienDen=DATEADD(HOUR,8,SYSUTCDATETIME()) WHERE ChuyenDiID=?",Timestamp.valueOf(tickets.databaseNow().withNano(0).plusMinutes(minutes)),id);
            assertThrows(IllegalArgumentException.class,()->tickets.changeSale(id,"MO_BAN"));assertEquals("DONG_BAN",tickets.trip(id).get("TrangThai"));
        }
        db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(HOUR,2,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,8,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);
        assertDoesNotThrow(()->tickets.changeSale(id,"MO_BAN"));
    }
    @Test void inconsistentCapacityCannotSearchSuggestBookOrOpen(){
        var f=form();int id=TestTrips.open(tickets,db,f);db.update("UPDATE dbo.ChuyenDi SET TongSoCho=100 WHERE ChuyenDiID=?",id);
        assertFalse(tickets.configured(id));assertThrows(IllegalArgumentException.class,()->tickets.book(id,booking()));
        assertTrue(tickets.search("XE_KHACH",f.getFrom(),f.getTo(),f.getDeparture().toLocalDate()).stream().noneMatch(t->Integer.valueOf(id).equals(t.get("ChuyenDiID"))));
        assertTrue(tickets.nextDates("XE_KHACH",f.getFrom(),f.getTo(),f.getDeparture().toLocalDate(),1,"all").isEmpty());
        tickets.changeSale(id,"DONG_BAN");assertThrows(IllegalArgumentException.class,()->tickets.changeSale(id,"MO_BAN"));
    }
    @Test void incompleteLayoutCannotBeSoldAndPollReportsClosed() throws Exception {
        int id=TestTrips.open(tickets,db,form());db.update("DELETE FROM dbo.SoDoCho WHERE ChuyenDiID=? AND MaCho='A01'",id);
        assertFalse(tickets.configured(id));assertThrows(IllegalArgumentException.class,()->tickets.book(id,booking()));
        assertTrue(tickets.saleIssues(id).stream().anyMatch(i->i.contains("vị trí")));
        mvc.perform(get("/dat-ve/"+id+"/cho-trong").with(user("customer").roles("CUSTOMER"))).andExpect(jsonPath("$.open").value(false));
    }
    @Test void zeroFareOrOutOfRangeSeatPositionCannotBeSold(){
        int id=TestTrips.open(tickets,db,form());db.update("UPDATE dbo.ChuyenDi SET GiaCoBan=0 WHERE ChuyenDiID=?",id);
        assertFalse(tickets.configured(id));assertThrows(IllegalArgumentException.class,()->tickets.book(id,booking()));
        db.update("UPDATE dbo.ChuyenDi SET GiaCoBan=320000 WHERE ChuyenDiID=?",id);
        db.update("UPDATE dbo.SoDoCho SET Hang=31 WHERE ChuyenDiID=? AND MaCho='A01'",id);
        assertFalse(tickets.configured(id));tickets.changeSale(id,"DONG_BAN");assertThrows(IllegalArgumentException.class,()->tickets.changeSale(id,"MO_BAN"));
    }
    @Test void occupancyUsesRegisteredSeatsInsteadOfUnverifiedCapacity(){
        var f=form();int id=TestTrips.open(tickets,db,f);tickets.pay(tickets.book(id,booking()),"0900000000");
        db.update("UPDATE dbo.ChuyenDi SET TongSoCho=100 WHERE ChuyenDiID=?",id);
        @SuppressWarnings("unchecked") var summary=(Map<String,Object>)reports.report(f.getDeparture().toLocalDate(),f.getDeparture().toLocalDate(),"XE_KHACH").get("summary");
        assertEquals(28,summary.get("capacity"));assertEquals(1,summary.get("sold"));assertEquals(new BigDecimal("3.6"),summary.get("occupancy"));
    }
    @Test void boatReferenceTitleIsConsistentBetweenCustomerAndAdmin() throws Exception {
        var f=form();f.setCode("RVT-SEA-UI-"+unique());f.setOperator("RVT Sea");f.setLayout("BOAT");f.setFrom("Rạch Giá");f.setTo("Phú Quốc");
        int id=TestTrips.open(tickets,db,f);String code="RVT-T"+id;String ship=(String)ViewData.localTime(tickets.trip(id)).get("TenHienThi");
        assertEquals("Phương tiện kiểm thử",ship);
        mvc.perform(get("/chuyen-di").param("type","TAU_THUYEN").param("date",f.getDeparture().toLocalDate().toString())).andExpect(status().isOk()).andExpect(content().string(containsString(ship))).andExpect(content().string(containsString(code)));
        mvc.perform(get("/admin/chuyen-di").param("q",code).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString(ship))).andExpect(content().string(containsString(code)));
    }
    @Test void automaticClosureKeepsExistingTicketsOrdersPaymentsAndSeatRows(){
        int id=TestTrips.open(tickets,db,form());String code=tickets.book(id,booking());tickets.pay(code,"0900000000");
        var before=tickets.staffOrder(code);var seats=tickets.seats(id);
        db.update("UPDATE dbo.ChuyenDi SET TongSoCho=100 WHERE ChuyenDiID=?",id);
        tickets.closeInvalidTrips();assertEquals("DONG_BAN",tickets.trip(id).get("TrangThai"));
        var after=tickets.staffOrder(code);assertEquals(before.get("TrangThai"),after.get("TrangThai"));assertEquals(before.get("TongTien"),after.get("TongTien"));assertEquals(before.get("tickets"),after.get("tickets"));assertEquals(seats,tickets.seats(id));
        assertEquals("THANH_CONG",db.queryForObject("SELECT p.TrangThai FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=?",String.class,before.get("DonDatVeID")));
        assertEquals(0,tickets.closeInvalidTrips());
    }
    @Test void publicCodeWorksInAdminAndManifestWithoutChangingImportedKey() throws Exception {
        var f=form();f.setCode("RVT-DEMO-UI-"+unique());int id=TestTrips.open(tickets,db,f);String publicCode="RVT-B"+id;
        assertEquals(publicCode,ViewData.localTime(tickets.trip(id)).get("MaChuyenHienThi"));
        assertEquals(1,workspace.trips(publicCode,"","",null,null,1).total());assertEquals(1,workspace.trips(f.getCode(),"","",null,null,1).total());
        assertEquals(0,workspace.trips(publicCode+"%","","",null,null,1).total());
        mvc.perform(get("/admin/chuyen-di").param("q",publicCode).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString(publicCode)));
        tickets.book(id,booking());
        mvc.perform(get("/admin/chuyen-di/"+id+"/hanh-khach.csv").with(user("admin").roles("ADMIN"))).andExpect(content().string(containsString(publicCode))).andExpect(content().string(not(containsString(f.getCode()))));
        assertEquals(f.getCode(),tickets.trip(id).get("MaChuyen"));
    }
    @Test void newTripCannotReuseAnExistingPublicCode(){
        var f=form();f.setCode("RVT-DEMO-UI-"+unique());int id=TestTrips.open(tickets,db,f);
        var conflict=form();conflict.setCode("RVT-B"+id);assertThrows(IllegalArgumentException.class,()->tickets.createTrip(conflict));
    }
    @Test void unbookedTripCanBeConfiguredFromVehicleAndRemainsClosed(){
        var f=form();f.setRows(1);int id=tickets.createTrip(f);int v=vehicle(busLayout());
        tickets.assignVehicle(id,v,"QA");var trip=tickets.trip(id);
        assertEquals(v,trip.get("PhuongTienID"));assertEquals(28,trip.get("TongSoCho"));assertEquals(28,tickets.seats(id).size());assertEquals("DONG_BAN",trip.get("TrangThai"));assertTrue(tickets.configured(id));
        assertEquals("Đơn vị kiểm thử",trip.get("DonViVanChuyen"));tickets.changeSale(id,"MO_BAN");assertDoesNotThrow(()->tickets.book(id,booking()));
    }
    @Test void assignmentPreservesPublishedSeatExtras(){
        int id=tickets.createTrip(form());
        db.update("UPDATE dbo.ChoNgoi SET PhuThu=123000 WHERE ChuyenDiID=? AND MaCho='A01'",id);
        tickets.assignVehicle(id,vehicle(busLayout()),"QA");assertEquals(new BigDecimal("123000.00"),tickets.seats(id).stream().filter(s->"A01".equals(s.get("MaCho"))).findFirst().orElseThrow().get("PhuThu"));
    }
    @Test void bookedTripOnlyAcceptsIdenticalSeatPositionsAndNeverRewritesTicket(){
        String layout=busLayout();int v=vehicle(layout);var f=form();f.setVehicleId(v);int id=TestTrips.open(tickets,db,f);String code=tickets.book(id,booking());tickets.pay(code,"0900000000");tickets.changeSale(id,"DONG_BAN");
        var before=tickets.staffOrder(code);var seats=tickets.seats(id);
        int mismatched=vehicle(layout.replace("\"A01\"","\"C01\""));
        assertThrows(IllegalArgumentException.class,()->tickets.assignVehicle(id,mismatched,"QA"));assertEquals(v,tickets.trip(id).get("PhuongTienID"));assertEquals(seats,tickets.seats(id));
        int same=vehicle(layout);tickets.assignVehicle(id,same,"QA");assertEquals(same,tickets.trip(id).get("PhuongTienID"));assertEquals(before.get("tickets"),tickets.staffOrder(code).get("tickets"));assertEquals(seats,tickets.seats(id));
    }
    @Test void sameVehicleCannotBeAssignedToOverlappingTrips(){
        int v=vehicle(busLayout());var f=form();int first=tickets.createTrip(f);tickets.assignVehicle(first,v,"QA");
        var second=form();int id=tickets.createTrip(second);tickets.changeSale(id,"DONG_BAN");assertThrows(IllegalArgumentException.class,()->tickets.assignVehicle(id,v,"QA"));assertNull(tickets.trip(id).get("PhuongTienID"));
        db.update("UPDATE dbo.ChuyenDi SET NoiDi=?,NoiDen=?,KhoiHanh=?,DuKienDen=? WHERE ChuyenDiID=?",f.getTo(),f.getFrom(),Timestamp.valueOf(f.getArrival().plusMinutes(30).minusHours(7)),Timestamp.valueOf(f.getArrival().plusHours(6).minusHours(7)),id);
        assertDoesNotThrow(()->tickets.assignVehicle(id,v,"QA"));
    }
    @Test void assignmentRejectsOpenCancelledPastInactiveAndWrongTypeTrips(){
        int v=vehicle(busLayout());int id=TestTrips.open(tickets,db,form());assertThrows(IllegalArgumentException.class,()->tickets.assignVehicle(id,v,"QA"));
        tickets.changeSale(id,"DONG_BAN");db.update("UPDATE dbo.PhuongTien SET HoatDong=0 WHERE PhuongTienID=?",v);assertThrows(IllegalArgumentException.class,()->tickets.assignVehicle(id,v,"QA"));db.update("UPDATE dbo.PhuongTien SET HoatDong=1 WHERE PhuongTienID=?",v);
        int boat=((Number)fleet.all().stream().filter(p->"TAU_THUYEN".equals(p.get("LoaiVe"))).findFirst().orElseThrow().get("PhuongTienID")).intValue();assertThrows(IllegalArgumentException.class,()->tickets.assignVehicle(id,boat,"QA"));
        db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(MINUTE,-10,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,6,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);assertThrows(IllegalArgumentException.class,()->tickets.assignVehicle(id,v,"QA"));
        db.update("UPDATE dbo.ChuyenDi SET TrangThai='DA_HUY' WHERE ChuyenDiID=?",id);assertThrows(IllegalArgumentException.class,()->tickets.assignVehicle(id,v,"QA"));
    }
    @Test void adminReadinessAndAssignmentFormRenderAndEndpointSaves() throws Exception {
        int id=TestTrips.open(tickets,db,form());tickets.changeSale(id,"DONG_BAN");int v=vehicle(busLayout());
        mvc.perform(get("/admin/chuyen-di/"+id).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("Kiểm tra trước khi mở bán"))).andExpect(content().string(containsString("Phân công phương tiện")));
        mvc.perform(post("/admin/chuyen-di/"+id+"/phuong-tien").with(user("admin").roles("ADMIN")).with(csrf()).param("vehicleId",String.valueOf(v))).andExpect(redirectedUrl("/admin/chuyen-di/"+id+".html")).andExpect(flash().attributeExists("message"));
        assertEquals(v,tickets.trip(id).get("PhuongTienID"));
    }
    @Test void supersededSystemReferenceKeepsBookedPriceAndTicketHistory(){
        var f=form();f.setLayout("BOAT");f.setFrom("Vũng Tàu");f.setTo("Côn Đảo");f.setCode("RVT-DEMO-UI-"+unique()+"-8");int old=TestTrips.open(tickets,db,f);
        var b=booking();var passenger=b.getPassengers().getFirst();passenger.setBirthDate(java.time.LocalDate.of(1985,1,1));passenger.setDocument("012345678901");
        String order=tickets.book(old,b);tickets.pay(order,"0900000000");var before=tickets.staffOrder(order);
        f.setCode("RVT-SEA-UI-"+unique());f.setPrice(BigDecimal.valueOf(950000));int replacement=TestTrips.open(tickets,db,f);
        tickets.closeSupersededReferenceTrips();assertEquals("DONG_BAN",tickets.trip(old).get("TrangThai"));assertEquals("MO_BAN",tickets.trip(replacement).get("TrangThai"));
        assertEquals(before.get("TongTien"),tickets.staffOrder(order).get("TongTien"));assertEquals(before.get("tickets"),tickets.staffOrder(order).get("tickets"));assertEquals("DA_THANH_TOAN",tickets.staffOrder(order).get("TrangThai"));
        var results=tickets.search("TAU_THUYEN",f.getFrom(),f.getTo(),f.getDeparture().toLocalDate());assertTrue(results.stream().noneMatch(t->Integer.valueOf(old).equals(t.get("ChuyenDiID"))));assertTrue(results.stream().anyMatch(t->Integer.valueOf(replacement).equals(t.get("ChuyenDiID"))));
        assertEquals(0,tickets.closeSupersededReferenceTrips());
    }
    @Test void distinctSailingsAndManualTripCodesAreNotRetired(){
        var f=form();f.setLayout("BOAT");f.setFrom("Vũng Tàu");f.setTo("Côn Đảo");f.setCode("RVT-DEMO-UI-"+unique()+"-8");int old=TestTrips.open(tickets,db,f);
        var g=form();g.setLayout("BOAT");g.setFrom(f.getFrom());g.setTo(f.getTo());g.setCode("RVT-SEA-UI-"+unique());g.setDeparture(f.getDeparture().plusHours(1));g.setArrival(f.getArrival().plusHours(1));TestTrips.open(tickets,db,g);
        tickets.closeSupersededReferenceTrips();assertEquals("MO_BAN",tickets.trip(old).get("TrangThai"));
        f.setCode("MANUAL-"+unique());int manual=TestTrips.open(tickets,db,f);f.setCode("RVT-SEA-UI-"+unique());int exact=TestTrips.open(tickets,db,f);tickets.changeSale(exact,"DONG_BAN");
        tickets.closeSupersededReferenceTrips();assertEquals("MO_BAN",tickets.trip(manual).get("TrangThai"));assertEquals("MO_BAN",tickets.trip(old).get("TrangThai"));
    }
}
