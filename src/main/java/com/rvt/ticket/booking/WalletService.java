package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final JdbcTemplate db;
    public WalletService(JdbcTemplate db){this.db=db;}
    public BigDecimal balance(String username){return db.queryForObject("SELECT SoDuRVT FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",BigDecimal.class,username);}
    public List<Map<String,Object>> history(String username,int page){
        if(page<1||page>10000)throw new IllegalArgumentException("Trang không hợp lệ.");
        return db.queryForList("SELECT g.*,o.MaDon FROM dbo.GiaoDichRVT g JOIN dbo.TaiKhoanKhach a ON a.TaiKhoanID=g.TaiKhoanID JOIN dbo.DonDatVe o ON o.DonDatVeID=g.DonDatVeID WHERE a.TenDangNhap=? ORDER BY g.GiaoDichID DESC OFFSET ? ROWS FETCH NEXT 21 ROWS ONLY",username,(page-1)*20);
    }
    // Lock order is trip -> booking -> account, shared by pay/cancel/claim. Never called without an order lock.
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void debit(Map<String,Object> order,String username,BigDecimal amount){
        var rows=db.queryForList("SELECT TaiKhoanID,SoDuRVT FROM dbo.TaiKhoanKhach WITH(UPDLOCK,HOLDLOCK) WHERE TenDangNhap=? AND BiKhoa=0",username);
        if(rows.isEmpty()||!Objects.equals(rows.getFirst().get("TaiKhoanID"),order.get("TaiKhoanID")))throw new IllegalArgumentException("Chỉ chủ đơn được thanh toán bằng số dư RVT.");
        movement(order,rows.getFirst(),amount.negate(),"PAY:","Thanh toán đơn "+order.get("MaDon"));
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void refund(Map<String,Object> order){
        BigDecimal amount=db.queryForObject("SELECT COALESCE(SUM(p.SoTien),0) FROM dbo.ThanhToan p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=? AND p.TrangThai='THANH_CONG'",BigDecimal.class,order.get("DonDatVeID"));
        if(amount.signum()==0)return; // Unpaid bookings do not create money.
        if(order.get("TaiKhoanID")==null){
            db.update("INSERT dbo.HoanRVTChoNhan(DonDatVeID,SoTien) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.HoanRVTChoNhan WHERE DonDatVeID=?)",order.get("DonDatVeID"),amount,order.get("DonDatVeID"));
        }else credit(order,amount);
    }
    /** Hoàn một phần (sau khi trừ phí hủy). Đơn chưa gắn tài khoản: lưu khoản chờ nhận như hoàn toàn phần. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void refundPartial(Map<String,Object> order,BigDecimal amount,BigDecimal fee){
        if(amount==null||amount.signum()<=0)return;
        if(order.get("TaiKhoanID")==null){
            db.update("INSERT dbo.HoanRVTChoNhan(DonDatVeID,SoTien) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.HoanRVTChoNhan WHERE DonDatVeID=?)",order.get("DonDatVeID"),amount,order.get("DonDatVeID"));
            return;
        }
        var account=db.queryForMap("SELECT TaiKhoanID,SoDuRVT FROM dbo.TaiKhoanKhach WITH(UPDLOCK,HOLDLOCK) WHERE TaiKhoanID=?",order.get("TaiKhoanID"));
        movement(order,account,amount,"REFUND:","Hoàn đơn "+order.get("MaDon")+(fee.signum()>0?" (đã trừ phí hủy "+fee.stripTrailingZeros().toPlainString()+" đồng)":""));
    }
    /** Chênh lệch khi đổi vé: dương là cộng vào số dư, âm là trừ. Khóa sự kiện riêng để không ghi hai lần. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void exchangeDifference(Map<String,Object> newOrder,String username,BigDecimal amount,String oldCode){
        if(amount.signum()==0)return;
        var rows=db.queryForList("SELECT TaiKhoanID,SoDuRVT FROM dbo.TaiKhoanKhach WITH(UPDLOCK,HOLDLOCK) WHERE TenDangNhap=? AND BiKhoa=0",username);
        if(rows.isEmpty()||!Objects.equals(rows.getFirst().get("TaiKhoanID"),newOrder.get("TaiKhoanID")))throw new IllegalArgumentException("Chỉ chủ đơn được đổi vé.");
        movementKey(newOrder,rows.getFirst(),amount,"EXCHANGE:"+newOrder.get("DonDatVeID"),(amount.signum()>0?"Hoàn chênh lệch khi đổi từ đơn ":"Trả thêm chênh lệch khi đổi từ đơn ")+oldCode+" sang "+newOrder.get("MaDon"));
    }
    private void credit(Map<String,Object> order,BigDecimal amount){
        var account=db.queryForMap("SELECT TaiKhoanID,SoDuRVT FROM dbo.TaiKhoanKhach WITH(UPDLOCK,HOLDLOCK) WHERE TaiKhoanID=?",order.get("TaiKhoanID"));
        movement(order,account,amount,"REFUND:","Hoàn toàn bộ đơn "+order.get("MaDon"));
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void refundCaptured(Map<String,Object> order,String reference,BigDecimal amount){
        if(amount==null||amount.signum()<=0||reference==null||!reference.matches("RVT[A-F0-9]{32}"))throw new IllegalArgumentException("Khoản hoàn VNPay không hợp lệ.");
        if(order.get("TaiKhoanID")==null){
            db.update("INSERT dbo.HoanVNPayChoNhan(MaThamChieu,DonDatVeID,SoTien) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.HoanVNPayChoNhan WHERE MaThamChieu=?)",reference,order.get("DonDatVeID"),amount,reference);
        }else capturedCredit(order,reference,amount);
    }
    private void capturedCredit(Map<String,Object> order,String reference,BigDecimal amount){
        var account=db.queryForMap("SELECT TaiKhoanID,SoDuRVT FROM dbo.TaiKhoanKhach WITH(UPDLOCK,HOLDLOCK) WHERE TaiKhoanID=?",order.get("TaiKhoanID"));
        movementKey(order,account,amount,"VNPAY_REFUND:"+reference,"Hoàn giao dịch VNPay "+reference+" · đơn "+order.get("MaDon"));
    }
    private void movement(Map<String,Object> order,Map<String,Object> account,BigDecimal amount,String prefix,String text){
        String key=prefix+order.get("DonDatVeID");
        movementKey(order,account,amount,key,text);
    }
    private void movementKey(Map<String,Object> order,Map<String,Object> account,BigDecimal amount,String key,String text){
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.GiaoDichRVT WHERE MaSuKien=?",Integer.class,key)>0)return;
        BigDecimal after=((BigDecimal)account.get("SoDuRVT")).add(amount);
        if(after.signum()<0)throw new IllegalArgumentException("Số dư RVT không đủ. Chọn VNPay hoặc dùng số dư sau khi được hoàn vé.");
        db.update("UPDATE dbo.TaiKhoanKhach SET SoDuRVT=? WHERE TaiKhoanID=?",after,account.get("TaiKhoanID"));
        db.update("INSERT dbo.GiaoDichRVT(TaiKhoanID,DonDatVeID,MaSuKien,SoTien,SoDuSau,NoiDung) VALUES(?,?,?,?,?,?)",account.get("TaiKhoanID"),order.get("DonDatVeID"),key,amount,after,text);
    }
    @Transactional(rollbackFor = Exception.class) public void claimLegacy(String code,String phone,String username){
        var info=db.queryForList("SELECT o.* FROM dbo.DonDatVe o JOIN dbo.KhachHang k ON k.KhachHangID=o.KhachHangID WHERE o.MaDon=? AND k.SoDienThoai=?",code,phone);
        if(info.isEmpty())throw new IllegalArgumentException("Mã đơn hoặc số điện thoại không đúng.");
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH(UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",info.getFirst().get("ChuyenDiID"));
        var order=db.queryForMap("SELECT * FROM dbo.DonDatVe WITH(UPDLOCK,HOLDLOCK) WHERE MaDon=?",code);
        var account=db.queryForMap("SELECT TaiKhoanID,SoDienThoai FROM dbo.TaiKhoanKhach WITH(UPDLOCK,HOLDLOCK) WHERE TenDangNhap=? AND BiKhoa=0",username);
        if(!phone.equals(account.get("SoDienThoai")))throw new IllegalArgumentException("Số điện thoại tài khoản phải trùng số điện thoại người đặt đơn cũ.");
        if(order.get("TaiKhoanID")!=null&&!order.get("TaiKhoanID").equals(account.get("TaiKhoanID")))throw new IllegalArgumentException("Đơn đã thuộc tài khoản khác.");
        db.update("UPDATE dbo.DonDatVe SET TaiKhoanID=? WHERE DonDatVeID=?",account.get("TaiKhoanID"),order.get("DonDatVeID"));order.put("TaiKhoanID",account.get("TaiKhoanID"));
        var pending=db.queryForList("SELECT SoTien FROM dbo.HoanRVTChoNhan WHERE DonDatVeID=? AND DaNhan=0",order.get("DonDatVeID"));
        if(!pending.isEmpty()){credit(order,(BigDecimal)pending.getFirst().get("SoTien"));db.update("UPDATE dbo.HoanRVTChoNhan SET DaNhan=1 WHERE DonDatVeID=?",order.get("DonDatVeID"));}
        for(var row:db.queryForList("SELECT MaThamChieu,SoTien FROM dbo.HoanVNPayChoNhan WHERE DonDatVeID=? AND DaNhan=0 ORDER BY MaThamChieu",order.get("DonDatVeID"))){
            capturedCredit(order,(String)row.get("MaThamChieu"),(BigDecimal)row.get("SoTien"));
            db.update("UPDATE dbo.HoanVNPayChoNhan SET DaNhan=1 WHERE MaThamChieu=?",row.get("MaThamChieu"));
        }
    }
}
