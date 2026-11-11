package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
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
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"ADMIN_PASSWORD=123456"}) @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class AdminWorkspaceIntegrationTests {
    @Autowired JdbcTemplate db;
    @Autowired TicketService tickets;
    @Autowired AdminWorkspaceService workspace;
    @Autowired TripBatchService batches;
    @Autowired AdminIdentityService identity;
    @Autowired AccountService accounts;
    @Autowired FleetService fleet;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.security.core.userdetails.UserDetailsService users;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    String unique(){return UUID.randomUUID().toString().replace("-","").substring(0,10);}
    String customer(){var name="customer_"+unique();accounts.register(new RegistrationForm(name,"CustomerTest_123","Khách quản trị thử nghiệm","0900000000"));return name;}
    int customerId(String name){return db.queryForObject("SELECT TaiKhoanID FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",Integer.class,name);}
    TripForm tripForm(){
        var f=new TripForm();f.setCode("BATCH-"+unique().toUpperCase());f.setOperator("RVT mẫu");f.setFrom("TP. Hồ Chí Minh");f.setTo("Đà Lạt");
        f.setVehicleId(((Number)fleet.all().stream().filter(v->"XE_KHACH".equals(v.get("LoaiVe"))).findFirst().orElseThrow().get("PhuongTienID")).intValue());
        f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(15).withNano(0));f.setArrival(f.getDeparture().plusHours(5));f.setPrice(BigDecimal.valueOf(320000));return f;
    }
    BookingForm booking(String seat){var f=new BookingForm();f.setName("Khách thử nghiệm");f.setPhone("0900000000");var p=new BookingForm.Passenger();p.setSeat(seat);p.setName("Hành khách thử nghiệm");p.setLuggage(BigDecimal.ZERO);f.getPassengers().add(p);return f;}
    @Test void everyWorkspacePageRendersAndOnlyAdminCanAccess() throws Exception {
        int id=TestTrips.open(tickets,db,tripForm());String customer=customer();int customerId=customerId(customer);String code=tickets.book(id,booking("A01"),customer);
        for(String url:List.of("/admin","/admin/chuyen-di","/admin/chuyen-di/"+id,"/admin/chuyen-di/moi?copy="+id,"/admin/don-ve","/admin/don-ve/"+code,"/admin/khach-hang","/admin/khach-hang/"+customerId,"/admin/nhat-ky","/admin/tai-khoan","/admin/kiem-ve","/admin/vai-tro")) {
            mvc.perform(get(url).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("operator-sidebar")));
            mvc.perform(get(url).with(user(customer).roles("CUSTOMER"))).andExpect(status().isForbidden());
            mvc.perform(get(url).with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/").with(user("admin").roles("ADMIN"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("operator-sidebar"))));
    }
    @Test void lockRejectsNewLoginAndEndsExistingSessionWithoutDeletingOrders() throws Exception {
        String name=customer();int account=customerId(name),id=TestTrips.open(tickets,db,tripForm());String code=tickets.book(id,booking("A01"),name);
        assertThrows(IllegalArgumentException.class,()->workspace.lockCustomer(account,true,"","admin"));
        workspace.lockCustomer(account,true,"Kiểm thử khóa tài khoản","admin");assertFalse(users.loadUserByUsername(name).isEnabled());
        mvc.perform(post("/login").with(csrf()).param("username",name).param("password","CustomerTest_123"))
            .andExpect(redirectedUrl("/login.html?error")).andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated());
        var session=new MockHttpSession();mvc.perform(get("/tai-khoan").session(session).with(user(name).roles("CUSTOMER"))).andExpect(redirectedUrl("/login.html?locked"));assertTrue(session.isInvalid());
        assertEquals(1,accounts.history(name).size());assertEquals(code,accounts.history(name).getFirst().get("MaDon"));
        assertThrows(IllegalArgumentException.class,()->tickets.book(id,booking("A02"),name));
        workspace.lockCustomer(account,false,"","admin");assertTrue(users.loadUserByUsername(name).isEnabled());
        assertEquals("Kiểm thử khóa tài khoản",db.queryForObject("SELECT TOP 1 NoiDung FROM dbo.NhatKyThaoTac WHERE HanhDong='KHOA_KHACH' AND DoiTuong=? ORDER BY NhatKyID DESC",String.class,String.valueOf(account)));
        mvc.perform(post("/login").with(csrf()).param("username",name).param("password","CustomerTest_123")).andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated().withUsername(name));
    }
    @Test void adminPasswordIsEncodedAndSeedingDoesNotResetChangedPassword() {
        identity.changePassword("admin","123456","654321","654321");identity.seed();
        var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();String stored=users.loadUserByUsername("admin").getPassword();
        assertTrue(encoder.matches("654321",stored));assertFalse(encoder.matches("123456",stored));assertNotEquals("654321",stored);
        assertThrows(IllegalArgumentException.class,()->identity.changePassword("admin","bad","123456","123456"));
        assertThrows(IllegalArgumentException.class,()->identity.changePassword("admin","654321","123456","other"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap='admin'",Integer.class));
        assertNull(db.queryForObject("SELECT TOP 1 NoiDung FROM dbo.NhatKyThaoTac WHERE HanhDong='DOI_MAT_KHAU' ORDER BY NhatKyID DESC",String.class));
    }
    @Test void batchCreatesClosedTripsWithIndependentSeatSnapshots() {
        var f=tripForm();var preview=batches.preview(f,3);assertEquals(3,preview.size());assertTrue(preview.getFirst().getCode().length()<=30);
        var ids=batches.create(f,3,"admin");assertEquals(3,ids.size());
        for(int id:ids){assertEquals("DONG_BAN",tickets.trip(id).get("TrangThai"));assertEquals(fleet.seats(f.getVehicleId()).size(),tickets.seats(id).size());}
        assertEquals(preview.get(2).getDeparture().minusHours(7),((Timestamp)tickets.trip(ids.get(2)).get("KhoiHanh")).toLocalDateTime());
        assertThrows(IllegalArgumentException.class,()->batches.preview(f,32));assertThrows(IllegalArgumentException.class,()->batches.preview(f,0));
        f.setCode("X".repeat(22));assertThrows(IllegalArgumentException.class,()->batches.preview(f,2));
    }
    @Test void conflictOnLaterBatchDayRollsBackAllEarlierInserts() {
        var f=tripForm();String prefix=f.getCode();var tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->{var conflict=tripForm();conflict.setDeparture(f.getDeparture().plusDays(1));conflict.setArrival(f.getArrival().plusDays(1));tickets.createTrip(conflict);return batches.create(f,3,"admin");}));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi WHERE MaChuyen LIKE ?",Integer.class,prefix+"%"));
    }
    @Test void copyKeepsServiceInformationButUsesNewCodeAndFutureDate() {
        var f=tripForm();f.setPickup("Bến kiểm thử");f.setDropoff("Điểm trả kiểm thử");f.setDescription("Mô tả sao chép");int id=TestTrips.open(tickets,db,f);
        var copy=batches.duplicate(id);assertNotEquals(f.getCode(),copy.getCode());assertEquals(f.getVehicleId(),copy.getVehicleId());assertEquals(0,f.getPrice().compareTo(copy.getPrice()));
        assertEquals("Bến kiểm thử",copy.getPickup());assertEquals("Mô tả sao chép",copy.getDescription());assertTrue(copy.getDeparture().isAfter(tickets.databaseNow().plusHours(7)));
        assertEquals(Duration.ofHours(5),Duration.between(copy.getDeparture(),copy.getArrival()));
    }
    @Test void previewHasNoWritesAndReturnsEditableForm() throws Exception {
        var f=tripForm();int count=db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi",Integer.class);
        var request=post("/admin/chuyen-di/xem-lai").with(user("admin").roles("ADMIN")).with(csrf())
            .param("vehicleId",f.getVehicleId().toString()).param("code",f.getCode()).param("operator",f.getOperator()).param("from",f.getFrom()).param("to",f.getTo())
            .param("departure",f.getDeparture().toString()).param("arrival",f.getArrival().toString()).param("price","320000").param("days","3");
        mvc.perform(request).andExpect(status().isOk()).andExpect(view().name("trip-preview")).andExpect(content().string(org.hamcrest.Matchers.containsString("reviewed")));
        assertEquals(count,db.queryForObject("SELECT COUNT(*) FROM dbo.ChuyenDi",Integer.class));
        mvc.perform(post("/admin/chuyen-di/chinh-lai").with(user("admin").roles("ADMIN")).with(csrf()).param("code",f.getCode()).param("days","3"))
            .andExpect(view().name("trip-form")).andExpect(model().attribute("days",3));
    }
    @Test void adminRefundExceptionRequiresReasonAndCannotCancelUsedTicket() {
        var f=tripForm();f.setDeparture(tickets.databaseNow().plusHours(7).plusHours(3).withNano(0));f.setArrival(f.getDeparture().plusHours(5));int id=TestTrips.open(tickets,db,f);String code=tickets.book(id,booking("A01"));tickets.pay(code,"0900000000");
        assertThrows(IllegalArgumentException.class,()->tickets.cancel(code,"0900000000"));assertThrows(IllegalArgumentException.class,()->tickets.adminCancelOrder(code,"","admin"));
        tickets.adminCancelOrder(code,"Xử lý ngoại lệ trước khởi hành","admin");assertEquals("DA_HUY",tickets.staffOrder(code).get("TrangThai"));assertEquals("Xử lý ngoại lệ trước khởi hành",tickets.staffOrder(code).get("LyDoHuy"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=? AND p.TrangThai='DA_HOAN' AND p.HoanLuc IS NOT NULL",Integer.class,tickets.staffOrder(code).get("DonDatVeID")));
        String second=tickets.book(id,booking("A02"));tickets.pay(second,"0900000000");db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(MINUTE,45,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,5,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)tickets.staffOrder(second).get("tickets");tickets.staffAction(second,"checkin",((Number)rows.getFirst().get("DangKyID")).intValue(),"admin");
        assertThrows(IllegalArgumentException.class,()->tickets.adminCancelOrder(second,"Không hợp lệ","admin"));
    }
    @Test void paginationQueriesBeyondOneHundredOrdersAndKeepsFilterLinks() throws Exception {
        var f=tripForm();int id=TestTrips.open(tickets,db,f);String prefix="PAGE"+unique().toUpperCase();int customer=tickets.insert("INSERT dbo.KhachHang(HoTen,SoDienThoai) VALUES(N'Khách phân trang','0900000000')");
        for(int i=0;i<125;i++)db.update("INSERT dbo.DonDatVe(MaDon,KhachHangID,ChuyenDiID,TongTien,HanThanhToan) VALUES(?,?,?,0,DATEADD(MINUTE,15,SYSUTCDATETIME()))",prefix+i,customer,id);
        var page=workspace.orders(prefix,"","",null,null,null,7);assertEquals(125,page.total());assertEquals(7,page.pages());assertEquals(5,page.rows().size());
        mvc.perform(get("/admin/don-ve").param("q",prefix).param("page","2").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("q="+prefix))).andExpect(content().string(org.hamcrest.Matchers.containsString("page=3")));
        assertEquals(0,workspace.orders(prefix+"%","","",null,null,null,1).total());
    }
    @Test void dashboardUsesVietnamDateBoundariesAndAllCreatedOrders() {
        var f=tripForm();var date=f.getDeparture().toLocalDate();int id=TestTrips.open(tickets,db,f);String code=tickets.book(id,booking("A01"));
        var utc=Timestamp.valueOf(date.atStartOfDay().minusHours(7).plusMinutes(1));db.update("UPDATE dbo.DonDatVe SET NgayTao=?,HanThanhToan=? WHERE MaDon=?",utc,Timestamp.valueOf(date.atStartOfDay().minusHours(7).plusMinutes(16)),code);
        @SuppressWarnings("unchecked") var summary=(Map<String,Object>)workspace.dashboard(date).get("summary");assertTrue(((Number)summary.get("orders")).intValue()>=1);
        assertTrue(workspace.trips(f.getCode(),"","",date,date,1).total()>=1);assertEquals(0,workspace.trips(f.getCode(),"","",date.plusDays(1),date.plusDays(1),1).total());
        assertThrows(IllegalArgumentException.class,()->workspace.orders("","BAD","",null,null,null,1));assertThrows(IllegalArgumentException.class,()->workspace.trips("","","",date,date.minusDays(1),1));
    }
    @Test void administrativeMutationsRequireCsrfAndAdminRole() throws Exception {
        int id=customerId(customer());
        mvc.perform(post("/admin/khach-hang/"+id+"/khoa").with(user("admin").roles("ADMIN")).param("locked","true").param("reason","Test")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/khach-hang/"+id+"/khoa").with(user("staff").roles("STAFF")).with(csrf()).param("locked","true").param("reason","Test")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/khach-hang/"+id+"/khoa").with(user("admin").roles("ADMIN")).with(csrf()).param("locked","true").param("reason","Lý do HTTP"))
            .andExpect(redirectedUrl("/admin/khach-hang/"+id+".html"));assertEquals(Boolean.TRUE,workspace.customer(id).get("BiKhoa"));
        mvc.perform(get("/admin/don-ve?status=INVALID").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }
    @Test void finalHttpCreationRevalidatesAndCreatesOnlyAfterReview() throws Exception {
        var f=tripForm();
        var request=post("/admin/chuyen-di/moi").with(user("admin").roles("ADMIN")).with(csrf())
            .param("vehicleId",f.getVehicleId().toString()).param("code",f.getCode()).param("operator",f.getOperator()).param("from",f.getFrom()).param("to",f.getTo())
            .param("departure",f.getDeparture().toString()).param("arrival",f.getArrival().toString()).param("price","320000").param("days","1");
        mvc.perform(request).andExpect(view().name("trip-preview"));assertEquals(0,workspace.trips(f.getCode(),"","",null,null,1).total());
        mvc.perform(request.param("reviewed","true")).andExpect(redirectedUrl("/admin/chuyen-di.html"));
        var created=workspace.trips(f.getCode(),"","",null,null,1);assertEquals(1,created.total());
        int id=((Number)created.rows().getFirst().get("ChuyenDiID")).intValue();assertEquals("DONG_BAN",tickets.trip(id).get("TrangThai"));
        mvc.perform(post("/admin/chuyen-di/"+id).with(user("admin").roles("ADMIN")).with(csrf()).param("status","MO_BAN")).andExpect(redirectedUrl("/admin/chuyen-di.html"));
        assertEquals("MO_BAN",tickets.trip(id).get("TrangThai"));
    }
    @Test void orderCancelHttpRequiresReasonAndRecordsActualRefund() throws Exception {
        var f=tripForm();int id=TestTrips.open(tickets,db,f);String code=tickets.book(id,booking("A01"));tickets.pay(code,"0900000000");
        mvc.perform(post("/admin/don-ve/"+code).with(user("admin").roles("ADMIN")).with(csrf()).param("action","cancel"))
            .andExpect(redirectedUrl("/admin/don-ve/"+code+".html")).andExpect(flash().attributeExists("error"));assertEquals("DA_THANH_TOAN",tickets.staffOrder(code).get("TrangThai"));
        mvc.perform(post("/admin/don-ve/"+code).with(user("admin").roles("ADMIN")).with(csrf()).param("action","cancel").param("reason","Hủy theo yêu cầu đã đối chiếu"))
            .andExpect(redirectedUrl("/admin/don-ve/"+code+".html")).andExpect(flash().attributeExists("message"));assertEquals("DA_HUY",tickets.staffOrder(code).get("TrangThai"));
        mvc.perform(get("/admin/don-ve/"+code).with(user("admin").roles("ADMIN"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Lý do hủy:")));
    }
}
