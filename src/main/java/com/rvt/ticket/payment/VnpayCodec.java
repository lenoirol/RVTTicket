package com.rvt.ticket.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.MultiValueMap;

public final class VnpayCodec {
    private VnpayCodec(){}
    public static String query(Map<String,String> fields) {
        var parts=new ArrayList<String>();
        for(var e:new TreeMap<>(fields).entrySet())if(e.getValue()!=null&&!e.getValue().isEmpty()
            &&!Set.of("vnp_SecureHash","vnp_SecureHashType").contains(e.getKey()))
            parts.add(encode(e.getKey())+"="+encode(e.getValue()));
        return String.join("&",parts);
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public static String hash(String secret,String input) {
        try {
            var mac=Mac.getInstance("HmacSHA512");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Không thể ký giao dịch VNPay.",e);}
    }
    public static Map<String,String> parameters(MultiValueMap<String,String> params) {
        var fields=new TreeMap<String,String>();
        if(params.size()>40)throw new IllegalArgumentException("Invalid parameters");
        for(var e:params.entrySet()) {
            if(!e.getKey().startsWith("vnp_"))continue;
            if(!e.getKey().matches("vnp_[A-Za-z0-9_]{1,40}")||e.getValue().size()!=1
                ||e.getValue().getFirst()==null||e.getValue().getFirst().length()>1024)
                throw new IllegalArgumentException("Invalid parameters");
            fields.put(e.getKey(),e.getValue().getFirst());
        }
        return fields;
    }
    public static boolean verify(String secret,Map<String,String> fields) {
        var signature=fields.getOrDefault("vnp_SecureHash","");
        return !secret.isBlank()&&signature.matches("[a-fA-F0-9]{128}")
            &&MessageDigest.isEqual(HexFormat.of().parseHex(signature),HexFormat.of().parseHex(hash(secret,query(fields))));
    }
}
