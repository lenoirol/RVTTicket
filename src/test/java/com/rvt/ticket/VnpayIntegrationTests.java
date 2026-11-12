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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"rvt.vnpay.enabled=true","rvt.vnpay.tmn-code=TEST0001","rvt.vnpay.hash-secret=test-only-secret-123456","rvt.vnpay.public-base-url=https://rvt-test.invalid"})
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class VnpayIntegrationTests {
    static final String KEY="test-only-secret-123456",PHONE="0900000000";
    @Autowired TicketService tickets;@Autowired VnpayService gateway;@Autowired AccountService accounts;
    @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired WalletService wallet;
    @Autowired VnpaySettings settings;@Autowired ReportService reports;
    String unique(){return UUID.randomUUID().toString().substring(0,8).toUpperCase();}
    String customer(){String name="vn_"+unique();accounts.register(new RegistrationForm(name,"VNPayTest_123","Khách VNPay thử",PHONE));return name;}
    String booking(String name){
        var t=new TripForm();t.setCode("QA-VNP-"+unique());t.setOperator("Đơn vị kiểm thử");t.setFrom("TP. Hồ Chí Minh");t.setTo("Đà Lạt");t.setLayout("BUS_SEAT");t.setRows(2);t.setPrice(new BigDecimal("320000"));t.setDeparture(tickets.databaseNow().plusHours(7).plusDays(4));t.setArrival(t.getDeparture().plusHours(6));
        var f=new BookingForm();f.setName("Khách VNPay thử");f.setPhone(PHONE);
        for(String seat:List.of("A01","A02")){var p=new BookingForm.Passenger();p.setSeat(seat);p.setName("Hành khách "+seat);p.setLuggage(new BigDecimal("26"));f.getPassengers().add(p);}
        return tickets.book(TestTrips.open(tickets,db,t),f,name);
    }
    Map<String,String> query(String url){var p=new HashMap<String,String>();for(String entry:URI.create(url).getRawQuery().split("&")){var s=entry.split("=",2);p.put(URLDecoder.decode(s[0],StandardCharsets.UTF_8),URLDecoder.decode(s[1],StandardCharsets.UTF_8));}return p;}
    Map<String,String> sign(Map<String,String> fields){var f=new HashMap<>(fields);f.remove("vnp_SecureHash");f.put("vnp_SecureHash",VnpayCodec.hash(KEY,VnpayCodec.query(f)));return f;}
    Map<String,String> callback(String code,String name){var q=query(gateway.start(code,name,"127.0.0.1"));return sign(Map.of("vnp_TmnCode","TEST0001","vnp_TxnRef",q.get("vnp_TxnRef"),"vnp_Amount",q.get("vnp_Amount"),"vnp_BankCode","NCB","vnp_ResponseCode","00","vnp_TransactionStatus","00","vnp_TransactionNo",String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()%99999999999999L)+1)));}
    String state(String code){return (String)tickets.order(code,PHONE).get("TrangThai");}
    String attempt(Map<String,String> f){return db.queryForObject("SELECT TrangThai FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",String.class,f.get("vnp_TxnRef"));}
    int receipts(String code){return db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=?",Integer.class,code);}
    @Test void startIsSignedReusesAttemptAndNeverConfirmsOrCharges(){
        String name=customer(),code=booking(name);String url=gateway.start(code,name,"127.0.0.1");var q=query(url);
        assertTrue(url.startsWith(VnpaySettings.PAY_URL+"?"));assertTrue(VnpayCodec.verify(KEY,q));
        assertEquals("https://rvt-test.invalid/thanh-toan/vnpay/return",q.get("vnp_ReturnUrl"));assertEquals("VND",q.get("vnp_CurrCode"));assertEquals("2.1.0",q.get("vnp_Version"));
        assertEquals(url,gateway.start(code,name,"127.0.0.2"));assertEquals("GIU_CHO",state(code));assertEquals(0,receipts(code));assertEquals(0,wallet.balance(name).signum());
    }
    @Test void wrongOwnerAndExpiredOrderCannotStart(){String name=customer(),code=booking(name);assertThrows(IllegalArgumentException.class,()->gateway.start(code,customer(),"127.0.0.1"));expire(code);assertThrows(IllegalArgumentException.class,()->gateway.start(code,name,"127.0.0.1"));}
    @Test void returnUsesOriginalApprovedOriginAndKeepsAttempt() {
        String name=customer(),code=booking(name);
        var first=query(gateway.start(code,name,"127.0.0.1","http://localhost:8080"));
        assertEquals("http://localhost:8080/thanh-toan/vnpay/return",first.get("vnp_ReturnUrl"));
        assertTrue(VnpayCodec.verify(KEY,first));
        var second=query(gateway.start(code,name,"127.0.0.1","http://127.0.0.1:8080"));
        assertEquals(first.get("vnp_TxnRef"),second.get("vnp_TxnRef"));
        assertEquals("http://127.0.0.1:8080/thanh-toan/vnpay/return",second.get("vnp_ReturnUrl"));
        assertThrows(IllegalArgumentException.class,()->gateway.start(code,name,"127.0.0.1","http://evil.invalid"));
        assertEquals("GIU_CHO",state(code));
    }
    void expire(String code){
        db.update("UPDATE v SET NgayDangKy=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=?",code);
        db.update("UPDATE dbo.DonDatVe SET NgayTao=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE MaDon=?",code);
        tickets.expireTrip(((Number)tickets.order(code,PHONE).get("ChuyenDiID")).intValue());
    }
    @Test void badSignatureMerchantUnknownRefAndWrongAmountCannotSettle(){
        String name=customer(),code=booking(name);var f=callback(code,name);
        var bad=new HashMap<>(f);bad.put("vnp_Amount","1");assertEquals("97",gateway.ipn(bad).get("RspCode"));assertEquals("04",gateway.ipn(sign(bad)).get("RspCode"));
        bad=new HashMap<>(f);bad.put("vnp_TmnCode","OTHER001");assertEquals("97",gateway.ipn(sign(bad)).get("RspCode"));
        bad=new HashMap<>(f);bad.put("vnp_TxnRef","UNKNOWN");assertEquals("01",gateway.ipn(sign(bad)).get("RspCode"));assertEquals("GIU_CHO",state(code));assertEquals(0,receipts(code));
    }
    @Test void ipnConfirmsEntireOrderIncludingLuggageExactlyOnce(){
        String name=customer(),code=booking(name);var f=callback(code,name);
        assertEquals("00",gateway.ipn(f).get("RspCode"));assertEquals("SUCCESS",attempt(f));assertEquals("DA_THANH_TOAN",state(code));assertEquals(2,receipts(code));
        assertEquals("02",gateway.ipn(f).get("RspCode"));assertEquals(2,receipts(code));assertEquals(0,wallet.balance(name).signum());
        var sum=db.queryForObject("SELECT SUM(p.SoTien) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=? AND p.PhuongThuc='VNPAY'",BigDecimal.class,code);
        assertEquals(0,sum.compareTo((BigDecimal)tickets.order(code,PHONE).get("TongTien")));
    }
    @Test void browserReturnNeverSettlesAndDoesNotGrantOtherCustomerAccess() throws Exception {
        String name=customer(),code=booking(name);var f=callback(code,name);var req=get("/thanh-toan/vnpay/return");f.forEach(req::param);
        mvc.perform(req).andExpect(status().isOk()).andExpect(content().string(containsString("Đang xác minh giao dịch"))).andExpect(content().string(not(containsString("/don-ve/"+code))));assertEquals("GIU_CHO",state(code));
        req=get("/thanh-toan/vnpay/return").with(user(customer()).roles("CUSTOMER"));f.forEach(req::param);mvc.perform(req).andExpect(content().string(not(containsString("/don-ve/"+code))));
        var session=new MockHttpSession();req=get("/thanh-toan/vnpay/return").session(session).with(user(name).roles("CUSTOMER"));f.forEach(req::param);mvc.perform(req).andExpect(content().string(containsString("/don-ve/"+code)));assertEquals(PHONE,session.getAttribute("order:"+code));assertEquals(0,receipts(code));
    }
    @Test void failureDoesNotPayAndRetryUsesNewReference(){String name=customer(),code=booking(name);var f=callback(code,name);var bad=new HashMap<>(f);bad.put("vnp_ResponseCode","24");bad.put("vnp_TransactionStatus","02");assertEquals("00",gateway.ipn(sign(bad)).get("RspCode"));assertEquals("FAILED",attempt(f));assertEquals("GIU_CHO",state(code));assertEquals(0,receipts(code));assertNotEquals(f.get("vnp_TxnRef"),query(gateway.start(code,name,"127.0.0.1")).get("vnp_TxnRef"));}
    @Test void bothSuccessCodesAreRequired(){String name=customer(),code=booking(name);var f=callback(code,name);f.put("vnp_TransactionStatus","02");gateway.ipn(sign(f));assertEquals("FAILED",attempt(f));assertEquals(0,receipts(code));}
    @Test void incompleteIpnIsNotFinalFailureAndCanLaterConfirm(){
        String name=customer(),code=booking(name);var f=callback(code,name);var pending=new HashMap<>(f);pending.put("vnp_TransactionStatus","01");
        assertEquals("00",gateway.ipn(sign(pending)).get("RspCode"));assertEquals("PENDING",attempt(f));assertEquals(0,receipts(code));
        assertEquals("00",gateway.ipn(f).get("RspCode"));assertEquals("SUCCESS",attempt(f));assertEquals(2,receipts(code));
        assertNull(db.queryForObject("SELECT LoiTruyVan FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",String.class,f.get("vnp_TxnRef")));
    }
    @Test void ReversalRefundAndSuspectedCaptureRequireReviewNotInventedRefund(){
        String name=customer();for(String status:List.of("04","05","06","07","09")){
            String code=booking(name);var f=callback(code,name);f.put("vnp_TransactionStatus",status);f.put("vnp_ResponseCode","07");f=sign(f);
            assertEquals("00",gateway.ipn(f).get("RspCode"));assertEquals("PENDING",attempt(f));assertEquals(0,receipts(code));
            String ref=f.get("vnp_TxnRef");assertEquals(Boolean.TRUE,db.queryForObject("SELECT CanDoiSoat FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",Boolean.class,ref));
            assertThrows(IllegalArgumentException.class,()->gateway.resolveReview(ref,"Giao dịch chưa được xác minh thành công.","admin"));
        }assertEquals(0,wallet.balance(name).signum());
    }
    @Test void expiredSuccessIsRecordedForReviewWithoutReallocatingSeats(){String name=customer(),code=booking(name);var f=callback(code,name);expire(code);assertEquals("00",gateway.ipn(f).get("RspCode"));assertEquals("REVIEW",attempt(f));assertEquals("HET_HAN",state(code));assertEquals(0,receipts(code));assertEquals(0,wallet.balance(name).signum());assertEquals("02",gateway.ipn(f).get("RspCode"));}
    @Test void cancelledSuccessIsRecordedForReviewWithoutRefundingUnpaidOrder(){String name=customer(),code=booking(name);var f=callback(code,name);tickets.cancel(code,PHONE,"Thay đổi kế hoạch");gateway.ipn(f);assertEquals("REVIEW",attempt(f));assertEquals("DA_HUY",state(code));assertEquals(0,receipts(code));assertEquals(0,wallet.balance(name).signum());}
    @Test void pendingGatewayBlocksDirectPaymentAndWalletSwitch(){String name=customer(),code=booking(name);callback(code,name);assertThrows(IllegalArgumentException.class,()->tickets.pay(code,PHONE,"RVT",name));assertThrows(IllegalArgumentException.class,()->tickets.pay(code,PHONE,"DEMO",name));assertEquals("GIU_CHO",state(code));}
    @Test void secondCapturedPaymentAfterAnotherMethodRequiresReview(){String name=customer(),code=booking(name);var f=callback(code,name);db.update("UPDATE dbo.GiaoDichVNPay SET HetHanLuc=DATEADD(SECOND,1,NgayTao) WHERE MaThamChieu=?",f.get("vnp_TxnRef"));db.update("UPDATE dbo.GiaoDichVNPay SET NgayTao=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HetHanLuc=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE MaThamChieu=?",f.get("vnp_TxnRef"));tickets.pay(code,PHONE);gateway.ipn(f);assertEquals("REVIEW",attempt(f));assertEquals(2,receipts(code));}
    @Test void malformedDuplicateParametersAndUnsignedCallbacksAreRejected() throws Exception {
        mvc.perform(get("/thanh-toan/vnpay/ipn").param("vnp_Amount","1","2")).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"));
        mvc.perform(get("/thanh-toan/vnpay/ipn")).andExpect(jsonPath("$.RspCode").value("97"));
        mvc.perform(get("/thanh-toan/vnpay/return")).andExpect(status().isOk()).andExpect(content().string(containsString("Chưa xác minh")));
    }
    @Test void checkoutRequiresCustomerCsrfAndDoesNotAcceptDemoByDefault() throws Exception {
        String name=customer(),code=booking(name);mvc.perform(post("/don-ve/"+code+"/thanh-toan").with(user(name).roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/don-ve/"+code+"/thanh-toan").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/don-ve/"+code+"/thanh-toan").with(user(name).roles("CUSTOMER")).with(csrf()).param("method","DEMO")).andExpect(flash().attribute("error",containsString("đã được tắt")));assertEquals(0,receipts(code));
        mvc.perform(post("/don-ve/"+code+"/thanh-toan").with(r->{r.setServerPort(8080);return r;}).with(user(name).roles("CUSTOMER")).with(csrf())).andExpect(redirectedUrlPattern(VnpaySettings.PAY_URL+"?*"));
    }
    @Test void adminMonitoringIsPrivateAndDirectConfirmationIsDisabled() throws Exception {
        mvc.perform(get("/admin/vnpay")).andExpect(status().is3xxRedirection());mvc.perform(get("/admin/vnpay").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        String name=customer(),code=booking(name);callback(code,name);mvc.perform(get("/admin/vnpay").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString(code)));
        mvc.perform(post("/admin/don-ve/"+code).with(user("admin").roles("ADMIN")).with(csrf()).param("action","pay")).andExpect(flash().attribute("error",containsString("đã tắt")));assertEquals(0,receipts(code));
    }
    @Test void checkoutHasVnpayAndNoDirectDemoOption() throws Exception {
        String name=customer(),code=booking(name);var session=new MockHttpSession();session.setAttribute("order:"+code,PHONE);
        mvc.perform(get("/don-ve/"+code).session(session).with(user(name).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(containsString("Thanh toán qua VNPay"))).andExpect(content().string(not(containsString("value=\"DEMO\""))));
    }
    @Test void paymentLabelsAreNeutralButCheckoutKeepsTestCardWarning() throws Exception {
        String name=customer(),code=booking(name);var session=new MockHttpSession();session.setAttribute("order:"+code,PHONE);
        mvc.perform(get("/don-ve/"+code).session(session).with(user(name).roles("CUSTOMER"))).andExpect(content().string(containsString("Thanh toán qua VNPay"))).andExpect(content().string(containsString("không nhập thẻ hoặc tài khoản ngân hàng thật"))).andExpect(content().string(not(containsString("Sandbox"))));
        var f=callback(code,name);gateway.ipn(f);
        mvc.perform(get("/don-ve/"+code).session(session).with(user(name).roles("CUSTOMER"))).andExpect(content().string(containsString("VNPay · Đã xác minh thanh toán"))).andExpect(content().string(not(containsString("Sandbox"))));
        var request=get("/thanh-toan/vnpay/return").with(user(name).roles("CUSTOMER"));f.forEach(request::param);
        mvc.perform(request).andExpect(content().string(containsString("RVT Ticket · VNPay"))).andExpect(content().string(not(containsString("SANDBOX")))).andExpect(content().string(not(containsString("Sandbox"))));
        mvc.perform(get("/huong-dan")).andExpect(status().isOk()).andExpect(content().string(containsString("Thanh toán qua VNPay hoặc số dư RVT"))).andExpect(content().string(not(containsString("Phiên bản học tập không thu tiền thật"))));
        mvc.perform(get("/admin/vnpay").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(not(containsString("Sandbox"))));
        mvc.perform(get("/admin/thong-ke").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("Giá trị giao dịch ghi nhận"))).andExpect(content().string(not(containsString("MÔ PHỎNG"))));
    }
    @Test void ipnIsAnonymousIndependentOfBrowserSession() throws Exception {
        String name=customer(),code=booking(name);var f=callback(code,name);var request=get("/thanh-toan/vnpay/ipn");f.forEach(request::param);
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));assertEquals("DA_THANH_TOAN",state(code));
    }
    @Test void freePassengerDoesNotCauseZeroReceiptConstraintFailure(){
        String name=customer(),code=booking(name);var o=tickets.order(code,PHONE);int id=((Number)o.get("DonDatVeID")).intValue();
        int ticket=db.queryForObject("SELECT MIN(DangKyID) FROM dbo.DonDatVeChiTiet WHERE DonDatVeID=?",Integer.class,id);
        BigDecimal share=db.queryForObject("SELECT v.GiaVe+COALESCE((SELECT SUM(p.SoTien) FROM dbo.ChiTietPhi p WHERE p.DangKyID=v.DangKyID),0) FROM dbo.DangKyVe v WHERE DangKyID=?",BigDecimal.class,ticket);
        db.update("UPDATE dbo.DangKyVe SET GiaVe=0 WHERE DangKyID=?",ticket);db.update("DELETE dbo.ChiTietPhi WHERE DangKyID=?",ticket);
        db.update("UPDATE dbo.DonDatVe SET TongTien=? WHERE DonDatVeID=?",((BigDecimal)o.get("TongTien")).subtract(share),id);
        var f=callback(code,name);assertEquals("00",gateway.ipn(f).get("RspCode"));assertEquals("SUCCESS",attempt(f));assertEquals(1,receipts(code));assertEquals("DA_THANH_TOAN",state(code));
    }
    @Test void changedPriceSnapshotIsHeldForReviewInsteadOfMisallocatingPayment(){
        String name=customer(),code=booking(name);var f=callback(code,name);
        db.update("UPDATE dbo.DonDatVe SET TongTien=TongTien+1000 WHERE MaDon=?",code);
        assertEquals("00",gateway.ipn(f).get("RspCode"));assertEquals("REVIEW",attempt(f));assertEquals(0,receipts(code));assertEquals("GIU_CHO",state(code));
    }
    @Test void queryConfirmationIsNotMisreportedAsIpnAndLateIpnIsIdempotent(){
        String name=customer(),code=booking(name);var f=callback(code,name);
        assertEquals("00",gateway.verifiedQuery(f).get("RspCode"));
        var a=db.queryForMap("SELECT * FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",f.get("vnp_TxnRef"));
        assertEquals("QUERYDR",a.get("NguonXacNhan"));assertNotNull(a.get("KetQuaNhanLuc"));assertNull(a.get("NhanIPNLuc"));
        assertEquals("02",gateway.ipn(f).get("RspCode"));assertEquals(2,receipts(code));
        a=db.queryForMap("SELECT * FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",f.get("vnp_TxnRef"));
        assertEquals("QUERYDR",a.get("NguonXacNhan"));assertNotNull(a.get("NhanIPNLuc"));
    }
    @Test void capturedExpiredPaymentCanBeRefundedOnceWithoutCreatingTicket(){
        String name=customer(),code=booking(name);var f=callback(code,name);String ref=f.get("vnp_TxnRef");
        var amount=(BigDecimal)tickets.order(code,PHONE).get("TongTien");expire(code);gateway.ipn(f);
        gateway.resolveReview(ref,"Đối chiếu VNPay thành công, đơn hết giữ chỗ.","admin");
        gateway.resolveReview(ref,"Yêu cầu lặp lại đã đối chiếu giao dịch.","admin");
        assertEquals(0,amount.compareTo(wallet.balance(name)));assertEquals(1,wallet.history(name,1).size());
        assertEquals("HET_HAN",state(code));assertEquals(0,receipts(code));assertEquals("REFUNDED",gateway.result(f).get("state"));
        var a=db.queryForMap("SELECT * FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",ref);
        assertEquals("HOAN_RVT",a.get("XuLyDoiSoat"));assertEquals("admin",a.get("NguoiXuLy"));assertNotNull(a.get("XuLyLuc"));
        assertEquals("02",gateway.ipn(f).get("RspCode"));assertEquals(0,amount.compareTo(wallet.balance(name)));
    }
    @Test void adminCannotRefundUnverifiedOrAlreadyIssuedPayment(){
        String name=customer(),code=booking(name);var f=callback(code,name);String ref=f.get("vnp_TxnRef");
        assertThrows(IllegalArgumentException.class,()->gateway.resolveReview(ref,"Đối chiếu giao dịch chưa thành công.","admin"));
        expire(code);gateway.ipn(f);
        assertThrows(IllegalArgumentException.class,()->gateway.resolveReview(ref,"ngắn","admin"));assertEquals(0,wallet.balance(name).signum());
        String paid=booking(name);var success=callback(paid,name);gateway.ipn(success);
        assertThrows(IllegalArgumentException.class,()->gateway.resolveReview(success.get("vnp_TxnRef"),"Vé đã cấp không dùng quy trình ngoại lệ.","admin"));
        assertEquals("DA_THANH_TOAN",state(paid));assertEquals(2,receipts(paid));assertEquals(0,wallet.balance(name).signum());
    }
    @Test void refundOfSecondCapturedPaymentKeepsOriginalReceiptAndSeparateLedger(){
        String name=customer(),code=booking(name);var f=callback(code,name);String ref=f.get("vnp_TxnRef");
        db.update("UPDATE dbo.GiaoDichVNPay SET NgayTao=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HetHanLuc=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE MaThamChieu=?",ref);
        tickets.pay(code,PHONE);gateway.ipn(f);var before=tickets.staffOrder(code).get("tickets");
        gateway.resolveReview(ref,"Đối chiếu khoản thu thứ hai, giữ nguyên vé đã cấp.","admin");
        assertEquals("DA_THANH_TOAN",state(code));assertEquals(before,tickets.staffOrder(code).get("tickets"));assertEquals(2,receipts(code));
        var amount=(BigDecimal)tickets.order(code,PHONE).get("TongTien");assertEquals(0,amount.compareTo(wallet.balance(name)));
        tickets.cancel(code,PHONE,"Thay đổi kế hoạch");assertEquals(0,amount.add(WalletPassengerIntegrationTests.afterFee(amount)).compareTo(wallet.balance(name)));assertEquals(2,wallet.history(name,1).size());
    }
    @Test void exceptionRefundHttpRequiresAdminCsrfAndReason() throws Exception {
        String name=customer(),code=booking(name);var f=callback(code,name);expire(code);gateway.ipn(f);
        String url="/admin/vnpay/"+f.get("vnp_TxnRef")+"/hoan-rvt";
        mvc.perform(post(url).with(user(name).roles("CUSTOMER")).with(csrf()).param("reason","Đã đối chiếu giao dịch thành công.")).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user("admin").roles("ADMIN")).param("reason","Đã đối chiếu giao dịch thành công.")).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user("admin").roles("ADMIN")).with(csrf()).param("reason","ngắn")).andExpect(flash().attributeExists("error"));assertEquals(0,wallet.balance(name).signum());
        mvc.perform(post(url).with(user("admin").roles("ADMIN")).with(csrf()).param("reason","Đã đối chiếu giao dịch thành công, hoàn đơn hết hạn.")).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        assertEquals("REFUNDED",gateway.result(f).get("state"));assertEquals(1,wallet.history(name,1).size());
        mvc.perform(get("/admin/vnpay?state=REFUNDED").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString(f.get("vnp_TxnRef"))));
    }
    @Test void unownedHistoricalCaptureWaitsForExplicitAccountClaim(){
        String name=customer(),code=booking(name);var f=callback(code,name);String ref=f.get("vnp_TxnRef");expire(code);gateway.ipn(f);
        db.update("UPDATE dbo.DonDatVe SET TaiKhoanID=NULL WHERE MaDon=?",code);
        gateway.resolveReview(ref,"Đã đối chiếu khoản thu, chờ chủ đơn xác nhận tài khoản.","admin");
        assertEquals(0,wallet.balance(name).signum());assertEquals(Boolean.FALSE,db.queryForObject("SELECT DaNhan FROM dbo.HoanVNPayChoNhan WHERE MaThamChieu=?",Boolean.class,ref));
        assertEquals("REFUND_PENDING",gateway.result(f).get("state"));
        assertThrows(IllegalArgumentException.class,()->wallet.claimLegacy(code,"0900000001",name));
        wallet.claimLegacy(code,PHONE,name);wallet.claimLegacy(code,PHONE,name);assertEquals(1,wallet.history(name,1).size());
        assertEquals(0,((BigDecimal)tickets.order(code,PHONE).get("TongTien")).compareTo(wallet.balance(name)));
        assertEquals(Boolean.TRUE,db.queryForObject("SELECT DaNhan FROM dbo.HoanVNPayChoNhan WHERE MaThamChieu=?",Boolean.class,ref));
        assertEquals("REFUNDED",gateway.result(f).get("state"));
    }
    @SuppressWarnings("unchecked") Map<String,Object> money(){var day=tickets.databaseNow().plusHours(7).toLocalDate();return (Map<String,Object>)reports.report(day,day,"").get("money");}
    BigDecimal value(Map<String,Object> m,String key){return (BigDecimal)m.get(key);}
    @Test void reportSeparatesGatewayCaptureFromWalletReuseAndExceptionRefund(){
        var before=money();String name=customer(),first=booking(name);var f=callback(first,name);gateway.ipn(f);var amount=(BigDecimal)tickets.order(first,PHONE).get("TongTien");
        tickets.adminCancelOrder(first,"Theo yêu cầu khách","admin");String second=booking(name);tickets.pay(second,PHONE,"RVT",name);
        String third=booking(name);var late=callback(third,name);expire(third);gateway.ipn(late);gateway.resolveReview(late.get("vnp_TxnRef"),"Đã đối chiếu, hoàn khoản thu cho đơn hết hạn.","admin");
        var after=money();assertEquals(0,amount.multiply(BigDecimal.valueOf(2)).compareTo(value(after,"vnpayVerified").subtract(value(before,"vnpayVerified"))));
        assertEquals(0,amount.compareTo(value(after,"walletDebits").subtract(value(before,"walletDebits"))));
        assertEquals(0,amount.multiply(BigDecimal.valueOf(2)).compareTo(value(after,"walletCredits").subtract(value(before,"walletCredits"))));
        assertEquals(0,amount.compareTo(value(after,"vnpayResolvedRvt").subtract(value(before,"vnpayResolvedRvt"))));
        var day=tickets.databaseNow().plusHours(7).toLocalDate();var methods=(Collection<Map<String,Object>>)reports.report(day,day,"").get("byMethod");
        assertTrue(methods.stream().anyMatch(m->"VNPay".equals(m.get("label"))));assertTrue(methods.stream().anyMatch(m->"Số dư RVT".equals(m.get("label"))));
    }
    @Test void retryQueueLeasesSlotsFairlyAndEscalatesOldRequestsWithoutMarkingPaid(){
        String name=customer(),code=booking(name);var f=callback(code,name);int order=((Number)tickets.order(code,PHONE).get("DonDatVeID")).intValue();
        db.update("UPDATE dbo.GiaoDichVNPay SET NgayTao=DATEADD(DAY,-2,SYSUTCDATETIME()),HetHanLuc=DATEADD(DAY,-1,SYSUTCDATETIME()) WHERE MaThamChieu=?",f.get("vnp_TxnRef"));
        var refs=new HashSet<String>();
        for(int i=0;i<13;i++){String ref="RVT"+UUID.randomUUID().toString().replace("-","").toUpperCase();refs.add(ref);db.update("INSERT dbo.GiaoDichVNPay(MaThamChieu,DonDatVeID,SoTien,TmnCode,NgayTao,HetHanLuc,DiaChiIP) VALUES(?,?,315000,'TEST0001',DATEADD(MINUTE,-2,SYSUTCDATETIME()),DATEADD(MINUTE,10,SYSUTCDATETIME()),'127.0.0.1')",ref,order);}
        var queue=new VnpayQuery(db,gateway,settings);var first=queue.claimDue();var second=queue.claimDue();
        var seen=new HashSet<Object>();first.forEach(a->assertTrue(seen.add(a.get("MaThamChieu"))));second.forEach(a->assertTrue(seen.add(a.get("MaThamChieu"))));
        assertTrue(seen.containsAll(refs));assertTrue(queue.claimDue().isEmpty());assertEquals(10,first.size());
        assertEquals("PENDING",attempt(f));assertEquals(Boolean.TRUE,db.queryForObject("SELECT CanDoiSoat FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",Boolean.class,f.get("vnp_TxnRef")));
        assertEquals(30,VnpayQuery.retrySeconds(1));assertEquals(60,VnpayQuery.retrySeconds(2));assertEquals(1800,VnpayQuery.retrySeconds(100));assertEquals(0,receipts(code));
    }
}
