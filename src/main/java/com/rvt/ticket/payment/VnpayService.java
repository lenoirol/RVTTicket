package com.rvt.ticket.payment;

import com.rvt.ticket.booking.TicketService;
import com.rvt.ticket.booking.NotificationService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VnpayService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final JdbcTemplate db;
    private final TicketService tickets;
    private final NotificationService notifications;
    private final VnpaySettings settings;
    private final com.rvt.ticket.booking.WalletService wallet;
    public VnpayService(JdbcTemplate db,TicketService tickets,NotificationService notifications,VnpaySettings settings,com.rvt.ticket.booking.WalletService wallet){
        this.db=db;this.tickets=tickets;this.notifications=notifications;this.settings=settings;this.wallet=wallet;
    }
    public boolean enabled(){return settings.enabled();}
    private Map<String,Object> lockOrder(String code) {
        var ids=db.queryForList("SELECT DonDatVeID,ChuyenDiID FROM dbo.DonDatVe WHERE MaDon=?",code);
        if(ids.isEmpty())throw new IllegalArgumentException("Không tìm thấy đơn vé.");
        // Shared lock ordering: trip -> order -> gateway attempt -> account (if a balance operation follows).
        db.queryForList("SELECT ChuyenDiID FROM dbo.ChuyenDi WITH(UPDLOCK,HOLDLOCK) WHERE ChuyenDiID=?",ids.getFirst().get("ChuyenDiID"));
        return db.queryForMap("SELECT o.*,c.TrangThai AS TrangThaiChuyen,c.KhoiHanh FROM dbo.DonDatVe o WITH(UPDLOCK,HOLDLOCK) JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE o.MaDon=?",code);
    }
    @Transactional(rollbackFor = Exception.class)
    public String start(String code,String username,String ip) {
        return start(code,username,ip,null);
    }
    @Transactional(rollbackFor = Exception.class)
    public String start(String code,String username,String ip,String origin) {
        if(!enabled())throw new IllegalArgumentException("VNPay chưa sẵn sàng. Vui lòng chọn số dư RVT hoặc thử lại sau.");
        String returnUrl=settings.returnUrl(origin);
        var order=lockOrder(code);tickets.ownerPhone(code,username);
        var now=tickets.databaseNow().withNano(0);
        var deadline=((Timestamp)order.get("HanThanhToan")).toLocalDateTime();
        if(!"GIU_CHO".equals(order.get("TrangThai"))||!deadline.isAfter(now.plusSeconds(60))
            ||Set.of("DA_HUY","HOAN_THANH","DA_KHOI_HANH").contains(order.get("TrangThaiChuyen")))
            throw new IllegalArgumentException("Đơn không còn đủ thời gian thanh toán. Vui lòng kiểm tra trạng thái đơn.");
        var amount=(BigDecimal)order.get("TongTien");minorAmount(amount);
        var active=db.queryForList("SELECT * FROM dbo.GiaoDichVNPay WITH(UPDLOCK,HOLDLOCK) WHERE DonDatVeID=? AND TrangThai='PENDING' AND HetHanLuc>SYSUTCDATETIME() ORDER BY NgayTao DESC",order.get("DonDatVeID"));
        if(!active.isEmpty()) {
            var a=active.getFirst();
            if(!settings.tmnCode().equals(a.get("TmnCode")))throw new IllegalArgumentException("Cấu hình merchant đã đổi. Chờ giao dịch cũ hết hạn trước khi thử lại.");
            return paymentUrl(a,returnUrl);
        }
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.GiaoDichVNPay WHERE DonDatVeID=?",Integer.class,order.get("DonDatVeID"))>=5)
            throw new IllegalArgumentException("Đơn đã thử thanh toán nhiều lần. Vui lòng liên hệ hỗ trợ.");
        if(ip==null||!ip.matches("[0-9a-fA-F:.]{1,45}"))throw new IllegalArgumentException("Không xác định được địa chỉ kết nối thanh toán.");
        String ref="RVT"+UUID.randomUUID().toString().replace("-","").toUpperCase();
        var expiry=deadline.minusSeconds(30); // Leave a small IPN delivery buffer; never extend the seat hold.
        db.update("INSERT dbo.GiaoDichVNPay(MaThamChieu,DonDatVeID,SoTien,TmnCode,NgayTao,HetHanLuc,DiaChiIP) VALUES(?,?,?,?,?,?,?)",
            ref,order.get("DonDatVeID"),amount,settings.tmnCode(),Timestamp.valueOf(now),Timestamp.valueOf(expiry),ip);
        return paymentUrl(db.queryForMap("SELECT * FROM dbo.GiaoDichVNPay WHERE MaThamChieu=?",ref),returnUrl);
    }
    private String paymentUrl(Map<String,Object> a,String returnUrl) {
        var fields=new TreeMap<String,String>();
        fields.put("vnp_Version","2.1.0");fields.put("vnp_Command","pay");fields.put("vnp_TmnCode",settings.tmnCode());
        fields.put("vnp_Amount",minorAmount((BigDecimal)a.get("SoTien")));fields.put("vnp_CurrCode","VND");
        fields.put("vnp_TxnRef",(String)a.get("MaThamChieu"));fields.put("vnp_OrderInfo","Thanh toan ve RVT "+a.get("MaThamChieu"));
        fields.put("vnp_OrderType","other");fields.put("vnp_Locale","vn");fields.put("vnp_ReturnUrl",returnUrl);
        fields.put("vnp_IpAddr",(String)a.get("DiaChiIP"));
        fields.put("vnp_CreateDate",date((Timestamp)a.get("NgayTao")));fields.put("vnp_ExpireDate",date((Timestamp)a.get("HetHanLuc")));
        String query=VnpayCodec.query(fields);
        return VnpaySettings.PAY_URL+"?"+query+"&vnp_SecureHash="+VnpayCodec.hash(settings.secret(),query);
    }
    private static String date(Timestamp timestamp){return timestamp.toLocalDateTime().plusHours(7).format(DATE);}
    public static String minorAmount(BigDecimal amount) {
        if(amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>0)
            throw new IllegalArgumentException("Số tiền VNPay phải là số đồng nguyên, lớn hơn 0.");
        var result=amount.movePointRight(2).toBigIntegerExact().toString();
        if(result.length()>12)throw new IllegalArgumentException("Số tiền vượt giới hạn VNPay.");
        return result;
    }
    public boolean valid(Map<String,String> fields){return enabled()&&settings.tmnCode().equals(fields.get("vnp_TmnCode"))&&VnpayCodec.verify(settings.secret(),fields);}
    private static Map<String,String> reply(String code,String message){return Map.of("RspCode",code,"Message",message);}
    @Transactional(rollbackFor = Exception.class)
    public Map<String,String> ipn(Map<String,String> fields) {
        return settle(fields,"IPN");
    }
    @Transactional(rollbackFor = Exception.class)
    public Map<String,String> verifiedQuery(Map<String,String> fields){return settle(fields,"QUERYDR");}
    public boolean returnSettles(){return settings.returnSettles();}
    /**
     * Only when rvt.vnpay.return-settles=true: the browser Return carries the same HMAC-signed fields as IPN,
     * so it goes through the identical checks (signature, merchant, reference, amount, both result codes,
     * unused transaction number, seat hold still valid) and the same locks; a repeated Return or a later IPN is a no-op.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String,String> signedReturn(Map<String,String> fields){
        if(!returnSettles())return reply("97","Return settlement disabled");
        return settle(fields,"RETURN");
    }
    private Map<String,String> settle(Map<String,String> fields,String source) {
        if(!valid(fields))return reply("97","Invalid signature");
        String ref=fields.getOrDefault("vnp_TxnRef","");
        var refs=db.queryForList("SELECT o.MaDon FROM dbo.GiaoDichVNPay a JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID WHERE a.MaThamChieu=?",String.class,ref);
        if(refs.isEmpty())return reply("01","Order not found");
        var order=lockOrder(refs.getFirst());
        var a=db.queryForMap("SELECT * FROM dbo.GiaoDichVNPay WITH(UPDLOCK,HOLDLOCK) WHERE MaThamChieu=?",ref);
        if(!settings.tmnCode().equals(a.get("TmnCode")))return reply("97","Invalid merchant");
        String rawAmount=fields.getOrDefault("vnp_Amount","");
        if(!rawAmount.matches("[0-9]{1,12}")||new BigDecimal(rawAmount).compareTo(((BigDecimal)a.get("SoTien")).movePointRight(2))!=0)
            return reply("04","Invalid amount");
        if(!"PENDING".equals(a.get("TrangThai"))){
            if("IPN".equals(source))db.update("UPDATE dbo.GiaoDichVNPay SET NhanIPNLuc=COALESCE(NhanIPNLuc,SYSUTCDATETIME()) WHERE MaThamChieu=?",ref);
            return reply("02","Order already confirmed");
        }
        String response=fields.getOrDefault("vnp_ResponseCode","");
        String status=fields.getOrDefault("vnp_TransactionStatus","");
        String bank=fields.getOrDefault("vnp_BankCode","");
        if(!response.matches("[0-9]{2}")||!status.matches("[0-9]{2}")||!bank.matches("[A-Za-z0-9]{1,20}"))
            return reply("99","Invalid response fields");
        if(!Set.of("00","02").contains(status)||"07".equals(response)){
            boolean processing="01".equals(status)&&!"07".equals(response);
            String note=processing?"VNPay chưa hoàn tất giao dịch; tiếp tục đối soát, không thanh toán lại.":"VNPay trả trạng thái đảo / hoàn / nghi vấn hoặc chưa xác định. Cần đối chiếu Merchant; chưa cấp vé hoặc hoàn RVT.";
            db.update("UPDATE dbo.GiaoDichVNPay SET MaKetQua=?,TrangThaiVNPay=?,MaNganHang=?,CanDoiSoat=?,LoiTruyVan=?,NhanIPNLuc=CASE WHEN ?='IPN' THEN SYSUTCDATETIME() ELSE NhanIPNLuc END WHERE MaThamChieu=?",response,status,bank,!processing,note,source,ref);
            return reply("00","Recorded pending reconciliation");
        }
        if(!"00".equals(response)||!"00".equals(status)) {
            finish(ref,"FAILED",null,bank,response,status,"Giao dịch không thành công tại VNPay.",source);
            return reply("00","Confirm Success");
        }
        String receipt=fields.getOrDefault("vnp_TransactionNo","");
        if(!receipt.matches("[1-9][0-9]{0,14}"))return reply("99","Invalid transaction number");
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.GiaoDichVNPay WHERE MaVNPay=?",Integer.class,receipt)>0)
            return reply("99","Transaction number already used");
        var now=tickets.databaseNow();
        var rows=db.queryForList("SELECT v.*,COALESCE((SELECT SUM(p.SoTien) FROM dbo.ChiTietPhi p WHERE p.DangKyID=v.DangKyID),0) AS TongPhi FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID WHERE d.DonDatVeID=?",order.get("DonDatVeID"));
        var total=rows.stream().map(t->((BigDecimal)t.get("GiaVe")).add((BigDecimal)t.get("TongPhi"))).reduce(BigDecimal.ZERO,BigDecimal::add);
        boolean available="GIU_CHO".equals(order.get("TrangThai"))
            &&((Timestamp)order.get("HanThanhToan")).toLocalDateTime().isAfter(now)
            &&((Timestamp)order.get("KhoiHanh")).toLocalDateTime().isAfter(now)
            &&!Set.of("DA_HUY","HOAN_THANH","DA_KHOI_HANH").contains(order.get("TrangThaiChuyen"))
            &&!rows.isEmpty()&&rows.stream().allMatch(t->"GIU_CHO".equals(t.get("TrangThai"))&&Boolean.TRUE.equals(t.get("DangChiemCho")))
            &&total.compareTo((BigDecimal)a.get("SoTien"))==0&&total.compareTo((BigDecimal)order.get("TongTien"))==0;
        if(!available) {
            String note="VNPay báo thành công nhưng đơn/chỗ không còn đủ điều kiện xác nhận. Cần đối chiếu, không tự cấp vé hay hoàn số dư.";
            finish(ref,"REVIEW",receipt,bank,response,status,note,source);
            db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES('VNPAY','VNPAY_REVIEW',?,?)","Đơn "+order.get("MaDon"),note);
            notifications.order(order,"VNPAY_REVIEW","Cần kiểm tra thanh toán","Đã nhận kết quả VNPay cho đơn "+order.get("MaDon")+". Đơn chưa được xác nhận; vui lòng liên hệ hỗ trợ với mã giao dịch "+ref+".");
            return reply("00","Recorded for review");
        }
        for(var t:rows) {
            var share=((BigDecimal)t.get("GiaVe")).add((BigDecimal)t.get("TongPhi"));
            // A zero-priced passenger can accompany a paid passenger; do not invent a positive receipt.
            if(share.signum()>0)db.update("INSERT dbo.ThanhToan(DangKyID,MaGiaoDich,SoTien,PhuongThuc,TrangThai,ThoiGian) VALUES(?,?,?,'VNPAY','THANH_CONG',SYSUTCDATETIME())",
                t.get("DangKyID"),"VNP-"+receipt+"-"+t.get("DangKyID"),share);
            db.update("UPDATE dbo.DangKyVe SET TrangThai='DA_THANH_TOAN' WHERE DangKyID=?",t.get("DangKyID"));
        }
        db.update("UPDATE dbo.DonDatVe SET TrangThai='DA_THANH_TOAN' WHERE DonDatVeID=?",order.get("DonDatVeID"));
        finish(ref,"SUCCESS",receipt,bank,response,status,"Vé đã được xác nhận bằng kết quả máy chủ VNPay đã kiểm tra chữ ký.",source);
        tickets.audit("VNPAY","VNPAY_PAY","Đơn "+order.get("MaDon"));
        notifications.order(order,"PAY","Thanh toán thành công","VNPay đã xác nhận thanh toán. Vé của bạn đã sẵn sàng.");
        return reply("00","Confirm Success");
    }
    private void finish(String ref,String state,String receipt,String bank,String response,String status,String note,String source) {
        db.update("UPDATE dbo.GiaoDichVNPay SET TrangThai=?,MaVNPay=?,MaNganHang=?,MaKetQua=?,TrangThaiVNPay=?,KetQuaNhanLuc=SYSUTCDATETIME(),NguonXacNhan=?,NhanIPNLuc=CASE WHEN ?='IPN' THEN SYSUTCDATETIME() ELSE NhanIPNLuc END,CanDoiSoat=0,LoiTruyVan=NULL,TruyVanTiepTheoLuc=NULL,GhiChu=? WHERE MaThamChieu=?",
            state,receipt,bank,response,status,source,source,note,ref);
    }
    @Transactional(rollbackFor = Exception.class) public void resolveReview(String reference,String reason,String actor){
        if(reason==null||reason.strip().length()<10||reason.length()>500)throw new IllegalArgumentException("Ghi kết quả đối chiếu và lý do hoàn (10–500 ký tự).");
        var codes=db.queryForList("SELECT o.MaDon FROM dbo.GiaoDichVNPay a JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID WHERE a.MaThamChieu=?",String.class,reference);
        if(codes.isEmpty())throw new IllegalArgumentException("Không tìm thấy giao dịch.");
        var order=lockOrder(codes.getFirst());
        var a=db.queryForMap("SELECT * FROM dbo.GiaoDichVNPay WITH(UPDLOCK,HOLDLOCK) WHERE MaThamChieu=?",reference);
        if("HOAN_RVT".equals(a.get("XuLyDoiSoat")))return;
        if(!"REVIEW".equals(a.get("TrangThai"))||!"00".equals(a.get("MaKetQua"))||!"00".equals(a.get("TrangThaiVNPay"))||a.get("MaVNPay")==null||a.get("NguonXacNhan")==null)
            throw new IllegalArgumentException("Chỉ hoàn giao dịch đã được máy chủ VNPay xác minh thành công nhưng chưa cấp vé.");
        wallet.refundCaptured(order,reference,(BigDecimal)a.get("SoTien"));
        db.update("UPDATE dbo.GiaoDichVNPay SET XuLyDoiSoat='HOAN_RVT',XuLyLuc=SYSUTCDATETIME(),NguoiXuLy=?,GhiChuXuLy=? WHERE MaThamChieu=?",actor,reason.strip(),reference);
        db.update("INSERT dbo.NhatKyThaoTac(NguoiThucHien,HanhDong,DoiTuong,NoiDung) VALUES(?,'VNPAY_HOAN_RVT',?,?)",actor,reference,reason.strip());
        notifications.order(order,"VNPAY_REFUND_"+reference,"Đã xử lý giao dịch VNPay","Giao dịch "+reference+" đã được hoàn vào số dư RVT hoặc lưu chờ chủ đơn xác nhận tài khoản. Trạng thái vé không thay đổi.");
    }
    /** Reads the attempt state for the Return page. Settling from Return happens only via signedReturn when explicitly enabled. */
    public Map<String,Object> result(Map<String,String> fields) {
        if(!valid(fields))return Map.of("state","INVALID");
        var rows=db.queryForList("SELECT a.*,o.MaDon,CASE WHEN EXISTS(SELECT 1 FROM dbo.HoanVNPayChoNhan h WHERE h.MaThamChieu=a.MaThamChieu AND h.DaNhan=0) THEN 1 ELSE 0 END AS ChoNhanRVT FROM dbo.GiaoDichVNPay a JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID WHERE a.MaThamChieu=? AND a.TmnCode=?",fields.get("vnp_TxnRef"),settings.tmnCode());
        if(rows.isEmpty())return Map.of("state","INVALID");
        var a=rows.getFirst();String amount=fields.getOrDefault("vnp_Amount","");
        if(!amount.matches("[0-9]{1,12}")||new BigDecimal(amount).compareTo(((BigDecimal)a.get("SoTien")).movePointRight(2))!=0)return Map.of("state","INVALID");
        // A failed browser redirect is not authoritative either.
        Object state="HOAN_RVT".equals(a.get("XuLyDoiSoat"))?(((Number)a.get("ChoNhanRVT")).intValue()==1?"REFUND_PENDING":"REFUNDED"):a.get("TrangThai");
        return Map.of("state",state,"code",a.get("MaDon"),"reference",a.get("MaThamChieu"));
    }
    public List<Map<String,Object>> attempts(String state,int page) {
        if(!Set.of("","PENDING","SUCCESS","FAILED","REVIEW","UNRESOLVED","REFUNDED").contains(state)||page<1||page>10000)throw new IllegalArgumentException("Bộ lọc không hợp lệ.");
        String filter=switch(state){case "UNRESOLVED"->"(a.TrangThai='REVIEW' AND a.XuLyDoiSoat IS NULL OR a.TrangThai='PENDING' AND a.CanDoiSoat=1)";case "REFUNDED"->"a.XuLyDoiSoat='HOAN_RVT'";case ""->"1=1";default->"a.TrangThai=?";};
        var args=new ArrayList<Object>();if(!state.isBlank()&&!Set.of("UNRESOLVED","REFUNDED").contains(state))args.add(state);args.add((page-1)*20);
        return db.queryForList("SELECT a.*,o.MaDon,o.TrangThai AS TrangThaiDon FROM dbo.GiaoDichVNPay a JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID WHERE "+filter+" ORDER BY CASE WHEN a.TrangThai='REVIEW' AND a.XuLyDoiSoat IS NULL OR a.CanDoiSoat=1 THEN 0 ELSE 1 END,a.NgayTao DESC,a.MaThamChieu OFFSET ? ROWS FETCH NEXT 21 ROWS ONLY",args.toArray());
    }
}
