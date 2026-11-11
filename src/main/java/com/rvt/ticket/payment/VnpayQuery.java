package com.rvt.ticket.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Server-to-server reconciliation. Browser Return is never treated as payment confirmation. */
@Component
@ConditionalOnProperty(name="rvt.vnpay.query.enabled",havingValue="true")
public class VnpayQuery {
    static final String ENDPOINT="https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    static final List<String> RESPONSE_FIELDS=List.of("vnp_ResponseId","vnp_Command","vnp_ResponseCode","vnp_Message","vnp_TmnCode","vnp_TxnRef","vnp_Amount","vnp_BankCode","vnp_PayDate","vnp_TransactionNo","vnp_TransactionType","vnp_TransactionStatus","vnp_OrderInfo","vnp_PromotionCode","vnp_PromotionAmount");
    private final JdbcTemplate db;
    private final VnpayService gateway;
    private final VnpaySettings settings;
    private final ObjectMapper json=new ObjectMapper();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    public VnpayQuery(JdbcTemplate db,VnpayService gateway,VnpaySettings settings){this.db=db;this.gateway=gateway;this.settings=settings;}
    static String joined(Map<String,String> fields,List<String> names){return String.join("|",names.stream().map(n->fields.getOrDefault(n,"")).toList());}
    public static Map<String,String> verifiedPayment(String secret,String merchant,String reference,String amount,Map<String,String> response) {
        String signature=response.getOrDefault("vnp_SecureHash","");
        if(!signature.matches("[a-fA-F0-9]{128}")||!MessageDigest.isEqual(HexFormat.of().parseHex(signature),HexFormat.of().parseHex(VnpayCodec.hash(secret,joined(response,RESPONSE_FIELDS)))))
            throw new IllegalArgumentException("Không xác minh được chữ ký phản hồi truy vấn VNPay.");
        if(!"00".equals(response.get("vnp_ResponseCode")))return Map.of();
        if(!merchant.equals(response.get("vnp_TmnCode"))||!reference.equals(response.get("vnp_TxnRef"))
            ||!amount.equals(response.get("vnp_Amount"))||!"querydr".equals(response.get("vnp_Command"))
            ||!"01".equals(response.get("vnp_TransactionType"))||response.getOrDefault("vnp_ResponseId","").isBlank())
            throw new IllegalArgumentException("Phản hồi truy vấn VNPay không khớp giao dịch.");
        String status=response.getOrDefault("vnp_TransactionStatus","");
        // Processing, uncompleted, reversal/refund and suspect states are not a captured payment.
        if(!Set.of("00","02").contains(status))return Map.of();
        var payment=new HashMap<String,String>();
        for(String name:List.of("vnp_TmnCode","vnp_TxnRef","vnp_Amount","vnp_BankCode","vnp_TransactionNo","vnp_TransactionStatus"))
            payment.put(name,response.getOrDefault(name,""));
        payment.put("vnp_ResponseCode","00".equals(status)?"00":"99");
        // Adapt an independently verified query response to the same transactional settlement checks as IPN.
        payment.put("vnp_SecureHash",VnpayCodec.hash(secret,VnpayCodec.query(payment)));
        return payment;
    }
    @Scheduled(initialDelay=15000,fixedDelay=30000)
    public void reconcile() {
        if(!settings.enabled())return;
        for(var attempt:claimDue()){
            if(Thread.currentThread().isInterrupted())break;
            queryAndReschedule(attempt);
        }
    }
    /** Atomic lease; an old processing request cannot occupy all ten slots on every run. */
    public List<Map<String,Object>> claimDue(){
        db.update("UPDATE dbo.GiaoDichVNPay SET CanDoiSoat=1,LoiTruyVan=N'Quá 24 giờ chưa có kết quả cuối. Cần đối chiếu tại Merchant.',TruyVanTiepTheoLuc=NULL WHERE TrangThai='PENDING' AND NgayTao<=DATEADD(DAY,-1,SYSUTCDATETIME()) AND CanDoiSoat=0 AND TmnCode=?",settings.tmnCode());
        return db.queryForList("WITH due AS (SELECT TOP(10) * FROM dbo.GiaoDichVNPay WITH(UPDLOCK,READPAST,ROWLOCK) WHERE TrangThai='PENDING' AND CanDoiSoat=0 AND TmnCode=? AND NgayTao<DATEADD(SECOND,-30,SYSUTCDATETIME()) AND (TruyVanTiepTheoLuc IS NULL OR TruyVanTiepTheoLuc<=SYSUTCDATETIME()) ORDER BY COALESCE(TruyVanTiepTheoLuc,NgayTao),MaThamChieu) UPDATE due SET SoLanTruyVan=SoLanTruyVan+1,TruyVanGanNhatLuc=SYSUTCDATETIME(),TruyVanTiepTheoLuc=DATEADD(MINUTE,5,SYSUTCDATETIME()) OUTPUT inserted.*",settings.tmnCode());
    }
    public static int retrySeconds(int attempts){return (int)Math.min(1800,30L<<Math.min(6,Math.max(0,attempts-1)));}
    private void queryAndReschedule(Map<String,Object> attempt){
        String failure=null;
        try{query(attempt);}
        catch(InterruptedException e){Thread.currentThread().interrupt();failure="Kết nối bị gián đoạn; sẽ kiểm tra lại.";}
        catch(Exception e){failure="Chưa xác minh được phản hồi VNPay; sẽ kiểm tra lại.";org.slf4j.LoggerFactory.getLogger(VnpayQuery.class).warn("VNPay reconciliation deferred ({})",e.getClass().getSimpleName());}
        int delay=retrySeconds(((Number)attempt.get("SoLanTruyVan")).intValue());
        db.update("UPDATE dbo.GiaoDichVNPay SET TruyVanTiepTheoLuc=DATEADD(SECOND,?,SYSUTCDATETIME()),LoiTruyVan=? WHERE MaThamChieu=? AND TrangThai='PENDING'",delay,failure,attempt.get("MaThamChieu"));
    }
    /** Manual retry is still a signed server query, never an admin 'mark paid' override. */
    public void queryReference(String reference){
        if(!settings.enabled())throw new IllegalArgumentException("VNPay chưa sẵn sàng.");
        var rows=db.queryForList("UPDATE dbo.GiaoDichVNPay SET SoLanTruyVan=SoLanTruyVan+1,TruyVanGanNhatLuc=SYSUTCDATETIME(),TruyVanTiepTheoLuc=DATEADD(MINUTE,5,SYSUTCDATETIME()) OUTPUT inserted.* WHERE MaThamChieu=? AND TmnCode=? AND TrangThai='PENDING' AND (TruyVanGanNhatLuc IS NULL OR TruyVanGanNhatLuc<DATEADD(MINUTE,-5,SYSUTCDATETIME()))",reference,settings.tmnCode());
        if(rows.isEmpty())throw new IllegalArgumentException("Giao dịch đã có kết quả hoặc vừa được truy vấn. Vui lòng kiểm tra lại sau 5 phút.");
        queryAndReschedule(rows.getFirst());
    }
    private void query(Map<String,Object> attempt) throws Exception {
        var fields=new LinkedHashMap<String,String>();
        fields.put("vnp_RequestId",UUID.randomUUID().toString().replace("-",""));
        fields.put("vnp_Version","2.1.0");fields.put("vnp_Command","querydr");fields.put("vnp_TmnCode",settings.tmnCode());
        fields.put("vnp_TxnRef",(String)attempt.get("MaThamChieu"));
        var date=DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        fields.put("vnp_TransactionDate",((Timestamp)attempt.get("NgayTao")).toLocalDateTime().plusHours(7).format(date));
        fields.put("vnp_CreateDate",LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(date));
        fields.put("vnp_IpAddr","127.0.0.1");fields.put("vnp_OrderInfo","Kiem tra thanh toan RVT "+fields.get("vnp_TxnRef"));
        fields.put("vnp_SecureHash",VnpayCodec.hash(settings.secret(),joined(fields,new ArrayList<>(fields.keySet()))));
        var request=HttpRequest.newBuilder(URI.create(ENDPOINT)).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(fields))).build();
        var reply=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
        Map<String,String> response=new HashMap<>();
        try(var stream=reply.body()) {
            byte[] body=stream.readNBytes(16385);
            if(reply.statusCode()!=200||body.length>16384)throw new java.io.IOException("Invalid query response");
            var root=json.readTree(body);
            if(!root.isObject())throw new java.io.IOException("Invalid query response");
            root.fields().forEachRemaining(e->response.put(e.getKey(),e.getValue().asText()));
        }
        var payment=verifiedPayment(settings.secret(),settings.tmnCode(),fields.get("vnp_TxnRef"),VnpayService.minorAmount((java.math.BigDecimal)attempt.get("SoTien")),response);
        if(!payment.isEmpty())gateway.verifiedQuery(payment);
    }
}
