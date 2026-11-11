package com.rvt.ticket.booking;

import com.rvt.ticket.payment.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.*;

class VnpayCodecTests {
    @Test void hmacMatchesIndependentRfc4231Vector(){
        assertEquals("164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
            VnpayCodec.hash("Jefe","what do ya want for nothing?"));
    }
    @Test void sortingAndUrlEncodingAreDeterministic(){
        assertEquals("vnp_Amount=31500000&vnp_OrderInfo=Thanh+toan+RVT&vnp_ReturnUrl=https%3A%2F%2Fexample.com%2Freturn",
            VnpayCodec.query(Map.of("vnp_OrderInfo","Thanh toan RVT","vnp_ReturnUrl","https://example.com/return","vnp_Amount","31500000","vnp_SecureHash","ignored","vnp_SecureHashType","ignored")));
    }
    @Test void verifyRejectsTamperingMalformedAndMissingHash(){
        var f=new HashMap<>(Map.of("vnp_Amount","31500000","vnp_TxnRef","RVT123"));String key="test-only-secret-123456";
        f.put("vnp_SecureHash",VnpayCodec.hash(key,VnpayCodec.query(f)).toUpperCase());assertTrue(VnpayCodec.verify(key,f));
        f.put("vnp_Amount","1");assertFalse(VnpayCodec.verify(key,f));f.put("vnp_SecureHash","bad");assertFalse(VnpayCodec.verify(key,f));
        f.remove("vnp_SecureHash");assertFalse(VnpayCodec.verify(key,f));assertFalse(VnpayCodec.verify("",f));
    }
    @Test void duplicateParametersAreNotSilentlyCollapsed(){
        var f=new LinkedMultiValueMap<String,String>();f.add("vnp_Amount","1");f.add("vnp_Amount","2");
        assertThrows(IllegalArgumentException.class,()->VnpayCodec.parameters(f));f.remove("vnp_Amount");f.add("utm_source","anything");f.add("vnp_TxnRef","RVT1");
        assertEquals(Map.of("vnp_TxnRef","RVT1"),VnpayCodec.parameters(f));
    }
    @Test void amountIsIntegerVndConvertedExactlyWithinProviderLimit(){
        assertEquals("31500000",VnpayService.minorAmount(new BigDecimal("315000.00")));
        for(var bad:List.of("0","-1","1.01","10000000000"))assertThrows(IllegalArgumentException.class,()->VnpayService.minorAmount(new BigDecimal(bad)));
    }
    @Test void enabledSettingsFailClosedWithoutSecretsAndHttpsOrigin(){
        assertFalse(new VnpaySettings(false,"","","").enabled());
        for(String url:List.of("http://localhost:8080","https://example.com/path","https://example.com?x=1","https://user@example.com"))
            assertThrows(IllegalArgumentException.class,()->new VnpaySettings(true,"TEST0001","test-only-secret-123456",url));
        assertThrows(IllegalArgumentException.class,()->new VnpaySettings(true,"TEST0001","","https://example.com"));
        assertEquals("https://example.com/thanh-toan/vnpay/ipn",new VnpaySettings(true,"TEST0001","test-only-secret-123456","https://example.com/").ipnUrl());
    }
}
