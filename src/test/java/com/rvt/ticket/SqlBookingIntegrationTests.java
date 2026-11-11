package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"ADMIN_PASSWORD=123456"}) @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class SqlBookingIntegrationTests {
    @Autowired TicketService service;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired org.springframework.security.core.userdetails.UserDetailsService users;
    @Autowired FleetService fleet;
    @Autowired PlaceService places;
    @Autowired QrService qr;
    @Autowired ReportService reports;
    private final Map<String,Integer> fixtures=new HashMap<>();
    int trip(String type) {
        // Test-owned trips are rolled back; tests no longer depend on the user's seeded sales status or calendar.
        return fixtures.computeIfAbsent(type,key->{
            var f=new TripForm();f.setCode("TEST-SQL-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());f.setOperator("RVT kiểm thử");
            boolean boat="TAU_THUYEN".equals(key);f.setLayout(boat?"BOAT":"BUS_SEAT");f.setRows(boat?10:7);f.setVipSeats(boat?8:0);f.setVipExtra(boat?BigDecimal.valueOf(100000):BigDecimal.ZERO);
            f.setFrom(boat?"Rạch Giá":"TP. Hồ Chí Minh");f.setTo(boat?"Phú Quốc":"Đà Lạt");f.setPrice(BigDecimal.valueOf(boat?350000:320000));
            f.setDeparture(service.databaseNow().plusHours(7).plusDays(3).withNano(0));f.setArrival(f.getDeparture().plusHours(boat?3:6));return TestTrips.open(service,db,f);
        });
    }
    BookingForm form(String... seats) {
        var f=new BookingForm();f.setName("Khách kiểm thử");f.setPhone("0900000000");f.setEmail("test@example.com");
        for(String seat:seats) {var p=new BookingForm.Passenger();p.setName("Hành khách "+seat);p.setSeat(seat);p.setDocument("012345678901");p.setBirthDate(java.time.LocalDate.of(1985,1,1));p.setLuggage(BigDecimal.valueOf(26));f.getPassengers().add(p);}
        return f;
    }
    String httpCustomer(){String name="http_"+UUID.randomUUID().toString().replace("-","").substring(0,12);accounts.register(new RegistrationForm(name,"Test_Customer_123","Khách HTTP","0900000001"));return name;}
    @Test void multiplePassengersPaymentAndRefund() throws Exception {
        int id=trip("XE_KHACH");String code=service.book(id,form("A01","A02"));
        var order=service.order(code,"0900000000");assertEquals(2,((List<?>)order.get("tickets")).size());
        assertThrows(IllegalArgumentException.class,()->service.changeSale(id,"DONG_BAN"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.vw_DangKyThieuChiTietLoaiVe",Integer.class));
        assertThrows(IllegalArgumentException.class,()->service.book(id,form("A01")));
        var session=new MockHttpSession();session.setAttribute("order:"+code,"0900000000");
        mvc.perform(get("/don-ve/"+code).session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(code)));
        service.pay(code,"0900000000");service.pay(code,"0900000000");
        service.changeSale(id,"DONG_BAN");service.changeSale(id,"MO_BAN");
        assertEquals("DA_THANH_TOAN",service.order(code,"0900000000").get("TrangThai"));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan t JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=t.DangKyID WHERE d.DonDatVeID=?",Integer.class,order.get("DonDatVeID")));
        service.cancel(code,"0900000000");assertEquals("DA_HUY",service.order(code,"0900000000").get("TrangThai"));
        assertDoesNotThrow(()->service.book(id,form("A01")));
    }
    @Test void boatDocumentAndLuggage() {
        int id=trip("TAU_THUYEN");var invalid=form("A01");invalid.getPassengers().getFirst().setDocument("123");
        assertThrows(IllegalArgumentException.class,()->service.book(id,invalid));
        String code=service.book(id,form("A01"));var order=service.order(code,"0900000000");
        assertEquals("GIU_CHO",order.get("TrangThai"));
        assertEquals(new BigDecimal("15000.00"),db.queryForObject("SELECT p.SoTien FROM dbo.ChiTietPhi p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=?",BigDecimal.class,order.get("DonDatVeID")));
        assertThrows(IllegalArgumentException.class,()->service.order(code,"0999999999"));
    }
    @Test void templatesSecurityAndHttpBooking() throws Exception {
        for(String url:List.of("/","/chuyen-di","/tra-cuu","/login","/chinh-sach","/tuyen-tau"))
            mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(get("/dat-ve/"+trip("TAU_THUYEN"))).andExpect(status().is3xxRedirection());
        mvc.perform(get("/dat-ve/"+trip("TAU_THUYEN")).with(user(httpCustomer()).roles("CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin/chuyen-di/moi").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/dat-ve/"+trip("XE_KHACH")).with(user(httpCustomer()).roles("CUSTOMER")).with(csrf()).param("name","Khách HTTP").param("phone","0900000001")
            .param("passengers[0].name","Hành khách HTTP").param("passengers[0].seat","A03").param("passengers[0].luggage","0"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/don-ve/RVT*"));
        mvc.perform(post("/dat-ve/"+trip("XE_KHACH"))).andExpect(status().isForbidden());
    }
    @Test void expiredOrdersReleaseSeats() {
        int id=trip("XE_KHACH");String code=service.book(id,form("A04"));var order=service.order(code,"0900000000");
        db.update("UPDATE dbo.DangKyVe SET NgayDangKy=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE DangKyID IN (SELECT DangKyID FROM dbo.DonDatVeChiTiet WHERE DonDatVeID=?)",order.get("DonDatVeID"));
        db.update("UPDATE dbo.DonDatVe SET NgayTao=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE DonDatVeID=?",order.get("DonDatVeID"));
        service.expireTrip(id);assertEquals("HET_HAN",service.order(code,"0900000000").get("TrangThai"));
        assertDoesNotThrow(()->service.book(id,form("A04")));
    }
    @Test void adminTripCreatesConsistentLayout() throws Exception {
        var f=new TripForm();f.setCode("TEST-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());
        f.setFrom("Điểm đi kiểm thử");f.setTo("Điểm đến kiểm thử");f.setOperator("RVT kiểm thử");f.setLayout("BUS_SLEEPER");
        f.setDeparture(service.databaseNow().plusHours(7).plusDays(3));f.setArrival(f.getDeparture().plusHours(5));f.setPrice(BigDecimal.valueOf(300000));f.setRows(6);
        int id=TestTrips.open(service,db,f);assertEquals(36,service.seats(id).size());
        assertEquals(18,service.seats(id).stream().filter(s->Integer.valueOf(2).equals(s.get("Tang"))).count());
        mvc.perform(get("/dat-ve/"+id).with(user(httpCustomer()).roles("CUSTOMER"))).andExpect(status().isOk());
    }
    @Test void extendedTemplatesAndRoleBoundaries() throws Exception {
        for(String url:List.of("/dang-ky","/nguon-anh","/chuyen-di?sort=price&places=2&period=morning"))
            mvc.perform(get(url)).andExpect(status().isOk());
        for(String url:List.of("/staff","/admin/chinh-sach","/admin/chuyen-di/"+trip("XE_KHACH")+"/thong-tin"))
            mvc.perform(get(url).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin").with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/staff").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/chinh-sach").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        String code=service.book(trip("TAU_THUYEN"),form("A05"));
        mvc.perform(get("/staff/don/"+code).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }
    @Test void customerRegistrationLoginAndPrivateHistory() throws Exception {
        String username="test_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String password="Testonly_10Characters";
        accounts.register(new RegistrationForm(username,password,"Khách tài khoản","0900000000"));
        String stored=users.loadUserByUsername(username).getPassword();
        assertNotEquals(password,stored);
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(password,stored));
        mvc.perform(post("/login").with(csrf()).param("username",username).param("password",password))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/workspace"))
            .andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated().withUsername(username));
        String code=service.book(trip("XE_KHACH"),form("A06"),username);
        String guestCode=service.book(trip("XE_KHACH"),form("A07"));
        assertEquals(1,accounts.history(username).size());
        assertEquals("0900000000",service.ownerPhone(code,username));
        assertThrows(IllegalArgumentException.class,()->service.ownerPhone(guestCode,username));
        assertThrows(IllegalArgumentException.class,()->service.ownerPhone(code,"someone_else"));
        mvc.perform(get("/tai-khoan").with(user(username).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(code)))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(guestCode))));
        assertThrows(IllegalArgumentException.class,()->accounts.register(new RegistrationForm("staff",password,"Khách","0900000000")));
    }
    @Test void operatorLoginUsesEncodedPasswords() throws Exception {
        String stored=users.loadUserByUsername("admin").getPassword();
        assertNotEquals("123456",stored);
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("123456",stored));
        mvc.perform(post("/login").with(csrf()).param("username","admin").param("password","123456"))
            .andExpect(redirectedUrl("/workspace")).andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated().withUsername("admin"));
        mvc.perform(post("/login").with(csrf()).param("username","staff").param("password","RvtTestOnly_Staff"))
            .andExpect(redirectedUrl("/login?error")).andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated());
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,()->users.loadUserByUsername("staff"));
        mvc.perform(get("/staff/kiem-ve").with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
    }
    @Test void configuredFeeAndTripInformationAreSnapshotted() throws Exception {
        int id=trip("XE_KHACH");
        service.updateInfo(id,"Điểm đón cũ","Điểm trả cũ","Tiện ích kiểm thử","Mô tả","admin");
        service.saveRule(new RuleForm("XE_KHACH",BigDecimal.valueOf(20),BigDecimal.valueOf(30),BigDecimal.valueOf(12000),24),"admin");
        String code=service.book(id,form("A08"));
        var before=service.order(code,"0900000000");
        assertEquals(new BigDecimal("72000.00"),before.get("TongPhi"));
        assertEquals(24,before.get("GioHanHuy"));
        service.saveRule(new RuleForm("XE_KHACH",BigDecimal.valueOf(10),BigDecimal.valueOf(30),BigDecimal.valueOf(15000),24),"admin");
        service.updateInfo(id,"Điểm đón mới","Điểm trả mới","","","admin");
        var after=service.order(code,"0900000000");
        assertEquals(before.get("TongTien"),after.get("TongTien"));
        assertEquals(24,after.get("GioHanHuy"));
        assertEquals("Điểm đón cũ",after.get("DiemDon"));
        assertEquals("Điểm đón mới",service.trip(id).get("DiemDon"));
        var tooHeavy=form("A09");tooHeavy.getPassengers().getFirst().setLuggage(BigDecimal.valueOf(31));
        assertThrows(IllegalArgumentException.class,()->service.book(id,tooHeavy));
        var f=new RuleForm("XE_KHACH",BigDecimal.valueOf(31),BigDecimal.valueOf(31),BigDecimal.ZERO,24);
        assertThrows(IllegalArgumentException.class,()->service.saveRule(f,"admin"));
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }
    @Test void checkinCannotResellUsedSeatsAndIsAudited() {
        int id=trip("XE_KHACH");String code=service.book(id,form("A10"));
        service.pay(code,"0900000000");
        int ticket=db.queryForObject("SELECT DangKyID FROM dbo.DangKyVe WHERE DangKyCode=?",Integer.class,code+"-1");
        assertThrows(IllegalArgumentException.class,()->service.staffAction(code,"checkin",ticket,"staff"));
        db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(HOUR,1,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);
        service.staffAction(code,"checkin",ticket,"staff");
        assertEquals("DA_SU_DUNG",db.queryForObject("SELECT TrangThai FROM dbo.DangKyVe WHERE DangKyID=?",String.class,ticket));
        assertEquals(1,service.seats(id).stream().filter(s->s.get("MaCho").equals("A10")).findFirst().orElseThrow().get("DaDat"));
        assertThrows(IllegalArgumentException.class,()->service.book(id,form("A10")));
        assertThrows(IllegalArgumentException.class,()->service.cancel(code,"0900000000"));
        assertThrows(IllegalArgumentException.class,()->service.staffAction(code,"checkin",ticket,"staff"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyThaoTac WHERE HanhDong='CHECKIN' AND DoiTuong=?",Integer.class,code+" / "+ticket));
    }
    TripForm newTripForm(){
        var f=new TripForm();f.setCode("TEST-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());
        f.setFrom("Hà Nội");f.setTo("Đà Lạt");f.setOperator("RVT kiểm thử");f.setLayout("BUS_SEAT");
        f.setDeparture(service.databaseNow().plusHours(7).plusDays(3));f.setArrival(f.getDeparture().plusHours(5));f.setPrice(BigDecimal.valueOf(320000));f.setRows(7);return f;
    }
    VehicleForm vehicleForm(String layout){
        String id=UUID.randomUUID().toString().substring(0,8).toUpperCase();
        return new VehicleForm("TEST-"+id,"Phương tiện riêng","TEST-"+id,"RVT kiểm thử","XE_KHACH",true,layout);
    }
    @Test void catalogueHasAllProvincesAndCanFilterWholeProvince() throws Exception {
        assertEquals(34,places.provinces().size());assertTrue(places.destinations().size()>=185);
        assertEquals("da lat",PlaceService.normalized("Đà Lạt"));
        int id=trip("XE_KHACH");var local=((java.sql.Timestamp)service.trip(id).get("KhoiHanh")).toLocalDateTime().plusHours(7).toLocalDate();
        assertTrue(service.search("XE_KHACH","","province:lam-dong",local).stream().anyMatch(t->t.get("NoiDen").equals("Đà Lạt")));
        assertEquals(List.of("Vũng Tàu"),places.searchNames("vung tau"));
        assertTrue(places.searchNames("Kiên Giang").contains("Rạch Giá"));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Cảng Hà Tiên")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("province:ha-noi")));
        int before=places.destinations().size();places.seed();assertEquals(before,places.destinations().size());
        places.add("lam-dong","Địa điểm kiểm thử "+UUID.randomUUID(),"admin");assertEquals(before+1,places.destinations().size());
    }
    @Test void fleetSnapshotAndOverlappingAssignment() throws Exception {
        String json="[{\"seat\":\"G01\",\"kind\":\"GHE_NGOI\",\"extra\":0,\"floor\":1,\"row\":1,\"column\":1},{\"seat\":\"G02\",\"kind\":\"GHE_NGOI\",\"extra\":20000,\"floor\":1,\"row\":1,\"column\":5}]";
        var v=vehicleForm(json);int vehicle=fleet.save(null,v,"admin");var f=newTripForm();f.setVehicleId(vehicle);
        int id=service.createTrip(f,"admin");assertEquals(2,service.seats(id).size());assertEquals(vehicle,service.trip(id).get("PhuongTienID"));
        assertEquals(5,service.seats(id).get(1).get("Cot"));assertEquals(v.name(),service.trip(id).get("TenPhuongTien"));
        var overlap=newTripForm();overlap.setVehicleId(vehicle);assertThrows(IllegalArgumentException.class,()->service.createTrip(overlap,"admin"));
        assertThrows(IllegalArgumentException.class,()->fleet.save(vehicle,v,"admin"));
        var later=newTripForm();later.setVehicleId(vehicle);later.setDeparture(f.getArrival());later.setArrival(later.getDeparture().plusHours(5));assertThrows(IllegalArgumentException.class,()->service.createTrip(later,"admin"));
        later.setDeparture(f.getArrival().plusMinutes(30));later.setArrival(later.getDeparture().plusHours(5));later.setFrom(f.getTo());later.setTo(f.getFrom());assertDoesNotThrow(()->service.createTrip(later,"admin"));
        mvc.perform(get("/dat-ve/"+id).with(user(httpCustomer()).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("custom-layout")));
    }
    @Test void fleetRejectsInvalidPositionsDuplicateCodesAndWrongKinds(){
        String seat="{\"seat\":\"G01\",\"kind\":\"GHE_NGOI\",\"extra\":0,\"floor\":1,\"row\":1,\"column\":1}";
        assertThrows(IllegalArgumentException.class,()->fleet.parse("["+seat+","+seat+"]","XE_KHACH"));
        assertThrows(IllegalArgumentException.class,()->fleet.parse("["+seat.replace("\"column\":1","\"column\":7")+"]","XE_KHACH"));
        assertThrows(IllegalArgumentException.class,()->fleet.parse("["+seat+"]","TAU_THUYEN"));
        assertThrows(IllegalArgumentException.class,()->fleet.parse("[]","XE_KHACH"));
        assertThrows(IllegalArgumentException.class,()->fleet.parse("not json","XE_KHACH"));
    }
    @Test void allNewAdminAndStaffPagesRenderAndAreProtected() throws Exception {
        for(String url:List.of("/admin/phuong-tien","/admin/phuong-tien/moi","/admin/phuong-tien/"+fleet.all().getFirst().get("PhuongTienID"),"/admin/dia-diem","/admin/thong-ke","/admin/thong-ke.csv?start=2026-10-01&end=2026-10-03")){
            mvc.perform(get(url).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
            mvc.perform(get(url).with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/staff/kiem-ve").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/staff/kiem-ve")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/thong-ke?start=2026-10-03&end=2026-10-01").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }
    @Test void qrEncodingDecodingOwnerAccessAndStaffUpload() throws Exception {
        int id=trip("XE_KHACH");String code=service.book(id,form("A11"));var order=service.order(code,"0900000000");
        int ticket=db.queryForObject("SELECT DangKyID FROM dbo.DangKyVe WHERE DangKyCode=?",Integer.class,code+"-1");
        String token=db.queryForObject("SELECT QrToken FROM dbo.DangKyVe WHERE DangKyID=?",String.class,ticket);byte[] png=qr.image(token);
        assertEquals(token,qr.decode(png));assertTrue(((String)qr.verify(token).get("message")).contains("chưa thanh toán"));
        var session=new MockHttpSession();session.setAttribute("order:"+code,"0900000000");
        mvc.perform(get("/don-ve/"+code+"/qr/"+ticket+".png")).andExpect(status().isForbidden());
        mvc.perform(get("/don-ve/"+code+"/qr/"+ticket+".png").session(session)).andExpect(status().isBadRequest());
        service.pay(code,"0900000000");
        mvc.perform(get("/don-ve/"+code+"/qr/"+ticket+".png").session(session)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/don-ve/"+code+"/qr/"+Integer.MAX_VALUE+".png").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/staff/kiem-ve?token="+token).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("verified-ticket")));
        var photo=new org.springframework.mock.web.MockMultipartFile("photo","qr.png","image/png",png);
        mvc.perform(multipart("/staff/kiem-ve/anh").file(photo).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/kiem-ve?token="+token));
        assertThrows(IllegalArgumentException.class,()->qr.token("https://malicious.example"));
        assertThrows(IllegalArgumentException.class,()->qr.decode(new byte[]{1,2,3}));
        assertThrows(IllegalArgumentException.class,()->qr.decode(new byte[4*1024*1024+1]));
    }
    @Test void allPassengersCheckedInMarksOrderUsedAndQrDetectsReuse() throws Exception {
        int id=trip("XE_KHACH");String code=service.book(id,form("A12","A13"));service.pay(code,"0900000000");
        db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(HOUR,1,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);
        var tickets=db.queryForList("SELECT DangKyID,QrToken FROM dbo.DangKyVe WHERE DangKyCode LIKE ? ORDER BY DangKyID",code+"-%");
        service.staffAction(code,"checkin",((Number)tickets.getFirst().get("DangKyID")).intValue(),"staff");
        assertEquals("DA_THANH_TOAN",service.order(code,"0900000000").get("TrangThai"));
        service.staffAction(code,"checkin",((Number)tickets.getLast().get("DangKyID")).intValue(),"staff");
        assertEquals("DA_SU_DUNG",service.order(code,"0900000000").get("TrangThai"));
        assertTrue(((String)qr.verify((String)tickets.getFirst().get("QrToken")).get("message")).contains("đã được sử dụng"));
        assertThrows(IllegalArgumentException.class,()->service.cancel(code,"0900000000"));
        assertDoesNotThrow(()->service.pay(code,"0900000000"));
        var session=new MockHttpSession();session.setAttribute("order:"+code,"0900000000");
        mvc.perform(get("/don-ve/"+code).session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("cancel-form"))));
    }
    @Test void tripCancellationRefundsEvenInsideCustomerCancellationWindow(){
        var f=newTripForm();f.setDeparture(service.databaseNow().plusHours(8));f.setArrival(f.getDeparture().plusHours(5));int id=TestTrips.open(service,db,f);
        String paid=service.book(id,form("A01")),held=service.book(id,form("A02"));service.pay(paid,"0900000000");
        assertThrows(IllegalArgumentException.class,()->service.cancel(paid,"0900000000"));
        service.cancelTrip(id,"Chuyến mẫu không khai thác","admin");service.cancelTrip(id,"Lặp thao tác","admin");
        assertEquals("DA_HUY",service.trip(id).get("TrangThai"));assertEquals("DA_HUY",service.order(paid,"0900000000").get("TrangThai"));assertEquals("DA_HUY",service.order(held,"0900000000").get("TrangThai"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID WHERE v.ChuyenDiID=? AND p.TrangThai='DA_HOAN' AND p.HoanLuc IS NOT NULL",Integer.class,id));
        assertThrows(IllegalArgumentException.class,()->service.book(id,form("A03")));
    }
    @Test void lifecycleMovesDepartedAndArrivedTrips(){
        int id=TestTrips.open(service,db,newTripForm());
        db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(HOUR,-1,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,1,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);
        service.refreshLifecycle();assertEquals("DA_KHOI_HANH",service.trip(id).get("TrangThai"));
        db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(HOUR,-2,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,-1,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);
        service.refreshLifecycle();assertEquals("HOAN_THANH",service.trip(id).get("TrangThai"));
    }
    @Test void reportUsesVietnameseDayAndActualRefundDate(){
        int id=trip("XE_KHACH");String code=service.book(id,form("A14"));service.pay(code,"0900000000");var o=service.order(code,"0900000000");BigDecimal amount=(BigDecimal)o.get("TongTien");
        var day=java.time.LocalDate.of(2031,2,2);
        db.update("UPDATE p SET ThoiGian=? FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=?",java.sql.Timestamp.valueOf(day.atStartOfDay().minusHours(7)),o.get("DonDatVeID"));
        service.cancel(code,"0900000000");
        db.update("UPDATE p SET HoanLuc=? FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=?",java.sql.Timestamp.valueOf(day.plusDays(1).atStartOfDay().minusHours(7)),o.get("DonDatVeID"));
        @SuppressWarnings("unchecked") var first=(Map<String,Object>)reports.report(day,day,"XE_KHACH").get("summary");
        @SuppressWarnings("unchecked") var next=(Map<String,Object>)reports.report(day.plusDays(1),day.plusDays(1),"XE_KHACH").get("summary");
        assertEquals(0,amount.compareTo((BigDecimal)first.get("gross")));assertEquals(1,first.get("tickets"));assertEquals(0,BigDecimal.ZERO.compareTo((BigDecimal)first.get("refund")));
        assertEquals(0,amount.negate().compareTo((BigDecimal)next.get("net")));
    }
    @Test void customerPrefillAndServerFieldErrors() throws Exception {
        String username="prefill_"+UUID.randomUUID().toString().substring(0,8);
        accounts.register(new RegistrationForm(username,"RvtTestOnly_Customer","Tên tự điền","0900000000"));
        mvc.perform(get("/dat-ve/"+trip("XE_KHACH")).with(user(username).roles("CUSTOMER"))).andExpect(status().isOk())
            .andExpect(model().attribute("booking",org.hamcrest.Matchers.hasProperty("name",org.hamcrest.Matchers.equalTo("Tên tự điền"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"name\" value=\"T&ecirc;n tự điền\"")));
        mvc.perform(post("/dat-ve/"+trip("XE_KHACH")).with(user(username).roles("CUSTOMER")).with(csrf()).param("name","").param("phone","123").param("passengers[0].name","").param("passengers[0].seat","A15").param("passengers[0].luggage","0"))
            .andExpect(status().isOk()).andExpect(model().attribute("fieldErrors",org.hamcrest.Matchers.hasEntry("name","Vui lòng điền thông tin này.")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("field-error-phone")));
    }
    @Test void httpVehicleCreationBindsTheReusableLayout() throws Exception {
        var v=vehicleForm("[{\"seat\":\"X01\",\"kind\":\"GHE_NGOI\",\"extra\":0,\"floor\":1,\"row\":1,\"column\":2}]");
        mvc.perform(post("/admin/phuong-tien/moi").with(user("admin").roles("ADMIN")).with(csrf()).param("code",v.code()).param("name",v.name()).param("registration",v.registration()).param("operator",v.operator()).param("type",v.type()).param("active","true").param("layout",v.layout()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/phuong-tien"));
        int id=db.queryForObject("SELECT PhuongTienID FROM dbo.PhuongTien WHERE MaPhuongTien=?",Integer.class,v.code());
        assertEquals(1,fleet.seats(id).size());assertEquals(2,fleet.seats(id).getFirst().get("Cot"));assertEquals(true,fleet.vehicle(id,false).get("HoatDong"));
        mvc.perform(post("/admin/phuong-tien/moi").with(user("staff").roles("STAFF")).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void expiredAndCanceledQrCannotBeUsed() throws Exception {
        int id=trip("XE_KHACH");String code=service.book(id,form("A16"));var o=service.order(code,"0900000000");
        String token=db.queryForObject("SELECT QrToken FROM dbo.DangKyVe WHERE DangKyCode=?",String.class,code+"-1");
        db.update("UPDATE dbo.DangKyVe SET NgayDangKy=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE DangKyCode=?",code+"-1");
        db.update("UPDATE dbo.DonDatVe SET NgayTao=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE DonDatVeID=?",o.get("DonDatVeID"));
        service.expireTrip(id);assertTrue(((String)qr.verify(token).get("message")).contains("hết hạn"));
        String canceled=service.book(id,form("A17"));String second=db.queryForObject("SELECT QrToken FROM dbo.DangKyVe WHERE DangKyCode=?",String.class,canceled+"-1");
        service.pay(canceled,"0900000000");service.cancel(canceled,"0900000000");assertTrue(((String)qr.verify(second).get("message")).contains("hủy"));
        mvc.perform(get("/staff/kiem-ve?token="+second).with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        assertThrows(IllegalArgumentException.class,()->qr.verify("0".repeat(64)));
    }
    @Test void boatVehicleCopiesVipAndPassengerFee(){
        var base=vehicleForm("[{\"seat\":\"VIP01\",\"kind\":\"VIP\",\"extra\":125000,\"floor\":2,\"row\":1,\"column\":4}]");
        var v=new VehicleForm(base.code(),base.name(),base.registration(),base.operator(),"TAU_THUYEN",true,base.layout());
        int vehicle=fleet.save(null,v,"admin");var f=newTripForm();f.setVehicleId(vehicle);f.setFrom("Rạch Giá");f.setTo("Phú Quốc");int id=TestTrips.open(service,db,f);
        assertEquals("TAU_THUYEN",service.trip(id).get("LoaiVe"));assertEquals("VIP",service.seats(id).getFirst().get("LoaiCho"));assertEquals(2,service.seats(id).getFirst().get("Tang"));
        String code=service.book(id,form("VIP01"));assertEquals(new BigDecimal("460000.00"),service.order(code,"0900000000").get("TongTien"));
    }
    @Test void reportCountsDistinctTicketsAndRejectsInvalidRanges(){
        int id=trip("XE_KHACH");String code=service.book(id,form("A18"));service.pay(code,"0900000000");var o=service.order(code,"0900000000");
        var day=java.time.LocalDate.of(2032,3,3);var date=java.sql.Timestamp.valueOf(day.atStartOfDay().minusHours(7));
        int ticket=db.queryForObject("SELECT DangKyID FROM dbo.DangKyVe WHERE DangKyCode=?",Integer.class,code+"-1");
        BigDecimal amount=(BigDecimal)o.get("TongTien");
        db.update("UPDATE dbo.ThanhToan SET ThoiGian=? WHERE DangKyID=?",date,ticket);
        db.update("INSERT dbo.ThanhToan(DangKyID,MaGiaoDich,SoTien,PhuongThuc,TrangThai,ThoiGian) VALUES(?,?,?,'CHUYEN_KHOAN','THANH_CONG',?)",ticket,"TEST-"+UUID.randomUUID(),amount,date);
        @SuppressWarnings("unchecked") var total=(Map<String,Object>)reports.report(day,day,"XE_KHACH").get("summary");
        assertEquals(1,total.get("tickets"));assertEquals(0,amount.multiply(BigDecimal.valueOf(2)).compareTo((BigDecimal)total.get("gross")));
        assertThrows(IllegalArgumentException.class,()->reports.report(day,day.plusDays(366),""));
        assertThrows(IllegalArgumentException.class,()->reports.report(day,day,"INVALID"));
    }
}
