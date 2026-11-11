package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import com.rvt.ticket.config.CustomerIdentity;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class ProductWorkflowIntegrationTests {
    @Autowired TicketService tickets;@Autowired AccountService accounts;@Autowired SupportService support;
    @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired UserDetailsService users;
    static final String PHONE="0900000000",PASSWORD="ProductTest_12345";
    String customer(){String name="qa_product_"+UUID.randomUUID().toString().substring(0,10);accounts.register(new RegistrationForm(name,PASSWORD,"Khách kiểm thử",PHONE));return name;}
    int trip(){var f=new TripForm();f.setCode("RVT-QA-"+UUID.randomUUID().toString().substring(0,12).toUpperCase());f.setOperator("RVT QA");f.setFrom("TP. Hồ Chí Minh");f.setTo("Đà Lạt");f.setPickup("Bến xe Miền Đông");f.setDropoff("Đà Lạt");f.setLayout("BUS_SEAT");f.setRows(4);f.setPrice(BigDecimal.valueOf(300000));f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(3));f.setArrival(f.getDeparture().plusHours(6));return TestTrips.open(tickets,db,f);}
    BookingForm booking(String seat){var f=new BookingForm();f.setName("Người đặt QA");f.setPhone(PHONE);f.setRequestKey(UUID.randomUUID().toString());var p=new BookingForm.Passenger();p.setName("Khách "+seat);p.setSeat(seat);p.setLuggage(BigDecimal.ZERO);f.getPassengers().add(p);return f;}
    @Test void repeatedRequestHasOneOrderAndOneNotification(){
        String name=customer();int id=trip();var form=booking("A01");String first=tickets.book(id,form,name);String repeat=tickets.book(id,form,name);assertEquals(first,repeat);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.DonDatVe WHERE YeuCauDat=?",Integer.class,form.getRequestKey()));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.ThongBaoKhach WHERE MaDon=?",Integer.class,first));
    }
    @Test void requestKeyCannotBeUsedByAnotherAccountOrTrip(){String name=customer(),other=customer();int id=trip();var form=booking("A01");tickets.book(id,form,name);assertThrows(IllegalArgumentException.class,()->tickets.book(id,form,other));assertThrows(IllegalArgumentException.class,()->tickets.book(trip(),form,name));}
    @Test void profileDoesNotRewriteBookedContact() throws Exception{
        String name=customer();String code=tickets.book(trip(),booking("A01"),name);accounts.updateProfile(name,"Tên mới","0911111111","new@example.com");
        assertEquals(PHONE,tickets.order(code,PHONE).get("SoDienThoai"));assertEquals("0911111111",accounts.profile(name).get("SoDienThoai"));
        mvc.perform(get("/dat-ve/"+trip()).with(user(name).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("new@example.com")));
    }
    @Test void invalidProfileAndPasswordAreRejected(){String name=customer();assertThrows(IllegalArgumentException.class,()->accounts.updateProfile(name,"A","bad","wrong"));assertThrows(IllegalArgumentException.class,()->accounts.changePassword(name,"wrong","NextPassword_123","NextPassword_123"));assertThrows(IllegalArgumentException.class,()->accounts.changePassword(name,PASSWORD,"short","short"));assertThrows(IllegalArgumentException.class,()->accounts.changePassword(name,PASSWORD,"NextPassword_123","different"));}
    @Test void passwordVersionEndsOldSessions() throws Exception{
        String name=customer();var old=users.loadUserByUsername(name);assertInstanceOf(CustomerIdentity.class,old);accounts.changePassword(name,PASSWORD,"NextPassword_123","NextPassword_123");
        var auth=UsernamePasswordAuthenticationToken.authenticated(old,null,old.getAuthorities());
        mvc.perform(get("/tai-khoan").with(authentication(auth))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login.html?changed"));
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("NextPassword_123",users.loadUserByUsername(name).getPassword()));
    }
    @Test void customerHistoryFiltersAndPaginationArePrivate(){
        String name=customer(),other=customer();int id=trip();for(int i=1;i<=11;i++)tickets.book(id,booking("A"+String.format("%02d",i)),name);
        assertEquals(11,accounts.history(name,"pending","",1).total());assertEquals(10,accounts.history(name,"all","",1).rows().size());assertEquals(1,accounts.history(name,"all","",2).rows().size());assertEquals(0,accounts.history(other,"all","",1).total());assertEquals(0,accounts.history(name,"upcoming","",1).total());
    }
    @Test void cancelledOrderHasTimestampReasonAndReceipt(){String name=customer();String code=tickets.book(trip(),booking("A01"),name);tickets.pay(code,PHONE,"DEMO",name);tickets.cancel(code,PHONE,"Thay đổi kế hoạch");var order=tickets.order(code,PHONE);assertEquals("Thay đổi kế hoạch",order.get("LyDoHuy"));assertNotNull(order.get("HuyLuc"));assertEquals(1,((List<?>)order.get("payments")).size());assertEquals(1,accounts.history(name,"cancelled","",1).total());}
    @Test void supportRequiresOrderOwnership(){String name=customer(),other=customer();String code=tickets.book(trip(),booking("A01"),name);assertThrows(IllegalArgumentException.class,()->support.create(other,code,"VE","Thông tin vé","Xin kiểm tra thông tin vé này"));int id=support.create(name,code,"VE","Thông tin vé","Xin kiểm tra thông tin vé này");assertEquals(code,support.detail(id,name).get("MaDon"));assertThrows(IllegalArgumentException.class,()->support.detail(id,other));assertEquals(0,support.list(other,"",1).total());}
    @Test void supportReplyCloseAndReopenPreserveMessages(){String name=customer();int id=support.create(name,"","HANH_TRINH","Điểm đón","Tôi muốn xác nhận vị trí điểm đón");support.reply(id,"admin",true,"Điểm đón ghi trong đơn vé. Vui lòng kiểm tra đúng bến.","DA_GIAI_QUYET");assertEquals("DA_GIAI_QUYET",support.detail(id,name).get("TrangThai"));support.reply(id,name,false,"Tôi cần hỏi thêm về giờ có mặt.","ignored");assertEquals("MOI",support.detail(id,name).get("TrangThai"));assertEquals(3,((List<?>)support.detail(id,name).get("messages")).size());}
    @Test void supportLimitAndValidation(){String name=customer();assertThrows(IllegalArgumentException.class,()->support.create(name,"","FAKE","Tiêu đề","Nội dung hợp lệ"));for(int i=0;i<5;i++)support.create(name,"","KHAC","Yêu cầu "+i,"Nội dung kiểm thử đủ độ dài");assertThrows(IllegalArgumentException.class,()->support.create(name,"","KHAC","Thêm yêu cầu","Nội dung kiểm thử đủ độ dài"));}
    @Test void supportNotificationsStayPrivateAndPointToConversation() throws Exception {
        String name=customer(),other=customer();int id=support.create(name,"","VE","Hỏi về vé","Tôi muốn kiểm tra thông tin vé");
        support.reply(id,"admin",true,"Nội dung trao đổi riêng không đưa vào pop-up.","DANG_XU_LY");
        mvc.perform(get("/tai-khoan/thong-bao").with(user(name).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(1)).andExpect(jsonPath("$.items[0].LienKet").value("/tai-khoan/ho-tro/"+id)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nội dung trao đổi riêng"))));
        mvc.perform(get("/tai-khoan/thong-bao").with(user(other).roles("CUSTOMER"))).andExpect(jsonPath("$.unread").value(0));
    }
    @Test void forgedPassengerIndexIsBoundedAndHasFriendlyError() throws Exception {
        String name=customer();mvc.perform(post("/dat-ve/"+trip()).with(user(name).roles("CUSTOMER")).with(csrf()).param("passengers[1000000].name","Không được cấp phát danh sách lớn")).andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("chưa đúng định dạng")));
    }
    @Test void errorPagesKeepStatusAndNeverEchoInternalException() throws Exception {
        for(int error:List.of(403,404,405,500))mvc.perform(get("/error").requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE,error).requestAttr(jakarta.servlet.RequestDispatcher.ERROR_MESSAGE,"secret-database-query")).andExpect(status().is(error)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-database-query"))));
    }
    @Test void readAllNotificationsRequiresCsrfAndIsAccountScoped() throws Exception {
        String name=customer(),other=customer();int a=support.create(name,"","VE","Hỏi về vé","Tôi muốn kiểm tra thông tin vé"),b=support.create(other,"","VE","Hỏi về vé","Tôi muốn kiểm tra thông tin vé");
        support.reply(a,"admin",true,"Đã kiểm tra yêu cầu của bạn.","DA_GIAI_QUYET");support.reply(b,"admin",true,"Đã kiểm tra yêu cầu của bạn.","DA_GIAI_QUYET");
        mvc.perform(post("/tai-khoan/thong-bao/doc-tat-ca").with(user(name).roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/tai-khoan/thong-bao/doc-tat-ca").with(user(name).roles("CUSTOMER")).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/tai-khoan/thong-bao").with(user(name).roles("CUSTOMER"))).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/tai-khoan/thong-bao").with(user(other).roles("CUSTOMER"))).andExpect(jsonPath("$.unread").value(1));
    }
    @Test void cancellationHttpRequiresReasonAndLeavesOrderUntouchedWhenMissing() throws Exception {
        String name=customer();String code=tickets.book(trip(),booking("A01"),name);tickets.pay(code,PHONE,"DEMO",name);
        mvc.perform(post("/don-ve/"+code+"/huy").with(user(name).roles("CUSTOMER")).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error","Chọn lý do hủy trước khi xác nhận."));
        assertEquals("DA_THANH_TOAN",tickets.order(code,PHONE).get("TrangThai"));
    }
    @Test void newTemplatesAndSupportRoles() throws Exception{
        String name=customer();for(String path:List.of("/huong-dan","/bao-mat"))mvc.perform(get(path)).andExpect(status().isOk());
        for(String path:List.of("/tai-khoan/ho-so","/tai-khoan/ho-tro","/tai-khoan?filter=pending"))mvc.perform(get(path).with(user(name).roles("CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(get("/admin/ho-tro").with(user(name).roles("CUSTOMER"))).andExpect(status().isForbidden());mvc.perform(get("/admin/ho-tro").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        int id=support.create(name,"","VE","Vấn đề vé","Tôi cần kiểm tra trạng thái của vé");
        mvc.perform(get("/tai-khoan/ho-tro/"+id).with(user(name).roles("CUSTOMER"))).andExpect(status().isOk());mvc.perform(get("/admin/ho-tro/"+id).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/tai-khoan/ho-tro/"+id).with(user(name).roles("CUSTOMER")).param("content","Nội dung bổ sung")).andExpect(status().isForbidden());
    }
    @Test void supportRendersUntrustedContentAsText() throws Exception{String name=customer();int id=support.create(name,"","KHAC","Kiểm tra nội dung","<script>alert('x')</script>");mvc.perform(get("/tai-khoan/ho-tro/"+id).with(user(name).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>alert"))));}
    @Test void availabilityHasNoPassengerDataAndHonoursClosing() throws Exception{String name=customer();int id=trip();mvc.perform(get("/dat-ve/"+id+"/cho-trong")).andExpect(status().is3xxRedirection());mvc.perform(get("/dat-ve/"+id+"/cho-trong").with(user(name).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.open").value(true)).andExpect(jsonPath("$.seats[0].seat").value("A01")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("HoTen"))));tickets.changeSale(id,"DONG_BAN");mvc.perform(get("/dat-ve/"+id+"/cho-trong").with(user(name).roles("CUSTOMER"))).andExpect(jsonPath("$.open").value(false));}
    @Test void nextDatesUseActualTripsAndMatchingFilters(){int id=trip();db.update("UPDATE dbo.ChuyenDi SET NoiDi=N'QA điểm riêng đi',NoiDen=N'QA điểm riêng đến' WHERE ChuyenDiID=?",id);LocalDate date=((java.sql.Timestamp)tickets.trip(id).get("KhoiHanh")).toLocalDateTime().plusHours(7).toLocalDate();assertFalse(tickets.nextDates("XE_KHACH","QA điểm riêng đi","QA điểm riêng đến",date,1,"all").isEmpty());assertTrue(tickets.nextDates("XE_KHACH","QA điểm riêng đi","Lý Sơn",date,1,"all").isEmpty());tickets.changeSale(id,"DONG_BAN");assertTrue(tickets.nextDates("XE_KHACH","QA điểm riêng đi","QA điểm riêng đến",date,1,"all").isEmpty());}
    @Test void calendarAndManifestArePrivateAndUsable() throws Exception{
        String name=customer(),other=customer();int id=trip();var f=booking("A01");f.getPassengers().getFirst().setName("=CMD()");String code=tickets.book(id,f,name);
        mvc.perform(get("/tai-khoan/don/"+code+"/lich.ics").with(user(name).roles("CUSTOMER"))).andExpect(status().isBadRequest());tickets.pay(code,PHONE,"DEMO",name);
        var result=mvc.perform(get("/tai-khoan/don/"+code+"/lich.ics").with(user(name).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String ics=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(ics.contains("BEGIN:VEVENT"));for(String line:ics.split("\r\n"))assertTrue(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=75);
        mvc.perform(get("/tai-khoan/don/"+code+"/lich.ics").with(user(other).roles("CUSTOMER"))).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/chuyen-di/"+id+"/hanh-khach.csv").with(user(name).roles("CUSTOMER"))).andExpect(status().isForbidden());mvc.perform(get("/admin/chuyen-di/"+id+"/hanh-khach.csv").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("'=CMD()")));
    }
}
