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

/** Điểm đón / trả, mã khuyến mãi, đổi vé, phí hủy theo mốc, hóa đơn điện tử, lọc loại xe và sơ đồ có ghế tài xế. */
@SpringBootTest(properties={"ADMIN_PASSWORD=123456"}) @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class BookingFeaturesIntegrationTests {
    static final String PHONE="0900000000";
    @Autowired JdbcTemplate db;
    @Autowired TicketService tickets;
    @Autowired AccountService accounts;
    @Autowired WalletService wallet;
    @Autowired StopService stops;
    @Autowired PromotionService promotions;
    @Autowired ExchangeService exchanges;
    @Autowired InvoiceService invoices;
    @Autowired MockMvc mvc;

    String unique(){return UUID.randomUUID().toString().replace("-","").substring(0,10);}
    String customer(){var name="feat_"+unique();accounts.register(new RegistrationForm(name,"CustomerTest_123","Khách tính năng","0900000000"));return name;}
    int trip(String from,String to,String layout,int days,int hours,int price){
        var f=new TripForm();f.setCode("FEAT-"+unique().toUpperCase());f.setOperator("RVT kiểm thử");f.setFrom(from);f.setTo(to);f.setLayout(layout);f.setRows(3);
        f.setPrice(BigDecimal.valueOf(price));f.setChildPrice(BigDecimal.valueOf(price*8/10));f.setSeniorPrice(BigDecimal.valueOf(price*8/10));
        f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(days).plusHours(hours).withNano(0));f.setArrival(f.getDeparture().plusHours(6));
        return TestTrips.open(tickets,db,f);
    }
    int bus(){return trip("TP. Hồ Chí Minh","Đà Lạt","BUS_SEAT",4,0,300000);}
    BookingForm form(String... seats){var f=new BookingForm();f.setName("Khách tính năng");f.setPhone(PHONE);for(String s:seats){var p=new BookingForm.Passenger();p.setName("Hành khách "+s);p.setSeat(s);p.setLuggage(BigDecimal.ZERO);f.getPassengers().add(p);}return f;}
    void fund(String name,long amount){db.update("UPDATE dbo.TaiKhoanKhach SET SoDuRVT=? WHERE TenDangNhap=?",BigDecimal.valueOf(amount),name);}
    BigDecimal total(String code){return (BigDecimal)tickets.order(code,PHONE).get("TongTien");}
    void money(long expected,Object actual){assertEquals(0,BigDecimal.valueOf(expected).compareTo((BigDecimal)actual),"expected "+expected+" but was "+actual);}

    // ---- 1. Điểm đón / trả ----
    @Test void chosenTransferStopIsSnapshottedWithItsPickupTime(){
        int id=bus();var trip=tickets.trip(id);var pickups=stops.pickups(trip);
        assertTrue(pickups.size()>1);assertNull(pickups.getFirst().id());
        var office=pickups.stream().filter(s->s.id()!=null).findFirst().orElseThrow();
        var dropoff=stops.dropoffs(trip).stream().filter(s->s.id()!=null).findFirst().orElseThrow();
        var f=form("A01");f.setPickupId(office.id());f.setDropoffId(dropoff.id());String code=tickets.book(id,f,customer());
        var order=tickets.order(code,PHONE);
        assertEquals(office.name()+" (trung chuyển)",order.get("DiemDon"));assertEquals(office.id(),order.get("DiemDonID"));
        assertEquals(((Timestamp)trip.get("KhoiHanh")).toLocalDateTime().plusMinutes(office.offsetMinutes()),((Timestamp)order.get("GioDonDuKien")).toLocalDateTime());
        assertEquals(office.name()+" (trung chuyển)",db.queryForObject("SELECT x.DiemDon FROM dbo.VeXeKhach x JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=x.DangKyID WHERE d.DonDatVeID=?",String.class,order.get("DonDatVeID")));
    }
    @Test void stopFromAnotherCityOrOnABoatIsRejected(){
        int id=bus();int hanoi=db.queryForObject("SELECT TOP 1 DiemID FROM dbo.DiemDonTra WHERE ThanhPho=N'Hà Nội'",Integer.class);
        var f=form("A01");f.setPickupId(hanoi);String name=customer();
        assertThrows(IllegalArgumentException.class,()->tickets.book(id,f,name));
        int boat=trip("Rạch Giá","Phú Quốc","BOAT",4,0,300000);var b=form("A01");b.getPassengers().getFirst().setBirthDate(java.time.LocalDate.of(1990,1,1));b.getPassengers().getFirst().setDocument("012345678901");b.setPickupId(hanoi);
        assertThrows(IllegalArgumentException.class,()->tickets.book(boat,b,name));
    }

    // ---- 2. Mã khuyến mãi ----
    @Test void promotionReducesEveryTicketAndThePaidAmount(){
        String name=customer();String code=tickets.book(bus(),form("A01","A02"),name);
        var applied=promotions.apply(code,PHONE,name,"rvtmoi");money(50000,applied); // 10% của 600.000, tối đa 50.000
        money(550000,total(code));
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)tickets.order(code,PHONE).get("tickets");
        money(550000,rows.stream().map(t->((BigDecimal)t.get("GiaVe")).add((BigDecimal)t.get("TongPhi"))).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertThrows(IllegalArgumentException.class,()->promotions.apply(code,PHONE,name,"XEKHACH30K"));
        fund(name,550000);tickets.pay(code,PHONE,"RVT",name);assertEquals(0,wallet.balance(name).signum());
        money(550000,db.queryForObject("SELECT SUM(p.SoTien) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=?",BigDecimal.class,code));
        String second=tickets.book(bus(),form("A01"),name);
        assertThrows(IllegalArgumentException.class,()->promotions.apply(second,PHONE,name,"RVTMOI")); // mỗi tài khoản một lần
        money(30000,promotions.apply(second,PHONE,name,"XEKHACH30K")); // mã khác vẫn dùng được
    }
    @Test void promotionRulesAndRemoval(){
        String name=customer();String code=tickets.book(bus(),form("A01"),name);
        assertThrows(IllegalArgumentException.class,()->promotions.apply(code,PHONE,name,"TAUBIEN15"));
        assertThrows(IllegalArgumentException.class,()->promotions.apply(code,PHONE,name,"KHONGCO"));
        money(30000,promotions.apply(code,PHONE,name,"XEKHACH30K"));money(270000,total(code));
        promotions.remove(code,PHONE,name);money(300000,total(code));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.ChiTietPhi p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=? AND p.MaPhi='KHUYEN_MAI'",Integer.class,code));
        String other=customer();assertThrows(IllegalArgumentException.class,()->promotions.apply(code,PHONE,other,"XEKHACH30K"));
        tickets.pay(code,PHONE);assertThrows(IllegalArgumentException.class,()->promotions.apply(code,PHONE,name,"XEKHACH30K"));
    }

    // ---- 9. Phí hủy theo mốc ----
    @Test void cancellationFeeFollowsTheTierOfTheTicketType(){
        String name=customer();String early=tickets.book(bus(),form("A01"),name);tickets.pay(early,PHONE);
        money(30000,tickets.order(early,PHONE).get("PhiHuyDuKien"));tickets.cancel(early,PHONE,"Thay đổi kế hoạch");
        money(270000,wallet.balance(name));money(30000,tickets.order(early,PHONE).get("PhiHuy"));money(270000,tickets.order(early,PHONE).get("TienHoan"));
        int soon=trip("TP. Hồ Chí Minh","Đà Lạt","BUS_SEAT",0,10,300000);String late=tickets.book(soon,form("A01"),name);tickets.pay(late,PHONE);
        assertEquals(25,tickets.order(late,PHONE).get("MucPhiHuy"));tickets.cancel(late,PHONE,"Thay đổi kế hoạch");money(270000+225000,wallet.balance(name));
        int closing=trip("TP. Hồ Chí Minh","Đà Lạt","BUS_SEAT",0,10,300000);String last=tickets.book(closing,form("A01"),name);tickets.pay(last,PHONE);
        db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(HOUR,3,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,9,SYSUTCDATETIME()) WHERE ChuyenDiID=?",closing);
        assertEquals(Boolean.FALSE,tickets.order(last,PHONE).get("CoTheKhachHuy"));assertThrows(IllegalArgumentException.class,()->tickets.cancel(last,PHONE,"Muộn"));
        int boat=trip("Rạch Giá","Phú Quốc","BOAT",1,6,400000);var b=form("A05");b.getPassengers().getFirst().setBirthDate(java.time.LocalDate.of(1990,1,1));b.getPassengers().getFirst().setDocument("012345678901");
        String sea=tickets.book(boat,b,name);tickets.pay(sea,PHONE);assertEquals(20,tickets.order(sea,PHONE).get("MucPhiHuy"));
        String unpaid=tickets.book(bus(),form("A02"),name);tickets.cancel(unpaid,PHONE,"Đặt nhầm");assertNull(tickets.order(unpaid,PHONE).get("PhiHuy"));
    }

    // ---- 3. Đổi vé ----
    @Test void exchangeMovesPaymentToTheNewTripAndSettlesTheDifference(){
        String name=customer();int from=bus();String code=tickets.book(from,form("A01","A02"),name);tickets.pay(code,PHONE);
        int cheaper=trip("TP. Hồ Chí Minh","Đà Lạt","BUS_SEAT",5,0,250000);
        var newDate=((Timestamp)tickets.trip(cheaper).get("KhoiHanh")).toLocalDateTime().plusHours(7).toLocalDate();
        assertTrue(exchanges.candidates(code,PHONE,newDate).stream().anyMatch(t->Objects.equals(t.get("ChuyenDiID"),cheaper)));
        assertThrows(IllegalArgumentException.class,()->exchanges.exchange(code,PHONE,name,cheaper,List.of("A03")));
        String fresh=exchanges.exchange(code,PHONE,name,cheaper,List.of("A03","A04"));
        var old=tickets.order(code,PHONE);var neu=tickets.order(fresh,PHONE);
        assertEquals("DA_HUY",old.get("TrangThai"));assertEquals(neu.get("DonDatVeID"),old.get("DoiSangDonID"));
        assertEquals("DA_THANH_TOAN",neu.get("TrangThai"));assertEquals(old.get("DonDatVeID"),neu.get("DoiTuDonID"));
        money(100000,wallet.balance(name)); // 600.000 đã trả - 500.000 đơn mới
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=? AND p.PhuongThuc='DOI_VE' AND p.TrangThai='THANH_CONG'",Integer.class,neu.get("DonDatVeID")));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.DangKyVe WHERE ChuyenDiID=? AND DangChiemCho=1",Integer.class,from));
        assertEquals(Boolean.FALSE,neu.get("CoTheDoiVe"));
        int another=trip("TP. Hồ Chí Minh","Đà Lạt","BUS_SEAT",6,0,250000);
        assertThrows(IllegalArgumentException.class,()->exchanges.exchange(fresh,PHONE,name,another,List.of("A01","A02")));
    }
    @Test void exchangeToAPricierTripNeedsEnoughBalanceAndSameRoute(){
        String name=customer();String code=tickets.book(bus(),form("A01"),name);tickets.pay(code,PHONE);
        int pricier=trip("TP. Hồ Chí Minh","Đà Lạt","BUS_SEAT",5,0,380000);
        assertThrows(IllegalArgumentException.class,()->exchanges.exchange(code,PHONE,name,pricier,List.of("A01")));
        assertEquals("DA_THANH_TOAN",tickets.order(code,PHONE).get("TrangThai"));
        int otherRoute=trip("TP. Hồ Chí Minh","Cần Thơ","BUS_SEAT",5,0,200000);
        assertThrows(IllegalArgumentException.class,()->exchanges.exchange(code,PHONE,name,otherRoute,List.of("A01")));
        fund(name,80000);String fresh=exchanges.exchange(code,PHONE,name,pricier,List.of("A05"));
        assertEquals(0,wallet.balance(name).signum());money(380000,total(fresh));
    }

    // ---- 8. Hóa đơn điện tử ----
    @Test void invoiceSplitsVatOnceAndCanBeLookedUp(){
        String name=customer();String code=tickets.book(bus(),form("A01"),name);
        assertThrows(IllegalArgumentException.class,()->invoices.issue(code,PHONE,name,new InvoiceService.Buyer("CA_NHAN","Nguyễn Văn A",null,null,null)));
        tickets.pay(code,PHONE);
        assertThrows(IllegalArgumentException.class,()->invoices.issue(code,PHONE,name,new InvoiceService.Buyer("DON_VI","Công ty A",null,"Quận 1",null)));
        var invoice=invoices.issue(code,PHONE,name,new InvoiceService.Buyer("DON_VI","Công ty TNHH A","0312345678","Quận 1, TP. Hồ Chí Minh","ketoan@example.com"));
        money(272727,invoice.get("TienTruocThue"));money(27273,invoice.get("TienThue"));money(300000,invoice.get("TongThanhToan"));
        assertEquals(InvoiceService.SERIES,invoice.get("KyHieu"));assertEquals(7,((String)invoice.get("SoHoaDon")).length());
        var again=invoices.issue(code,PHONE,name,new InvoiceService.Buyer("CA_NHAN","Người khác",null,null,null));
        assertEquals(invoice.get("HoaDonID"),again.get("HoaDonID"));
        assertEquals(code,invoices.lookup((String)invoice.get("MaTraCuu")).get("MaDon"));
        assertThrows(IllegalArgumentException.class,()->invoices.lookup("ZZZZZZZZZZ"));
    }

    // ---- 6. Lọc loại xe, tầng và sơ đồ ----
    @Test void sleeperFilterAndSeatMapShowDecksAndDriverSeat() throws Exception{
        int sleeper=trip("TP. Hồ Chí Minh","Đà Lạt","BUS_SLEEPER",4,1,350000);int seat=trip("TP. Hồ Chí Minh","Đà Lạt","BUS_SEAT",4,2,300000);
        String date=((Timestamp)tickets.trip(sleeper).get("KhoiHanh")).toLocalDateTime().plusHours(7).toLocalDate().toString();
        var sleeperCode=com.rvt.ticket.booking.TripIdentity.code(tickets.trip(sleeper));var seatCode=com.rvt.ticket.booking.TripIdentity.code(tickets.trip(seat));
        mvc.perform(get("/chuyen-di").param("type","XE_KHACH").param("from","TP. Hồ Chí Minh").param("to","Đà Lạt").param("date",date).param("vehicle","GIUONG_NAM").param("deck","upper"))
            .andExpect(status().isOk()).andExpect(content().string(containsString(sleeperCode))).andExpect(content().string(not(containsString(seatCode)))).andExpect(content().string(containsString("Tầng dưới")));
        mvc.perform(get("/chuyen-di").param("type","XE_KHACH").param("from","TP. Hồ Chí Minh").param("to","Đà Lạt").param("date",date).param("vehicle","GHE_NGOI"))
            .andExpect(content().string(containsString(seatCode))).andExpect(content().string(not(containsString(sleeperCode))));
        mvc.perform(get("/chuyen-di").param("vehicle","XE_BAY")).andExpect(status().is4xxClientError());
        String name=customer();
        mvc.perform(get("/dat-ve/"+sleeper).with(user(name).roles("CUSTOMER","TRAVELER"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Ghế tài xế"))).andExpect(content().string(containsString("Tầng trên"))).andExpect(content().string(containsString("name=\"pickupId\"")));
    }
    @Test void fleetTemplatesHaveDistinctSleeperStructures(){
        var s41=FleetService.templateSeats("sleep41");var limo=FleetService.templateSeats("limo24");var s36=FleetService.templateSeats("sleep36");
        assertEquals(41,s41.size());assertEquals(24,limo.size());assertEquals(36,s36.size());
        assertEquals(5,s41.stream().filter(s->s.floor()==1&&s.row()==7).count()); // hàng cuối 5 giường
        assertEquals(Set.of(1,3,5),new HashSet<>(s36.stream().map(FleetService.Seat::column).toList())); // 3 dãy
        assertEquals(Set.of(1,5),new HashSet<>(limo.stream().map(FleetService.Seat::column).toList())); // limousine 2 dãy
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM dbo.PhuongTien WHERE MaPhuongTien IN('RVT-MAU-4','RVT-MAU-5')",Integer.class)==2);
    }

    // ---- Giao diện đơn vé ----
    @Test void orderPagesRenderPromotionTiersExchangeAndInvoice() throws Exception{
        String name=customer();String code=tickets.book(bus(),form("A01"),name);var session=new MockHttpSession();session.setAttribute("order:"+code,PHONE);
        mvc.perform(get("/don-ve/"+code).session(session).with(user(name).roles("CUSTOMER","TRAVELER"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Mã khuyến mãi"))).andExpect(content().string(containsString("RVTMOI")));
        mvc.perform(post("/don-ve/"+code+"/khuyen-mai").session(session).with(csrf()).with(user(name).roles("CUSTOMER","TRAVELER")).param("promo","XEKHACH30K"))
            .andExpect(redirectedUrl("/don-ve/"+code+".html")).andExpect(flash().attributeExists("message"));
        tickets.pay(code,PHONE);
        mvc.perform(get("/don-ve/"+code).session(session).with(user(name).roles("CUSTOMER","TRAVELER"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("phí 10%"))).andExpect(content().string(containsString("Đổi ngày / giờ đi"))).andExpect(content().string(containsString("Xuất hóa đơn điện tử")));
        mvc.perform(get("/don-ve/"+code+"/doi-ve").session(session).with(user(name).roles("CUSTOMER","TRAVELER"))).andExpect(status().isOk()).andExpect(content().string(containsString("Chọn chuyến mới")));
        mvc.perform(get("/don-ve/"+code+"/hoa-don").session(session).with(user(name).roles("CUSTOMER","TRAVELER"))).andExpect(status().isOk()).andExpect(content().string(containsString("Xuất hóa đơn")));
        mvc.perform(post("/don-ve/"+code+"/hoa-don").session(session).with(csrf()).with(user(name).roles("CUSTOMER","TRAVELER")).param("kind","CA_NHAN").param("name","Khách tính năng"))
            .andExpect(redirectedUrl("/don-ve/"+code+"/hoa-don.html"));
        String lookup=invoices.forOrder(tickets.order(code,PHONE).get("DonDatVeID")).orElseThrow().get("MaTraCuu").toString();
        mvc.perform(post("/hoa-don").with(csrf()).param("lookup",lookup)).andExpect(status().isOk()).andExpect(content().string(containsString("HÓA ĐƠN GIÁ TRỊ GIA TĂNG")));
        var anonymous=new MockHttpSession();anonymous.setAttribute("order:"+code,PHONE);
        mvc.perform(post("/don-ve/"+code+"/doi-ve/1").session(anonymous).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login*"));
    }
}
