package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real committed transactions on two connections. Only this test's uniquely named fixtures are cleaned up. */
@SpringBootTest @EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class WalletConcurrencyIntegrationTests {
    @Autowired JdbcTemplate db;@Autowired TicketService tickets;@Autowired AccountService accounts;@Autowired WalletService wallet;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    record Fixture(int trip,String username,String code,String first,String second){}
    Fixture fixture(){return new TransactionTemplate(manager).execute(tx->{
        String key=UUID.randomUUID().toString().replace("-","").substring(0,10),name="rvt_qa_"+key;accounts.register(new RegistrationForm(name,"QA_Wallet_123","RVT kiểm thử đồng thời","0900000000"));
        var f=new TripForm();f.setCode("RVT-QA-"+key.toUpperCase());f.setOperator("RVT QA");f.setFrom("Điểm QA đi");f.setTo("Điểm QA đến");f.setRows(1);f.setPrice(BigDecimal.valueOf(315000));f.setDeparture(tickets.databaseNow().plusHours(7).plusDays(4));f.setArrival(f.getDeparture().plusHours(2));int id=TestTrips.open(tickets,db,f);
        String seed=tickets.book(id,booking("A01"),name);tickets.pay(seed,"0900000000");tickets.cancel(seed,"0900000000");
        return new Fixture(id,name,f.getCode(),tickets.book(id,booking("A02"),name),tickets.book(id,booking("A03"),name));
    });}
    BookingForm booking(String seat){var f=new BookingForm();f.setName("RVT QA");f.setPhone("0900000000");var p=new BookingForm.Passenger();p.setName("Hành khách QA");p.setSeat(seat);p.setLuggage(BigDecimal.ZERO);f.getPassengers().add(p);return f;}
    List<Boolean> concurrent(Callable<Boolean> first,Callable<Boolean> second) throws Exception{
        var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{assertTrue(gate.await(5,TimeUnit.SECONDS));return first.call();});var b=pool.submit(()->{assertTrue(gate.await(5,TimeUnit.SECONDS));return second.call();});gate.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    boolean pay(Fixture f,String code){try{tickets.pay(code,"0900000000","RVT",f.username());return true;}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("Số dư RVT không đủ"));return false;}}
    @Test void simultaneousDuplicatePaymentDebitsOnlyOnce() throws Exception{
        var f=fixture();try{assertEquals(List.of(true,true),concurrent(()->pay(f,f.first()),()->pay(f,f.first())));assertEquals(0,wallet.balance(f.username()).signum());assertEquals(2,wallet.history(f.username(),1).size());assertEquals("DA_THANH_TOAN",tickets.order(f.first(),"0900000000").get("TrangThai"));}finally{cleanup(f);}
    }
    @Test void twoDifferentTripsCannotSpendTheSameBalance() throws Exception{
        var f=fixture();int secondTrip=new TransactionTemplate(manager).execute(tx->{var t=new TripForm();t.setCode("RVT-QA-OTHER-"+UUID.randomUUID().toString().substring(0,6).toUpperCase());t.setOperator("RVT QA");t.setFrom("QA đi");t.setTo("QA đến");t.setRows(1);t.setPrice(BigDecimal.valueOf(315000));t.setDeparture(tickets.databaseNow().plusHours(7).plusDays(4));t.setArrival(t.getDeparture().plusHours(2));return TestTrips.open(tickets,db,t);});
        String other=tickets.book(secondTrip,booking("A01"),f.username());try{
            var results=concurrent(()->pay(f,f.first()),()->pay(f,other));assertEquals(1,results.stream().filter(Boolean::booleanValue).count());assertEquals(0,wallet.balance(f.username()).signum());assertEquals(2,wallet.history(f.username(),1).size());
        }finally{cleanupTrip(secondTrip);cleanup(f);}
    }
    @Test void simultaneousCancellationCreditsOnlyOnce() throws Exception{
        var f=fixture();try{assertTrue(pay(f,f.first()));concurrent(()->{tickets.cancel(f.first(),"0900000000");return true;},()->{tickets.cancel(f.first(),"0900000000");return true;});assertEquals(0,new BigDecimal("315000").compareTo(wallet.balance(f.username())));assertEquals(3,wallet.history(f.username(),1).size());}finally{cleanup(f);}
    }
    @Test void simultaneousDuplicateBookingCreatesOnlyOneOrder() throws Exception{
        var f=fixture();try{
            String key=UUID.randomUUID().toString();var received=new java.util.concurrent.ConcurrentLinkedQueue<String>();
            Callable<Boolean> attempt=()->{var form=booking("A04");form.setRequestKey(key);received.add(tickets.book(f.trip(),form,f.username()));return true;};
            assertEquals(List.of(true,true),concurrent(attempt,attempt));assertEquals(1,received.stream().distinct().count());
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.DonDatVe WHERE YeuCauDat=?",Integer.class,key));
        }finally{cleanup(f);}
    }
    void cleanup(Fixture f){assertEquals(f.code(),db.queryForObject("SELECT MaChuyen FROM dbo.ChuyenDi WHERE ChuyenDiID=?",String.class,f.trip()));cleanupTrip(f.trip());db.update("DELETE dbo.TaiKhoanKhach WHERE TenDangNhap=? AND TenDangNhap LIKE 'rvt_qa_%'",f.username());}
    void cleanupTrip(int id){new TransactionTemplate(manager).executeWithoutResult(tx->{
        assertTrue(db.queryForObject("SELECT MaChuyen FROM dbo.ChuyenDi WHERE ChuyenDiID=?",String.class,id).startsWith("RVT-QA-"));
        Integer vehicle=db.queryForObject("SELECT PhuongTienID FROM dbo.ChuyenDi WHERE ChuyenDiID=?",Integer.class,id);
        var contacts=db.queryForList("SELECT KhachHangID FROM dbo.DonDatVe WHERE ChuyenDiID=?",Integer.class,id);
        for(String table:List.of("GiaoDichRVT","ThongBaoKhach","HoanRVTChoNhan")){
            if(table.equals("ThongBaoKhach"))db.update("DELETE dbo.ThongBaoKhach WHERE MaDon IN(SELECT MaDon FROM dbo.DonDatVe WHERE ChuyenDiID=?)",id);
            else db.update("DELETE dbo."+table+" WHERE DonDatVeID IN(SELECT DonDatVeID FROM dbo.DonDatVe WHERE ChuyenDiID=?)",id);
        }
        for(String table:List.of("ThanhToan","ChiTietPhi","VeXeKhach","VeTauThuyen"))db.update("DELETE dbo."+table+" WHERE DangKyID IN(SELECT DangKyID FROM dbo.DangKyVe WHERE ChuyenDiID=?)",id);
        db.update("DELETE dbo.DonDatVeChiTiet WHERE DonDatVeID IN(SELECT DonDatVeID FROM dbo.DonDatVe WHERE ChuyenDiID=?)",id);db.update("DELETE dbo.DonDatVe WHERE ChuyenDiID=?",id);
        for(String table:List.of("DangKyVe","ThongTinChuyen","SoDoCho","ChoNgoi"))db.update("DELETE dbo."+table+" WHERE ChuyenDiID=?",id);db.update("DELETE dbo.ChuyenDi WHERE ChuyenDiID=?",id);
        TestTrips.cleanupVehicle(db,vehicle);
        for(int contact:contacts)db.update("DELETE dbo.KhachHang WHERE KhachHangID=?",contact);
    });}
}
