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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class WalletPassengerIntegrationTests {
    @Autowired JdbcTemplate db; @Autowired TicketService tickets; @Autowired WalletService wallet;
    @Autowired AccountService accounts; @Autowired NotificationService notifications; @Autowired MockMvc mvc;
    String customer(){String name="wallet_"+UUID.randomUUID().toString().replace("-","").substring(0,10);accounts.register(new RegistrationForm(name,"WalletTest_123","Khách ví thử","0900000000"));return name;}
    int trip(boolean boat){var f=new TripForm();f.setCode("WALLET-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());f.setOperator("RVT kiểm thử");f.setFrom("Rạch Giá");f.setTo("Phú Quốc");f.setLayout(boat?"BOAT":"BUS_SEAT");f.setRows(2);f.setPrice(BigDecimal.valueOf(315000));f.setChildPrice(BigDecimal.valueOf(225000));f.setSeniorPrice(BigDecimal.valueOf(265000));f.setVipSeats(boat?1:0);f.setVipExtra(BigDecimal.valueOf(185000));f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(4));f.setArrival(f.getDeparture().plusHours(3));return TestTrips.open(tickets,db,f);}
    BookingForm booking(String... seats){var f=new BookingForm();f.setName("Khách ví");f.setPhone("0900000000");for(String seat:seats){var p=new BookingForm.Passenger();p.setSeat(seat);p.setName("Hành khách "+seat);p.setBirthDate(LocalDate.of(1985,1,1));p.setDocument("012345678901");p.setLuggage(BigDecimal.ZERO);f.getPassengers().add(p);}return f;}
    BigDecimal amount(String code){return (BigDecimal)tickets.order(code,"0900000000").get("TongTien");}
    void money(BigDecimal expected,BigDecimal actual){assertEquals(0,expected.compareTo(actual));}
    /** Khách tự hủy khi còn từ 24 giờ trở lên: phí 10% theo mốc của xe khách, phần còn lại hoàn vào số dư. */
    static BigDecimal afterFee(BigDecimal paid){return paid.subtract(com.rvt.ticket.booking.FarePolicy.cancellationFee(paid,new BigDecimal("0.10")));}

    @Test void refundThenPayWithBalanceAndRefundAgainIsIdempotent(){
        String name=customer();int id=trip(false);String first=tickets.book(id,booking("A01","A02"),name);BigDecimal total=amount(first);
        money(BigDecimal.ZERO,wallet.balance(name));tickets.pay(first,"0900000000");tickets.adminCancelOrder(first,"Tạo số dư kiểm thử","admin");tickets.adminCancelOrder(first,"Lặp lại","admin");money(total,wallet.balance(name));assertEquals(1,wallet.history(name,1).size());
        String second=tickets.book(id,booking("A03","A04"),name);tickets.pay(second,"0900000000","RVT",name);tickets.pay(second,"0900000000","RVT",name);money(BigDecimal.ZERO,wallet.balance(name));assertEquals(2,wallet.history(name,1).size());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=? AND p.PhuongThuc='SO_DU_RVT'",Integer.class,second));
        tickets.cancel(second,"0900000000");tickets.cancel(second,"0900000000");money(afterFee(total),wallet.balance(name));assertEquals(3,wallet.history(name,1).size());
        money(total.subtract(afterFee(total)),(BigDecimal)tickets.order(second,"0900000000").get("PhiHuy"));
    }
    @Test void insufficientFundsAndWrongOwnerDoNotChargeOrConfirm(){
        String name=customer(),other=customer();String code=tickets.book(trip(false),booking("A01"),name);
        assertThrows(IllegalArgumentException.class,()->tickets.pay(code,"0900000000","RVT",name));assertThrows(IllegalArgumentException.class,()->tickets.pay(code,"0900000000","RVT",other));
        assertEquals("GIU_CHO",tickets.order(code,"0900000000").get("TrangThai"));money(BigDecimal.ZERO,wallet.balance(name));assertTrue(wallet.history(name,1).isEmpty());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=?",Integer.class,code));
    }
    @Test void cancelUnpaidDoesNotInventBalance(){String name=customer();String code=tickets.book(trip(false),booking("A01"),name);tickets.cancel(code,"0900000000");money(BigDecimal.ZERO,wallet.balance(name));assertTrue(wallet.history(name,1).isEmpty());}
    @Test void refundIncludesThePaidLuggageFee(){String name=customer();var f=booking("A01");f.getPassengers().getFirst().setLuggage(BigDecimal.valueOf(26));String code=tickets.book(trip(false),f,name);tickets.pay(code,"0900000000");tickets.cancel(code,"0900000000");money(afterFee(amount(code)),wallet.balance(name));assertTrue(wallet.balance(name).compareTo(BigDecimal.valueOf(315000))>0);}
    @Test void cancelInsideLastTierRejectedButAdminTripCancellationRefunds(){
        String name=customer();int id=trip(false);String code=tickets.book(id,booking("A01"),name);tickets.pay(code,"0900000000");
        db.update("UPDATE dbo.ChuyenDi SET KhoiHanh=DATEADD(HOUR,3,SYSUTCDATETIME()),DuKienDen=DATEADD(HOUR,6,SYSUTCDATETIME()) WHERE ChuyenDiID=?",id);
        assertThrows(IllegalArgumentException.class,()->tickets.cancel(code,"0900000000"));money(BigDecimal.ZERO,wallet.balance(name));tickets.cancelTrip(id,"Biển động (kiểm thử)","admin");tickets.cancelTrip(id,"Lặp lại","admin");money(amount(code),wallet.balance(name));assertEquals(1,wallet.history(name,1).size());
        assertTrue(((List<?>)notifications.inbox(name).get("items")).size()>=3);
    }
    @Test void adminOrderCancellationReturnsBalance(){String name=customer();String code=tickets.book(trip(false),booking("A01"),name);tickets.pay(code,"0900000000");tickets.adminCancelOrder(code,"Theo yêu cầu khách","admin");tickets.adminCancelOrder(code,"Lặp lại","admin");money(amount(code),wallet.balance(name));assertEquals(1,wallet.history(name,1).size());}
    @Test void guestLegacyRefundWaitsForExplicitClaimAndIsCreditedOnce(){
        String name=customer();String code=tickets.book(trip(false),booking("A01"));tickets.pay(code,"0900000000");tickets.adminCancelOrder(code,"Đơn cũ kiểm thử","admin");money(BigDecimal.ZERO,wallet.balance(name));wallet.claimLegacy(code,"0900000000",name);wallet.claimLegacy(code,"0900000000",name);money(amount(code),wallet.balance(name));assertThrows(IllegalArgumentException.class,()->wallet.claimLegacy(code,"0900000000",customer()));
    }
    @Test void faresUseDateOfTravelAndVipDoesNotDiscount(){
        int id=trip(true);LocalDate date=((Timestamp)tickets.trip(id).get("KhoiHanh")).toLocalDateTime().plusHours(7).toLocalDate();var f=booking("A01","A02","A03","A04");
        f.getPassengers().get(0).setBirthDate(date.minusYears(10));f.getPassengers().get(1).setBirthDate(date.minusYears(10));f.getPassengers().get(1).setDocumentType("KHAI_SINH");f.getPassengers().get(2).setBirthDate(date.minusYears(60));f.getPassengers().get(3).setBirthDate(date.minusYears(12));
        String code=tickets.book(id,f,customer());@SuppressWarnings("unchecked")var rows=(List<Map<String,Object>>)tickets.order(code,"0900000000").get("tickets");
        money(new BigDecimal("500000"),(BigDecimal)rows.get(0).get("GiaVe"));money(new BigDecimal("225000"),(BigDecimal)rows.get(1).get("GiaVe"));money(new BigDecimal("265000"),(BigDecimal)rows.get(2).get("GiaVe"));money(new BigDecimal("315000"),(BigDecimal)rows.get(3).get("GiaVe"));assertEquals("CAO_TUOI",rows.get(2).get("NhomHanhKhach"));
        db.update("UPDATE dbo.ChuyenDi SET GiaTreEm=200000 WHERE ChuyenDiID=?",id);money(new BigDecimal("1305000"),amount(code));
    }
    @Test void childrenNeedAdultAndValidBirthday(){int id=trip(true);var f=booking("A02");f.getPassengers().getFirst().setBirthDate(LocalDate.now().minusYears(10));assertThrows(IllegalArgumentException.class,()->tickets.book(id,f,customer()));f.getPassengers().getFirst().setBirthDate(null);assertThrows(IllegalArgumentException.class,()->tickets.book(id,f,customer()));f.getPassengers().getFirst().setBirthDate(LocalDate.now().plusYears(1));assertThrows(IllegalArgumentException.class,()->tickets.book(id,f,customer()));}
    @Test void failedBoatFormKeepsBirthdayForClientSideRestore() throws Exception{
        String name=customer();mvc.perform(post("/dat-ve/"+trip(true)).with(user(name).roles("CUSTOMER")).with(csrf()).param("name","").param("phone","0900000000").param("passengers[0].name","Khách DOB").param("passengers[0].seat","A02").param("passengers[0].birthDate","1985-01-01").param("passengers[0].document","012345678901").param("passengers[0].documentType","CCCD").param("passengers[0].nationality","Việt Nam").param("passengers[0].luggage","0")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"birthDate\":\"1985-01-01\"")));
    }
    @Test void walletAndPopupPagesRenderAndNotificationReadIsPrivate() throws Exception{
        String name=customer(),other=customer();String code=tickets.book(trip(false),booking("A01"),name);var session=new MockHttpSession();session.setAttribute("order:"+code,"0900000000");
        mvc.perform(get("/don-ve/"+code).session(session).with(user(name).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Thanh toán bằng số dư RVT")));
        for(String url:List.of("/tai-khoan","/tai-khoan/so-du","/tai-khoan/thong-bao"))mvc.perform(get(url).with(user(name).roles("CUSTOMER"))).andExpect(status().isOk());
        long id=((Number)db.queryForObject("SELECT MAX(ThongBaoID) FROM dbo.ThongBaoKhach WHERE MaDon=?",Long.class,code)).longValue();notifications.read(other,id);assertEquals(1,notifications.inbox(name).get("unread"));notifications.read(name,id);assertEquals(0,notifications.inbox(name).get("unread"));
        mvc.perform(post("/tai-khoan/thong-bao/"+id+"/doc").with(user(name).roles("CUSTOMER"))).andExpect(status().isForbidden());
    }
    @Test void authenticationAndOwnershipRequiredForBookingAndPayment() throws Exception{
        int id=trip(false);mvc.perform(get("/dat-ve/"+id)).andExpect(status().is3xxRedirection());mvc.perform(get("/dat-ve/"+id).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/dat-ve/"+id).with(csrf())).andExpect(status().is3xxRedirection());
        String owner=customer(),other=customer();String code=tickets.book(id,booking("A01"),owner);var session=new MockHttpSession();session.setAttribute("order:"+code,"0900000000");
        mvc.perform(post("/don-ve/"+code+"/thanh-toan").session(session).with(user(other).roles("CUSTOMER")).with(csrf()).param("method","DEMO")).andExpect(status().is3xxRedirection());assertEquals("GIU_CHO",tickets.order(code,"0900000000").get("TrangThai"));
        mvc.perform(post("/don-ve/"+code+"/huy").session(session).with(user(other).roles("CUSTOMER")).with(csrf())).andExpect(status().is3xxRedirection());assertEquals("GIU_CHO",tickets.order(code,"0900000000").get("TrangThai"));
    }
    @Test void legacyClaimCannotUseDifferentPhone(){String name=customer();String code=tickets.book(trip(false),booking("A01"));assertThrows(IllegalArgumentException.class,()->wallet.claimLegacy(code,"0999999999",name));assertNull(tickets.order(code,"0900000000").get("TaiKhoanID"));}
    @Test void expiryNotifiesOnceAndDoesNotDebit(){String name=customer();int id=trip(false);String code=tickets.book(id,booking("A01"),name);int order=((Number)tickets.order(code,"0900000000").get("DonDatVeID")).intValue();db.update("UPDATE dbo.DangKyVe SET NgayDangKy=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE DangKyID IN(SELECT DangKyID FROM dbo.DonDatVeChiTiet WHERE DonDatVeID=?)",order);db.update("UPDATE dbo.DonDatVe SET NgayTao=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE DonDatVeID=?",order);tickets.expireTrip(id);tickets.expireTrip(id);assertEquals(2,notifications.inbox(name).get("unread"));money(BigDecimal.ZERO,wallet.balance(name));assertThrows(IllegalArgumentException.class,()->tickets.pay(code,"0900000000","RVT",name));}
}
