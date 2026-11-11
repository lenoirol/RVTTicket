package com.rvt.ticket;
import com.rvt.ticket.booking.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class OperationalIntegrityIntegrationTests {
    @Autowired TicketService tickets;@Autowired AccountService accounts;@Autowired AdminIdentityService admins;
    @Autowired LegacyOrderService legacy;@Autowired WalletService wallet;@Autowired FleetService fleet;
    @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired UserDetailsService users;
    static final String PHONE="0900000000",PASSWORD="ReviewOnly_12345";
    String suffix(){return UUID.randomUUID().toString().replace("-","").substring(0,10);}
    String customer(){String n="qa_review_"+suffix();accounts.register(new RegistrationForm(n,PASSWORD,"Khách kiểm thử",PHONE));return n;}
    TripForm form(boolean boat){var f=new TripForm();f.setCode("REVIEW-"+suffix().toUpperCase());f.setOperator("RVT QA");f.setFrom(boat?"Rạch Giá":"TP. Hồ Chí Minh");f.setTo(boat?"Phú Quốc":"Đà Lạt");f.setLayout(boat?"BOAT":"BUS_SEAT");f.setRows(1);f.setPrice(BigDecimal.valueOf(315000));f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(3));f.setArrival(f.getDeparture().plusHours(3));return f;}
    int trip(boolean boat){return TestTrips.open(tickets,db,form(boat));}
    BookingForm family(String record){var f=new BookingForm();f.setName("Khách kiểm thử");f.setPhone(PHONE);var a=new BookingForm.Passenger();a.setSeat("A01");a.setName("Người lớn QA");a.setBirthDate(LocalDate.of(1985,5,10));a.setDocumentType("CCCD");a.setDocument("012345678901");var c=new BookingForm.Passenger();c.setSeat("A02");c.setName("Trẻ em QA");c.setBirthDate(LocalDate.of(2020,5,10));c.setDocumentType("KHAI_SINH");c.setDocument(record);f.getPassengers().add(a);f.getPassengers().add(c);return f;}
    @Test void applicationAcceptedShortBirthRecordMustSave(){assertDoesNotThrow(()->tickets.book(trip(true),family("KS123"),customer()));}
    @Test void birthRecordMustRoundTripUnicode(){String code=tickets.book(trip(true),family("ĐK123456"),customer());assertEquals("ĐK123456",db.queryForObject("SELECT t.SoGiayTo FROM dbo.VeTauThuyen t JOIN dbo.DangKyVe v ON v.DangKyID=t.DangKyID WHERE v.DangKyCode=?",String.class,code+"-2"));}
    @Test void oneCharacterBirthRecordAndPunctuationRoundTrip(){for(String record:List.of("1","ĐK-12/3.4")){String code=tickets.book(trip(true),family(record),customer());assertEquals(record,db.queryForObject("SELECT t.SoGiayTo FROM dbo.VeTauThuyen t JOIN dbo.DangKyVe v ON v.DangKyID=t.DangKyID WHERE v.DangKyCode=?",String.class,code+"-2"));}}
    @Test void citizenCardStillNeedsExactlyTwelveDigits(){var f=family("KS123");f.getPassengers().getFirst().setDocument("12345678901");assertThrows(IllegalArgumentException.class,()->tickets.book(trip(true),f,customer()));}
    @Test void changedAdminPasswordMustRevokeOldIdentity() throws Exception {
        String n="qa_admin_review_"+suffix();db.update("INSERT dbo.TaiKhoanQuanTri(TenDangNhap,MatKhau,HoTen) VALUES(?,?,?)",n,new BCryptPasswordEncoder().encode(PASSWORD),"QA");
        var old=users.loadUserByUsername(n);admins.changePassword(n,PASSWORD,"ReviewNext_12345","ReviewNext_12345");
        var s=new MockHttpSession();mvc.perform(get("/admin").session(s).with(authentication(UsernamePasswordAuthenticationToken.authenticated(old,null,old.getAuthorities())))).andExpect(redirectedUrl("/login?changed"));assertTrue(s.isInvalid());
        var fresh=users.loadUserByUsername(n);mvc.perform(get("/admin").with(authentication(UsernamePasswordAuthenticationToken.authenticated(fresh,null,fresh.getAuthorities())))).andExpect(status().isOk());
    }
    int legacyTicket(int id){int k=tickets.insert("INSERT dbo.KhachHang(HoTen,SoDienThoai) VALUES(?,?)","QA",PHONE);var now=tickets.databaseNow();int v=tickets.insert("INSERT dbo.DangKyVe(DangKyCode,KhachHangID,ChuyenDiID,LoaiVe,TenHanhKhach,MaCho,NgayDangKy,HanThanhToan,GiaVe) VALUES(?,?,?,'XE_KHACH',?,'A01',?,?,?)","REVIEW-LEG-"+suffix(),k,id,"QA",Timestamp.valueOf(now),Timestamp.valueOf(now.plusMinutes(15)),BigDecimal.valueOf(315000));db.update("INSERT dbo.VeXeKhach(DangKyID,DiemDon,DiemTra) VALUES(?,?,?)",v,"TP. Hồ Chí Minh","Đà Lạt");db.update("UPDATE dbo.DangKyVe SET TrangThai='DA_THANH_TOAN' WHERE DangKyID=?",v);db.update("INSERT dbo.ThanhToan(DangKyID,MaGiaoDich,SoTien,PhuongThuc,TrangThai) VALUES(?,?,?,'TIEN_MAT','THANH_CONG')",v,"REVIEW-PAY-"+suffix(),BigDecimal.valueOf(315000));return v;}
    @Test void legacyRefundRequiresAMoneyRecordAndClaimsOnlyOnce(){
        int id=trip(false),v=legacyTicket(id);String user=customer();tickets.cancelTrip(id,"QA review rollback","QA");
        assertEquals("DA_HOAN",db.queryForObject("SELECT TrangThai FROM dbo.ThanhToan WHERE DangKyID=?",String.class,v));
        String code=db.queryForObject("SELECT o.MaDon FROM dbo.DonDatVe o JOIN dbo.DonDatVeChiTiet d ON d.DonDatVeID=o.DonDatVeID WHERE d.DangKyID=?",String.class,v);
        assertEquals(new BigDecimal("315000.00"),db.queryForObject("SELECT h.SoTien FROM dbo.HoanRVTChoNhan h JOIN dbo.DonDatVe o ON o.DonDatVeID=h.DonDatVeID WHERE o.MaDon=?",BigDecimal.class,code));
        wallet.claimLegacy(code,PHONE,user);wallet.claimLegacy(code,PHONE,user);assertEquals(new BigDecimal("315000.00"),wallet.balance(user));assertEquals(1,wallet.history(user,1).size());
    }
    @Test void importingLegacyDoesNotAlterReceiptFareSeatOrInferAccount(){int id=trip(false),v=legacyTicket(id);var before=db.queryForMap("SELECT * FROM dbo.DangKyVe WHERE DangKyID=?",v);assertEquals(1,legacy.importTrip(id));assertEquals(0,legacy.importTrip(id));assertEquals(before,db.queryForMap("SELECT * FROM dbo.DangKyVe WHERE DangKyID=?",v));assertNull(db.queryForObject("SELECT o.TaiKhoanID FROM dbo.DonDatVe o JOIN dbo.DonDatVeChiTiet d ON d.DonDatVeID=o.DonDatVeID WHERE d.DangKyID=?",Integer.class,v));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.HoanRVTChoNhan h JOIN dbo.DonDatVe o ON o.DonDatVeID=h.DonDatVeID WHERE o.ChuyenDiID=?",Integer.class,id));}
    @Test void loginMustContinueChosenTrip() throws Exception {String n=customer();int id=trip(false);var s=new MockHttpSession();mvc.perform(get("/dat-ve/"+id).session(s)).andExpect(status().is3xxRedirection());mvc.perform(post("/login").session(s).with(csrf()).param("username",n).param("password",PASSWORD)).andExpect(redirectedUrl("/dat-ve/"+id));}
    @Test void registrationPreservesChosenTripThroughProfile() throws Exception {
        int id=trip(false);var s=new MockHttpSession();mvc.perform(get("/dat-ve/"+id).session(s));String n="qa_review_"+suffix();
        mvc.perform(post("/dang-ky").session(s).with(csrf()).param("username",n).param("password",PASSWORD).param("name","Khách kiểm thử").param("phone",PHONE)).andExpect(redirectedUrl("/tai-khoan/ho-so"));
        assertEquals("/dat-ve/"+id,s.getAttribute("bookingContinue"));mvc.perform(get("/tai-khoan/ho-so").session(s)).andExpect(content().string(containsString("tiếp tục đặt chuyến"))).andExpect(content().string(containsString("/dat-ve/"+id)));
    }
    @Test void savedPostAndExternalTargetCannotRunAfterLogin() throws Exception {
        String n=customer();var s=new MockHttpSession();mvc.perform(post("/don-ve/FAKE/huy").session(s).with(csrf()));mvc.perform(post("/login").session(s).with(csrf()).param("username",n).param("password",PASSWORD).param("target","https://evil.invalid")).andExpect(redirectedUrl("/workspace"));
    }
    @Test void unassignedTripCannotOpenSearchOrBook(){var f=form(false);int id=tickets.createTrip(f);assertEquals("DONG_BAN",tickets.trip(id).get("TrangThai"));assertFalse(tickets.configured(id));assertThrows(IllegalArgumentException.class,()->tickets.changeSale(id,"MO_BAN"));var b=family("KS123");assertThrows(IllegalArgumentException.class,()->tickets.book(id,b,customer()));assertTrue(tickets.search("XE_KHACH",f.getFrom(),f.getTo(),f.getDeparture().toLocalDate()).stream().noneMatch(t->Integer.valueOf(id).equals(t.get("ChuyenDiID"))));}
    @Test void removingAssignmentClosesSalesWithoutTouchingPaidTickets(){int id=trip(false);var b=family("KS123");String code=tickets.book(id,b,customer());tickets.pay(code,PHONE);var before=tickets.staffOrder(code);db.update("UPDATE dbo.ChuyenDi SET PhuongTienID=NULL WHERE ChuyenDiID=?",id);tickets.closeInvalidTrips();assertEquals("DONG_BAN",tickets.trip(id).get("TrangThai"));assertEquals(before.get("TongTien"),tickets.staffOrder(code).get("TongTien"));assertEquals(before.get("tickets"),tickets.staffOrder(code).get("tickets"));}
    @Test void futureTripCanOpenOnlyAfterAssignedAndReady(){int id=tickets.createTrip(form(false));assertTrue(tickets.saleIssues(id).stream().anyMatch(x->x.contains("phân công")));int ready=trip(false);assertTrue(tickets.configured(ready));}
    @Test void assignmentMustMatchTheActualVehicleSeatCoordinates(){int id=trip(false);db.update("UPDATE dbo.SoDoCho SET Hang=Hang+10 WHERE ChuyenDiID=?",id);assertFalse(tickets.configured(id));assertThrows(IllegalArgumentException.class,()->tickets.changeSale(id,"MO_BAN"));tickets.closeInvalidTrips();assertEquals("DONG_BAN",tickets.trip(id).get("TrangThai"));}
    @Test void elapsedUnassignedDraftIsNotPretendedToHaveOperated(){int id=tickets.createTrip(form(false));db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(HOUR,-2,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,-1,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);tickets.refreshLifecycle();assertEquals("DONG_BAN",tickets.trip(id).get("TrangThai"));assertTrue(((String)com.rvt.ticket.web.ViewData.localTime(tickets.trip(id)).get("HanhTrinhLabel")).contains("Chưa phân công"));}
    @Test void oldAutomaticUnassignedStatusHasAnHonestAdminLabel() throws Exception {int id=tickets.createTrip(form(false));db.update("UPDATE dbo.ChuyenDi SET TrangThai='DA_KHOI_HANH',KhoiHanh=DATEADD(HOUR,-1,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,1,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);mvc.perform(get("/admin/chuyen-di/"+id).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("Quá giờ · chưa phân công"))).andExpect(content().string(not(containsString(">Đang hành trình<"))));}
    @Test void databaseClockAndDefaultsUseUtc(){long difference=Math.abs(db.queryForObject("SELECT DATEDIFF(SECOND,SYSUTCDATETIME(),?)",Long.class,Timestamp.valueOf(tickets.databaseNow())));assertTrue(difference<=2);assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys.default_constraints WHERE LOWER(definition) LIKE '%sysdatetime()%' AND LOWER(definition) NOT LIKE '%sysutcdatetime()%'",Integer.class));}
    @Test void unassignedDraftDoesNotInflateFleetOccupancy(){var f=form(false);int id=tickets.createTrip(f);var report=new ReportService(db);var day=f.getDeparture().toLocalDate();@SuppressWarnings("unchecked") var before=(Map<String,Object>)report.report(day,day,"XE_KHACH").get("summary");db.update("UPDATE dbo.ChuyenDi SET TongSoCho=100 WHERE ChuyenDiID=?",id);@SuppressWarnings("unchecked") var after=(Map<String,Object>)report.report(day,day,"XE_KHACH").get("summary");assertEquals(before.get("capacity"),after.get("capacity"));assertTrue(((Number)after.get("unassigned")).intValue()>0);}
}
