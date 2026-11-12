package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mã khuyến mãi áp dụng ở bước thanh toán cho đơn đang giữ chỗ.
 * Số tiền giảm được chia cho từng vé và lưu thành dòng ChiTietPhi âm (MaPhi = KHUYEN_MAI),
 * nên tổng tiền vé, thanh toán RVT, VNPay và hoàn tiền đều dùng cùng một con số.
 */
@Service
public class PromotionService {
    /** Mỗi vé sau giảm vẫn phải còn ít nhất 1.000 đồng (ThanhToan.SoTien > 0). */
    private static final BigDecimal MIN_TICKET=BigDecimal.valueOf(1000);
    private final JdbcTemplate db;
    private final TicketService tickets;
    public PromotionService(JdbcTemplate db,TicketService tickets){this.db=db;this.tickets=tickets;}

    public List<Map<String,Object>> active(){
        return db.queryForList("SELECT * FROM dbo.KhuyenMai WHERE HoatDong=1 AND BatDau<=SYSUTCDATETIME() AND KetThuc>SYSUTCDATETIME() ORDER BY MaKhuyenMai");
    }

    @Transactional(rollbackFor = Exception.class)
    public BigDecimal apply(String code,String phone,String username,String promoCode){
        String promo=promoCode==null?"":promoCode.strip().toUpperCase(Locale.ROOT);
        if(!promo.matches("[A-Z0-9]{3,30}"))throw new IllegalArgumentException("Mã khuyến mãi gồm 3 đến 30 chữ hoặc số.");
        var order=lockedHold(code,phone,username);
        if(order.get("MaKhuyenMai")!=null)throw new IllegalArgumentException("Đơn đã áp dụng mã "+order.get("MaKhuyenMai")+". Gỡ mã cũ trước khi dùng mã khác.");
        var rows=db.queryForList("SELECT * FROM dbo.KhuyenMai WITH(UPDLOCK,HOLDLOCK) WHERE MaKhuyenMai=?",promo);
        if(rows.isEmpty()||!Boolean.TRUE.equals(rows.getFirst().get("HoatDong")))throw new IllegalArgumentException("Mã khuyến mãi không tồn tại hoặc đã ngừng.");
        var p=rows.getFirst();var now=tickets.databaseNow();
        if(now.isBefore(((Timestamp)p.get("BatDau")).toLocalDateTime())||!now.isBefore(((Timestamp)p.get("KetThuc")).toLocalDateTime()))throw new IllegalArgumentException("Mã khuyến mãi chưa đến hoặc đã hết thời gian áp dụng.");
        if(p.get("LoaiVe")!=null&&!p.get("LoaiVe").equals(order.get("LoaiVe")))throw new IllegalArgumentException("Mã chỉ áp dụng cho vé "+("XE_KHACH".equals(p.get("LoaiVe"))?"xe khách.":"tàu thuyền."));
        var lines=ticketLines(order);
        if(lines.size()<((Number)p.get("SoVeToiThieu")).intValue())throw new IllegalArgumentException("Mã cần đơn có ít nhất "+p.get("SoVeToiThieu")+" vé.");
        BigDecimal subtotal=(BigDecimal)order.get("TongTien");
        if(subtotal.compareTo((BigDecimal)p.get("DonToiThieu"))<0)throw new IllegalArgumentException("Mã cần đơn từ "+((BigDecimal)p.get("DonToiThieu")).stripTrailingZeros().toPlainString()+" đồng.");
        if(p.get("SoLuotToiDa")!=null&&used(promo,null)>=((Number)p.get("SoLuotToiDa")).intValue())throw new IllegalArgumentException("Mã khuyến mãi đã hết lượt sử dụng.");
        if(Boolean.TRUE.equals(p.get("MoiTaiKhoanMotLan"))&&used(promo,order.get("TaiKhoanID"))>0)throw new IllegalArgumentException("Mỗi tài khoản chỉ dùng mã này một lần.");
        BigDecimal discount=discount(p,subtotal).min(subtotal.subtract(MIN_TICKET.multiply(BigDecimal.valueOf(lines.size()))).max(BigDecimal.ZERO));
        if(discount.signum()<=0)throw new IllegalArgumentException("Đơn không đủ giá trị để áp dụng mã này.");
        // Chia theo tỷ lệ giá từng vé, phần lẻ dồn vào vé cuối.
        BigDecimal left=discount;
        for(int i=0;i<lines.size();i++){
            var t=lines.get(i);BigDecimal amount=(BigDecimal)t.get("SoTien");
            BigDecimal share=i==lines.size()-1?left:discount.multiply(amount).divide(subtotal,0,RoundingMode.DOWN);
            share=share.min(amount.subtract(MIN_TICKET)).max(BigDecimal.ZERO);left=left.subtract(share);
            if(share.signum()>0)db.update("INSERT dbo.ChiTietPhi(DangKyID,MaPhi,TenPhi,SoTien) VALUES(?,'KHUYEN_MAI',?,?)",t.get("DangKyID"),"Giảm giá mã "+promo,share.negate());
        }
        BigDecimal applied=discount.subtract(left);
        db.update("UPDATE dbo.DonDatVe SET MaKhuyenMai=?,TienGiam=?,TongTien=TongTien-? WHERE DonDatVeID=?",promo,applied,applied,order.get("DonDatVeID"));
        return applied;
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(String code,String phone,String username){
        var order=lockedHold(code,phone,username);
        if(order.get("MaKhuyenMai")==null)return;
        db.update("DELETE p FROM dbo.ChiTietPhi p JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=p.DangKyID WHERE d.DonDatVeID=? AND p.MaPhi='KHUYEN_MAI'",order.get("DonDatVeID"));
        db.update("UPDATE dbo.DonDatVe SET TongTien=TongTien+TienGiam,TienGiam=0,MaKhuyenMai=NULL WHERE DonDatVeID=?",order.get("DonDatVeID"));
    }

    /** Lượt dùng = đơn đã thanh toán hoặc đang giữ chỗ còn hạn có gắn mã (tài khoản cụ thể nếu accountId khác null). */
    private int used(String promo,Object accountId){
        return db.queryForObject("SELECT COUNT(*) FROM dbo.DonDatVe WHERE MaKhuyenMai=? AND (TrangThai IN('DA_THANH_TOAN','DA_SU_DUNG') OR (TrangThai='GIU_CHO' AND HanThanhToan>SYSUTCDATETIME()))"
            +(accountId==null?"":" AND TaiKhoanID=?"),Integer.class,accountId==null?new Object[]{promo}:new Object[]{promo,accountId});
    }
    static BigDecimal discount(Map<String,Object> p,BigDecimal subtotal){
        BigDecimal value=(BigDecimal)p.get("GiaTri");
        BigDecimal result="PHAN_TRAM".equals(p.get("KieuGiam"))
            ?subtotal.multiply(value).divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN).divide(MIN_TICKET,0,RoundingMode.DOWN).multiply(MIN_TICKET)
            :value;
        if(p.get("GiamToiDa")!=null)result=result.min((BigDecimal)p.get("GiamToiDa"));
        return result.min(subtotal);
    }
    private List<Map<String,Object>> ticketLines(Map<String,Object> order){
        return db.queryForList("SELECT v.DangKyID,v.GiaVe+ISNULL((SELECT SUM(p.SoTien) FROM dbo.ChiTietPhi p WHERE p.DangKyID=v.DangKyID),0) AS SoTien FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=? ORDER BY v.DangKyID",order.get("DonDatVeID"));
    }
    private Map<String,Object> lockedHold(String code,String phone,String username){
        tickets.ownerPhone(code,username);
        var info=tickets.order(code,phone);
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH(UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",info.get("ChuyenDiID"));
        db.queryForList("SELECT DonDatVeID FROM dbo.DonDatVe WITH(UPDLOCK,HOLDLOCK) WHERE DonDatVeID=?",info.get("DonDatVeID"));
        var order=tickets.order(code,phone);
        if(!"GIU_CHO".equals(order.get("TrangThai")))throw new IllegalArgumentException("Chỉ áp dụng hoặc gỡ mã khi đơn đang giữ chỗ, chưa thanh toán.");
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.GiaoDichVNPay WHERE DonDatVeID=? AND TrangThai='PENDING' AND HetHanLuc>SYSUTCDATETIME()",Integer.class,order.get("DonDatVeID"))>0)
            throw new IllegalArgumentException("Đơn đang chờ kết quả VNPay, chưa thể đổi mã khuyến mãi.");
        return order;
    }
}
