package com.rvt.ticket.booking;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final JdbcTemplate db;public ReportService(JdbcTemplate db){this.db=db;}
    @Transactional(readOnly=true)
    public Map<String,Object> report(LocalDate start,LocalDate end,String type){
        if(start==null||end==null||end.isBefore(start)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>365||!Set.of("","XE_KHACH","TAU_THUYEN").contains(type))
            throw new IllegalArgumentException("Chọn khoảng ngày hợp lệ, tối đa 366 ngày, và loại vé.");
        var from=Timestamp.valueOf(start.atStartOfDay().minusHours(7));var to=Timestamp.valueOf(end.plusDays(1).atStartOfDay().minusHours(7));
        var rows=db.queryForList("""
            SELECT c.LoaiVe,p.SoTien,p.PhuongThuc,p.MaGiaoDich,p.TrangThai,p.ThoiGian,p.HoanLuc,v.DangKyID,v.TrangThai AS TrangThaiVe
            FROM dbo.ThanhToan p JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=v.ChuyenDiID
            WHERE p.TrangThai IN('THANH_CONG','DA_HOAN') AND (?='' OR c.LoaiVe=?)
             AND ((p.ThoiGian>=? AND p.ThoiGian<?) OR (p.HoanLuc>=? AND p.HoanLuc<?))
            """,type,type,from,to,from,to);
        var daily=new LinkedHashMap<LocalDate,Map<String,Object>>();
        for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1))daily.put(d,bucket(d.toString()));
        var byType=new LinkedHashMap<String,Map<String,Object>>();
        byType.put("XE_KHACH",bucket("Xe khách"));byType.put("TAU_THUYEN",bucket("Tàu / thuyền"));
        var uniqueType=new HashMap<String,Set<Object>>();var uniqueDaily=new HashMap<LocalDate,Set<Object>>();
        var methods=new LinkedHashMap<String,Map<String,Object>>();
        for(var row:rows){
            var paid=((Timestamp)row.get("ThoiGian")).toLocalDateTime().plusHours(7).toLocalDate();
            var refunded=row.get("HoanLuc") instanceof Timestamp t?t.toLocalDateTime().plusHours(7).toLocalDate():null;
            var b=byType.get(row.get("LoaiVe"));BigDecimal amount=(BigDecimal)row.get("SoTien");
            String method=Objects.toString(row.get("MaGiaoDich"),"").startsWith("DEMO-")?"LEGACY_TEST":(String)row.get("PhuongThuc");
            String label=switch(method){case "LEGACY_TEST"->"Giao dịch thử cũ";case "VNPAY"->"VNPay";case "SO_DU_RVT"->"Số dư RVT";case "TIEN_MAT"->"Tiền mặt";case "CHUYEN_KHOAN"->"Chuyển khoản";case "THE"->"Thẻ";default->method;};
            var methodBucket=methods.computeIfAbsent(method,k->bucket(label));
            if(daily.containsKey(paid)){
                add(methodBucket,"gross",amount);
                add(b,"gross",amount);add(daily.get(paid),"gross",amount);
                uniqueType.computeIfAbsent((String)row.get("LoaiVe"),k->new HashSet<>()).add(row.get("DangKyID"));
                uniqueDaily.computeIfAbsent(paid,k->new HashSet<>()).add(row.get("DangKyID"));
            }
            if("DA_HOAN".equals(row.get("TrangThai"))&&refunded!=null&&daily.containsKey(refunded)){
                add(methodBucket,"refund",amount);
                add(b,"refund",amount);add(daily.get(refunded),"refund",amount);
            }
        }
        byType.forEach((key,b)->b.put("tickets",uniqueType.getOrDefault(key,Set.of()).size()));
        daily.forEach((key,b)->b.put("tickets",uniqueDaily.getOrDefault(key,Set.of()).size()));
        var occupancy=db.queryForList("""
            SELECT c.ChuyenDiID,c.LoaiVe,c.PhuongTienID,(SELECT COUNT(*) FROM dbo.ChoNgoi s WHERE s.ChuyenDiID=c.ChuyenDiID) AS TongSoCho,
              (SELECT COUNT(DISTINCT v.MaCho) FROM dbo.DangKyVe v WHERE v.ChuyenDiID=c.ChuyenDiID AND v.TrangThai IN('DA_THANH_TOAN','DA_SU_DUNG')) AS DaBan
            FROM dbo.ChuyenDi c WHERE c.KhoiHanh>=? AND c.KhoiHanh<? AND c.TrangThai<>'DA_HUY' AND (?='' OR c.LoaiVe=?)
            """,from,to,type,type);
        for(var row:occupancy){
            var b=byType.get(row.get("LoaiVe"));
            if(row.get("PhuongTienID")==null){b.put("unassigned",(int)b.get("unassigned")+1);continue;}
            b.put("capacity",(int)b.get("capacity")+((Number)row.get("TongSoCho")).intValue());b.put("sold",(int)b.get("sold")+((Number)row.get("DaBan")).intValue());b.put("trips",(int)b.get("trips")+1);
        }
        var total=bucket("Tất cả");for(var b:byType.values()){
            for(String key:List.of("gross","refund"))add(total,key,(BigDecimal)b.get(key));
            for(String key:List.of("tickets","capacity","sold","trips","unassigned"))total.put(key,(int)total.get(key)+(int)b.get(key));
        }
        var visible=byType.entrySet().stream().filter(e->type.isBlank()||e.getKey().equals(type)).map(Map.Entry::getValue).toList();
        for(var b:byType.values())finish(b);finish(total);daily.values().forEach(this::finish);
        BigDecimal maximum=daily.values().stream().map(b->(BigDecimal)b.get("gross")).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        for(var b:daily.values())b.put("bar",maximum.signum()==0?0:((BigDecimal)b.get("gross")).multiply(BigDecimal.valueOf(100)).divide(maximum,0,java.math.RoundingMode.HALF_UP).intValue());
        int unknown=db.queryForObject("SELECT COUNT(*) FROM dbo.ThanhToan p JOIN dbo.DangKyVe v ON v.DangKyID=p.DangKyID WHERE p.TrangThai='DA_HOAN' AND p.HoanLuc IS NULL AND (?='' OR v.LoaiVe=?)",Integer.class,type,type);
        methods.values().forEach(this::finish);
        var money=new LinkedHashMap<String,Object>();
        money.put("walletCredits",db.queryForObject("SELECT COALESCE(SUM(CASE WHEN g.SoTien>0 THEN g.SoTien ELSE 0 END),0) FROM dbo.GiaoDichRVT g JOIN dbo.DonDatVe o ON o.DonDatVeID=g.DonDatVeID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE g.NgayTao>=? AND g.NgayTao<? AND (?='' OR c.LoaiVe=?)",BigDecimal.class,from,to,type,type));
        money.put("walletDebits",db.queryForObject("SELECT COALESCE(SUM(CASE WHEN g.SoTien<0 THEN -g.SoTien ELSE 0 END),0) FROM dbo.GiaoDichRVT g JOIN dbo.DonDatVe o ON o.DonDatVeID=g.DonDatVeID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE g.NgayTao>=? AND g.NgayTao<? AND (?='' OR c.LoaiVe=?)",BigDecimal.class,from,to,type,type));
        money.put("walletOutstanding",db.queryForObject("SELECT COALESCE(SUM(SoDuRVT),0) FROM dbo.TaiKhoanKhach",BigDecimal.class));
        money.put("unclaimedRefunds",db.queryForObject("SELECT (SELECT COALESCE(SUM(SoTien),0) FROM dbo.HoanRVTChoNhan WHERE DaNhan=0)+(SELECT COALESCE(SUM(SoTien),0) FROM dbo.HoanVNPayChoNhan WHERE DaNhan=0)",BigDecimal.class));
        var verified=db.queryForMap("SELECT COALESCE(SUM(a.SoTien),0) AS amount,COUNT(*) AS transactions FROM dbo.GiaoDichVNPay a JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE a.TrangThai IN('SUCCESS','REVIEW') AND COALESCE(a.KetQuaNhanLuc,a.NhanIPNLuc)>=? AND COALESCE(a.KetQuaNhanLuc,a.NhanIPNLuc)<? AND (?='' OR c.LoaiVe=?)",from,to,type,type);
        money.put("vnpayVerified",verified.get("amount"));money.put("vnpayTransactions",verified.get("transactions"));
        money.put("vnpayResolvedRvt",db.queryForObject("SELECT COALESCE(SUM(a.SoTien),0) FROM dbo.GiaoDichVNPay a JOIN dbo.DonDatVe o ON o.DonDatVeID=a.DonDatVeID JOIN dbo.ChuyenDi c ON c.ChuyenDiID=o.ChuyenDiID WHERE a.XuLyDoiSoat='HOAN_RVT' AND a.XuLyLuc>=? AND a.XuLyLuc<? AND (?='' OR c.LoaiVe=?)",BigDecimal.class,from,to,type,type));
        money.put("exceptions",db.queryForObject("SELECT COUNT(*) FROM dbo.GiaoDichVNPay WHERE TrangThai='REVIEW' AND XuLyDoiSoat IS NULL OR TrangThai='PENDING' AND CanDoiSoat=1",Integer.class));
        return Map.of("summary",total,"byType",visible,"daily",daily.values(),"unknownRefundDates",unknown,"byMethod",methods.values(),"money",money);
    }
    private Map<String,Object> bucket(String label){var b=new LinkedHashMap<String,Object>();b.put("label",label);b.put("gross",BigDecimal.ZERO);b.put("refund",BigDecimal.ZERO);for(String k:List.of("tickets","capacity","sold","trips","unassigned"))b.put(k,0);return b;}
    private void add(Map<String,Object> b,String key,BigDecimal value){b.put(key,((BigDecimal)b.get(key)).add(value));}
    private void finish(Map<String,Object> b){b.put("net",((BigDecimal)b.get("gross")).subtract((BigDecimal)b.get("refund")));int capacity=(int)b.get("capacity");b.put("occupancy",capacity==0?BigDecimal.ZERO:BigDecimal.valueOf((int)b.get("sold")*100L).divide(BigDecimal.valueOf(capacity),1,java.math.RoundingMode.HALF_UP));}
}
