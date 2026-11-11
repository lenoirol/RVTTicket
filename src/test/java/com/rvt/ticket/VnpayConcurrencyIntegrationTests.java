package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import com.rvt.ticket.payment.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Committed, isolated fixtures; parallel callbacks use separate SQL Server connections. */
@SpringBootTest(properties={"rvt.vnpay.enabled=true","rvt.vnpay.tmn-code=TEST0001","rvt.vnpay.hash-secret=test-only-secret-123456","rvt.vnpay.public-base-url=https://rvt-test.invalid"})
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class VnpayConcurrencyIntegrationTests {
    @Autowired JdbcTemplate db;@Autowired TicketService tickets;@Autowired AccountService accounts;@Autowired VnpayService gateway;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    record Fixture(int trip,String tripCode,String customer,String order){}
    Fixture fixture(){return new TransactionTemplate(manager).execute(tx->{
        String key=UUID.randomUUID().toString().substring(0,8).toUpperCase(),name="vnp_qa_"+key;accounts.register(new RegistrationForm(name,"VNPayTest_123","Khách kiểm thử đồng thời","0900000000"));
        var t=new TripForm();t.setCode("QA-VNP-C-"+key);t.setOperator("VNPay QA");t.setFrom("QA đi");t.setTo("QA đến");t.setRows(1);t.setPrice(new BigDecimal("315000"));t.setDeparture(tickets.databaseNow().plusHours(7).plusDays(4));t.setArrival(t.getDeparture().plusHours(3));int id=TestTrips.open(tickets,db,t);
        var b=new BookingForm();b.setName("VNPay QA");b.setPhone("0900000000");var p=new BookingForm.Passenger();p.setName("VNPay QA");p.setSeat("A01");p.setLuggage(BigDecimal.ZERO);b.getPassengers().add(p);
        return new Fixture(id,t.getCode(),name,tickets.book(id,b,name));
    });}
    <T> List<T> concurrent(Callable<T> first,Callable<T> second) throws Exception {
        var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{assertTrue(gate.await(5,TimeUnit.SECONDS));return first.call();});var b=pool.submit(()->{assertTrue(gate.await(5,TimeUnit.SECONDS));return second.call();});gate.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    Map<String,String> callback(Fixture f){
        var q=new HashMap<String,String>();for(String entry:URI.create(gateway.start(f.order(),f.customer(),"127.0.0.1")).getRawQuery().split("&")){var kv=entry.split("=",2);q.put(kv[0],URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}
        var fields=new HashMap<>(Map.of("vnp_TmnCode","TEST0001","vnp_TxnRef",q.get("vnp_TxnRef"),"vnp_Amount",q.get("vnp_Amount"),"vnp_BankCode","NCB","vnp_ResponseCode","00","vnp_TransactionStatus","00","vnp_TransactionNo",String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()%99999999999999L)+1)));
        fields.put("vnp_SecureHash",VnpayCodec.hash("test-only-secret-123456",VnpayCodec.query(fields)));return fields;
    }
    @Test void twoConcurrentStartsReuseOneAttempt() throws Exception {
        var f=fixture();try{var urls=concurrent(()->gateway.start(f.order(),f.customer(),"127.0.0.1"),()->gateway.start(f.order(),f.customer(),"127.0.0.1"));assertEquals(urls.getFirst(),urls.getLast());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.GiaoDichVNPay a JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID WHERE o.MaDon=?",Integer.class,f.order()));}finally{cleanup(f);}
    }
    @Test void duplicateConcurrentIpnsCreateOnlyOneReceipt() throws Exception {
        var f=fixture();try{var fields=callback(f);var replies=concurrent(()->gateway.ipn(fields).get("RspCode"),()->gateway.ipn(fields).get("RspCode"));assertEquals(Set.of("00","02"),new HashSet<>(replies));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=?",Integer.class,f.order()));assertEquals("DA_THANH_TOAN",tickets.order(f.order(),"0900000000").get("TrangThai"));}finally{cleanup(f);}
    }
    @Test void simultaneousIpnAndQueryConfirmationIssueOnlyOneReceipt() throws Exception {
        var f=fixture();try{
            var fields=callback(f);var replies=concurrent(()->gateway.ipn(fields).get("RspCode"),()->gateway.verifiedQuery(fields).get("RspCode"));
            assertEquals(Set.of("00","02"),new HashSet<>(replies));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.MaDon=?",Integer.class,f.order()));
            assertNotNull(db.queryForObject("SELECT NhanIPNLuc FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",java.sql.Timestamp.class,fields.get("vnp_TxnRef")));
        }finally{cleanup(f);}
    }
    @Test void concurrentAdminRefundRequestsCreditExactlyOnce() throws Exception {
        var f=fixture();try{
            var fields=callback(f);String ref=fields.get("vnp_TxnRef");
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                db.update("UPDATE dbo.DangKyVe SET NgayDangKy=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE ChuyenDiID=?",f.trip());
                db.update("UPDATE dbo.DonDatVe SET NgayTao=DATEADD(MINUTE,-20,SYSUTCDATETIME()),HanThanhToan=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE MaDon=?",f.order());tickets.expireTrip(f.trip());
            });gateway.ipn(fields);
            concurrent(()->{gateway.resolveReview(ref,"Đối chiếu khoản thu cho đơn hết hạn.","admin");return true;},()->{gateway.resolveReview(ref,"Đối chiếu khoản thu cho đơn hết hạn.","admin");return true;});
            assertEquals(new BigDecimal("315000.00"),db.queryForObject("SELECT SoDuRVT FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",BigDecimal.class,f.customer()));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.GiaoDichRVT WHERE MaSuKien=?",Integer.class,"VNPAY_REFUND:"+ref));
            assertEquals("HET_HAN",tickets.order(f.order(),"0900000000").get("TrangThai"));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID WHERE v.ChuyenDiID=?",Integer.class,f.trip()));
        }finally{cleanup(f);}
    }
    void cleanup(Fixture f){
        assertEquals(f.tripCode(),db.queryForObject("SELECT MaChuyen FROM dbo.ChuyenDi WHERE ChuyenDiID=?",String.class,f.trip()));
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            Integer vehicle=db.queryForObject("SELECT PhuongTienID FROM dbo.ChuyenDi WHERE ChuyenDiID=?",Integer.class,f.trip());
            var customers=db.queryForList("SELECT DISTINCT KhachHangID FROM dbo.DonDatVe WHERE ChuyenDiID=?",Integer.class,f.trip());
            db.update("DELETE dbo.ThongBaoKhach WHERE MaDon=?",f.order());db.update("DELETE dbo.NhatKyThaoTac WHERE DoiTuong=?","Đơn "+f.order());
            db.update("DELETE n FROM dbo.NhatKyThaoTac n JOIN dbo.GiaoDichVNPay a ON a.MaThamChieu=n.DoiTuong JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID WHERE o.ChuyenDiID=?",f.trip());
            db.update("DELETE g FROM dbo.GiaoDichRVT g JOIN dbo.DonDatVe o ON o.DonDatVeID=g.DonDatVeID WHERE o.ChuyenDiID=?",f.trip());
            db.update("DELETE h FROM dbo.HoanVNPayChoNhan h JOIN dbo.DonDatVe o ON o.DonDatVeID=h.DonDatVeID WHERE o.ChuyenDiID=?",f.trip());
            db.update("DELETE a FROM dbo.GiaoDichVNPay a JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID WHERE o.ChuyenDiID=?",f.trip());
            db.update("DELETE p FROM dbo.ThanhToan p JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID WHERE v.ChuyenDiID=?",f.trip());
            db.update("DELETE p FROM dbo.ChiTietPhi p JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID WHERE v.ChuyenDiID=?",f.trip());
            db.update("DELETE d FROM dbo.DonDatVeChiTiet d JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE o.ChuyenDiID=?",f.trip());
            db.update("DELETE x FROM dbo.VeXeKhach x JOIN dbo.DangKyVe v ON v.DangKyID=x.DangKyID WHERE v.ChuyenDiID=?",f.trip());
            db.update("DELETE dbo.DangKyVe WHERE ChuyenDiID=?",f.trip());db.update("DELETE dbo.DonDatVe WHERE ChuyenDiID=?",f.trip());
            db.update("DELETE dbo.SoDoCho WHERE ChuyenDiID=?",f.trip());db.update("DELETE dbo.ChoNgoi WHERE ChuyenDiID=?",f.trip());db.update("DELETE dbo.ThongTinChuyen WHERE ChuyenDiID=?",f.trip());db.update("DELETE dbo.ChuyenDi WHERE ChuyenDiID=?",f.trip());
            TestTrips.cleanupVehicle(db,vehicle);
            db.update("DELETE dbo.TaiKhoanKhach WHERE TenDangNhap=?",f.customer());
            for(int id:customers)db.update("DELETE dbo.KhachHang WHERE KhachHangID=? AND NOT EXISTS(SELECT 1 FROM dbo.DangKyVe WHERE KhachHangID=?) AND NOT EXISTS(SELECT 1 FROM dbo.DonDatVe WHERE KhachHangID=?)",id,id,id);
        });
    }
}
