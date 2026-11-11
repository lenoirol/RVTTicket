package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import com.rvt.ticket.payment.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
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

/** rvt.vnpay.return-settles=true: a signed Return settles with IPN-equivalent checks, exactly once. */
@SpringBootTest(properties={"rvt.vnpay.enabled=true","rvt.vnpay.tmn-code=TEST0001","rvt.vnpay.hash-secret=test-only-secret-123456",
    "rvt.vnpay.public-base-url=https://rvt-test.invalid","rvt.vnpay.return-settles=true"})
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class VnpayReturnSettlementIntegrationTests {
    static final String KEY="test-only-secret-123456",PHONE="0900000000";
    @Autowired TicketService tickets;@Autowired VnpayService gateway;@Autowired AccountService accounts;
    @Autowired JdbcTemplate db;@Autowired MockMvc mvc;
    String unique(){return UUID.randomUUID().toString().substring(0,8).toUpperCase();}
    String customer(){String name="vr_"+unique();accounts.register(new RegistrationForm(name,"VNPayTest_123","Khách VNPay thử",PHONE));return name;}
    String booking(String name){
        var t=new TripForm();t.setCode("QA-VNR-"+unique());t.setOperator("Đơn vị kiểm thử");t.setFrom("TP. Hồ Chí Minh");t.setTo("Đà Lạt");t.setLayout("BUS_SEAT");t.setRows(2);t.setPrice(new BigDecimal("320000"));t.setDeparture(tickets.databaseNow().plusHours(7).plusDays(4));t.setArrival(t.getDeparture().plusHours(6));
        var f=new BookingForm();f.setName("Khách VNPay thử");f.setPhone(PHONE);
        var p=new BookingForm.Passenger();p.setSeat("A01");p.setName("Hành khách A01");f.getPassengers().add(p);
        return tickets.book(TestTrips.open(tickets,db,t),f,name);
    }
    Map<String,String> query(String url){var p=new HashMap<String,String>();for(String entry:URI.create(url).getRawQuery().split("&")){var s=entry.split("=",2);p.put(URLDecoder.decode(s[0],StandardCharsets.UTF_8),URLDecoder.decode(s[1],StandardCharsets.UTF_8));}return p;}
    Map<String,String> sign(Map<String,String> fields){var f=new HashMap<>(fields);f.remove("vnp_SecureHash");f.put("vnp_SecureHash",VnpayCodec.hash(KEY,VnpayCodec.query(f)));return f;}
    Map<String,String> callback(String code,String name){var q=query(gateway.start(code,name,"127.0.0.1"));return sign(Map.of("vnp_TmnCode","TEST0001","vnp_TxnRef",q.get("vnp_TxnRef"),"vnp_Amount",q.get("vnp_Amount"),"vnp_BankCode","NCB","vnp_ResponseCode","00","vnp_TransactionStatus","00","vnp_TransactionNo",String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()%99999999999999L)+1)));}
    String state(String code){return (String)tickets.order(code,PHONE).get("TrangThai");}
    int receipts(String code){return db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=?",Integer.class,code);}
    String source(Map<String,String> f){return db.queryForObject("SELECT NguonXacNhan FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",String.class,f.get("vnp_TxnRef"));}

    @Test void signedReturnSettlesOnceAndLaterIpnIsNoop() throws Exception {
        String name=customer(),code=booking(name);var f=callback(code,name);
        var req=get("/thanh-toan/vnpay/return").with(user(name).roles("CUSTOMER"));f.forEach(req::param);
        mvc.perform(req).andExpect(status().isOk());
        assertEquals("DA_THANH_TOAN",state(code));assertEquals(1,receipts(code));assertEquals("RETURN",source(f));
        req=get("/thanh-toan/vnpay/return");f.forEach(req::param);mvc.perform(req).andExpect(status().isOk());
        assertEquals("02",gateway.ipn(f).get("RspCode"));assertEquals(1,receipts(code));
    }
    @Test void tamperedReturnDoesNotSettle() throws Exception {
        String name=customer(),code=booking(name);var f=new HashMap<>(callback(code,name));f.put("vnp_Amount","100");
        var req=get("/thanh-toan/vnpay/return");f.forEach(req::param);mvc.perform(req).andExpect(status().isOk());
        assertEquals("GIU_CHO",state(code));assertEquals(0,receipts(code));
        var resigned=sign(f);assertEquals("04",gateway.signedReturn(resigned).get("RspCode"));assertEquals(0,receipts(code));
    }
}
