package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hóa đơn điện tử cho đơn đã thanh toán (bản thể hiện mô phỏng của đồ án, không gửi lên cơ quan thuế).
 * Giá vé đã gồm thuế GTGT; tiền trước thuế = tổng / (1 + thuế suất).
 */
@Service
public class InvoiceService {
    public static final String SERIES="1C26TRV";
    private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final JdbcTemplate db;
    private final TicketService tickets;
    private final BigDecimal vatRate;
    private final SecureRandom random=new SecureRandom();
    public InvoiceService(JdbcTemplate db,TicketService tickets,@Value("${RVT_INVOICE_VAT_RATE:10}") BigDecimal vatRate){
        this.db=db;this.tickets=tickets;this.vatRate=vatRate;
    }
    public record Buyer(String kind,String name,String taxCode,String address,String email){}

    public Optional<Map<String,Object>> forOrder(Object orderId){
        var rows=db.queryForList("SELECT * FROM dbo.HoaDon WHERE DonDatVeID=?",orderId);
        return rows.isEmpty()?Optional.empty():Optional.of(rows.getFirst());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> issue(String code,String phone,String username,Buyer buyer){
        tickets.ownerPhone(code,username);
        var info=tickets.order(code,phone);
        db.queryForList("SELECT DonDatVeID FROM dbo.DonDatVe WITH(UPDLOCK,HOLDLOCK) WHERE DonDatVeID=?",info.get("DonDatVeID"));
        var order=tickets.order(code,phone);
        if(!Set.of("DA_THANH_TOAN","DA_SU_DUNG").contains((String)order.get("TrangThai")))throw new IllegalArgumentException("Chỉ xuất hóa đơn cho đơn đã thanh toán.");
        var existing=forOrder(order.get("DonDatVeID"));
        if(existing.isPresent())return existing.get();
        validate(buyer);
        BigDecimal total=(BigDecimal)order.get("TienSeHoan");
        if(total.signum()<=0)throw new IllegalArgumentException("Đơn không có khoản thanh toán để xuất hóa đơn.");
        BigDecimal before=total.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(100).add(vatRate),0,RoundingMode.HALF_UP);
        String lookup=lookupCode();
        db.update("INSERT dbo.HoaDon(DonDatVeID,KyHieu,MaTraCuu,LoaiNguoiMua,TenNguoiMua,MaSoThue,DiaChi,Email,TienTruocThue,ThueSuat,TienThue,TongThanhToan) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            order.get("DonDatVeID"),SERIES,lookup,buyer.kind(),buyer.name().strip(),blank(buyer.taxCode()),blank(buyer.address()),blank(buyer.email()),before,vatRate,total.subtract(before),total);
        // Số hóa đơn tăng dần theo id, 7 chữ số.
        db.update("UPDATE dbo.HoaDon SET SoHoaDon=RIGHT('0000000'+CAST(HoaDonID AS VARCHAR(10)),7) WHERE DonDatVeID=?",order.get("DonDatVeID"));
        tickets.audit(username,"XUAT_HOA_DON",code);
        return forOrder(order.get("DonDatVeID")).orElseThrow();
    }

    /** Tra cứu công khai bằng mã tra cứu in trên hóa đơn. */
    public Map<String,Object> lookup(String lookupCode){
        String c=lookupCode==null?"":lookupCode.strip().toUpperCase(Locale.ROOT);
        if(!c.matches("[A-Z0-9]{10}"))throw new IllegalArgumentException("Mã tra cứu gồm 10 ký tự chữ và số.");
        var rows=db.queryForList("SELECT h.*,o.MaDon,o.TrangThai AS TrangThaiDon,c.NoiDi,c.NoiDen,c.KhoiHanh,c.LoaiVe,c.DonViVanChuyen,(SELECT COUNT(*) FROM dbo.DonDatVeChiTiet d WHERE d.DonDatVeID=o.DonDatVeID) AS SoVe FROM dbo.HoaDon h JOIN dbo.DonDatVe o ON o.DonDatVeID=h.DonDatVeID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE h.MaTraCuu=?",c);
        if(rows.isEmpty())throw new IllegalArgumentException("Không tìm thấy hóa đơn với mã tra cứu này.");
        return rows.getFirst();
    }

    private void validate(Buyer b){
        if(b==null||!Set.of("CA_NHAN","DON_VI").contains(b.kind()))throw new IllegalArgumentException("Chọn người mua là cá nhân hoặc đơn vị.");
        if(b.name()==null||b.name().isBlank()||b.name().strip().length()>150)throw new IllegalArgumentException("Nhập tên người mua hoặc tên đơn vị, tối đa 150 ký tự.");
        String tax=blank(b.taxCode());
        if("DON_VI".equals(b.kind())&&(tax==null||!tax.matches("[0-9]{10}([0-9]{3})?|[0-9]{10}-[0-9]{3}")))throw new IllegalArgumentException("Mã số thuế đơn vị gồm 10 hoặc 13 chữ số.");
        if(tax!=null&&!tax.matches("[0-9]{10}([0-9]{3})?|[0-9]{10}-[0-9]{3}"))throw new IllegalArgumentException("Mã số thuế gồm 10 hoặc 13 chữ số.");
        if("DON_VI".equals(b.kind())&&blank(b.address())==null)throw new IllegalArgumentException("Nhập địa chỉ đơn vị.");
        if(blank(b.address())!=null&&b.address().strip().length()>250)throw new IllegalArgumentException("Địa chỉ tối đa 250 ký tự.");
        String email=blank(b.email());
        if(email!=null&&(email.length()>254||!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")))throw new IllegalArgumentException("Email nhận hóa đơn không hợp lệ.");
    }
    private String lookupCode(){
        for(int attempt=0;attempt<5;attempt++){
            var sb=new StringBuilder();for(int i=0;i<10;i++)sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            if(db.queryForObject("SELECT COUNT(*) FROM dbo.HoaDon WHERE MaTraCuu=?",Integer.class,sb.toString())==0)return sb.toString();
        }
        throw new IllegalStateException("Không tạo được mã tra cứu hóa đơn.");
    }
    private static String blank(String s){return s==null||s.isBlank()?null:s.strip();}
}
