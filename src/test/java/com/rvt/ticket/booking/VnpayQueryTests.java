package com.rvt.ticket.booking;

import com.rvt.ticket.payment.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VnpayQueryTests {
    static final String KEY="test-only-query-key-123456";
    static final List<String> NAMES=List.of("vnp_ResponseId","vnp_Command","vnp_ResponseCode","vnp_Message","vnp_TmnCode","vnp_TxnRef","vnp_Amount","vnp_BankCode","vnp_PayDate","vnp_TransactionNo","vnp_TransactionType","vnp_TransactionStatus","vnp_OrderInfo","vnp_PromotionCode","vnp_PromotionAmount");
    Map<String,String> response(String status){
        var f=new HashMap<String,String>();f.put("vnp_ResponseId","response1");f.put("vnp_Command","querydr");f.put("vnp_ResponseCode","00");f.put("vnp_Message","QueryDR success");f.put("vnp_TmnCode","TEST0001");f.put("vnp_TxnRef","RVTREF");f.put("vnp_Amount","42500000");f.put("vnp_BankCode","NCB");f.put("vnp_PayDate","20261004091100");f.put("vnp_TransactionNo","123456");f.put("vnp_TransactionType","01");f.put("vnp_TransactionStatus",status);f.put("vnp_OrderInfo","RVT");return sign(f);
    }
    Map<String,String> sign(Map<String,String> f){f.put("vnp_SecureHash",VnpayCodec.hash(KEY,String.join("|",NAMES.stream().map(n->f.getOrDefault(n,"")).toList())));return f;}
    Map<String,String> verify(Map<String,String> f){return VnpayQuery.verifiedPayment(KEY,"TEST0001","RVTREF","42500000",f);}
    @Test void onlyVerifiedSuccessProducesASettlement(){var p=verify(response("00"));assertTrue(VnpayCodec.verify(KEY,p));assertEquals("42500000",p.get("vnp_Amount"));assertEquals("00",p.get("vnp_ResponseCode"));}
    @Test void tamperedOrUnsignedReplyNeverSettles(){var f=response("00");f.put("vnp_Amount","1");assertThrows(IllegalArgumentException.class,()->verify(f));f.remove("vnp_SecureHash");assertThrows(IllegalArgumentException.class,()->verify(f));}
    @Test void signedWrongMerchantReferenceAmountTypeOrCommandNeverSettles(){for(String name:List.of("vnp_TmnCode","vnp_TxnRef","vnp_Amount","vnp_TransactionType","vnp_Command")){var f=response("00");f.put(name,"WRONG");assertThrows(IllegalArgumentException.class,()->verify(sign(f)));}}
    @Test void processingIncompleteReversedRefundedAndSuspectDoNotConfirm(){for(String state:List.of("01","04","05","06","07","09","UNKNOWN"))assertTrue(verify(response(state)).isEmpty());}
    @Test void apiSuccessIsNotPaymentSuccess(){var p=verify(response("02"));assertEquals("99",p.get("vnp_ResponseCode"));assertEquals("02",p.get("vnp_TransactionStatus"));var f=response("00");f.put("vnp_ResponseCode","91");assertTrue(verify(sign(f)).isEmpty());}
    @Test void returnOriginMustBeExplicitlyAllowed(){var s=new VnpaySettings(true,"TEST0001",KEY,"https://public.invalid");assertEquals("http://localhost:8080/thanh-toan/vnpay/return",s.returnUrl("http://localhost:8080"));assertThrows(IllegalArgumentException.class,()->s.returnUrl("https://evil.invalid"));assertThrows(IllegalArgumentException.class,()->s.returnUrl("http://localhost:8080@evil.invalid"));}
}
