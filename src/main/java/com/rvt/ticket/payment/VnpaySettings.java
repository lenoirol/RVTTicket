package com.rvt.ticket.payment;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sandbox only. Credentials never appear in templates or source-controlled configuration. */
@Component
public class VnpaySettings {
    public static final String PAY_URL="https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private final boolean enabled;
    private final String tmnCode,secret,baseUrl;
    @Value("${rvt.vnpay.return-origins:http://localhost:8080,http://127.0.0.1:8080}")
    private String returnOrigins="http://localhost:8080,http://127.0.0.1:8080";
    /** Off by default. Lets a VNPay-signed browser Return settle with the same checks as IPN, for hosts VNPay cannot reach by IPN and that cannot reach querydr. */
    @Value("${rvt.vnpay.return-settles:false}")
    private boolean returnSettles=false;
    public boolean returnSettles(){return enabled&&returnSettles;}
    public VnpaySettings(@Value("${rvt.vnpay.enabled:false}") boolean enabled,
            @Value("${rvt.vnpay.tmn-code:}") String tmnCode,
            @Value("${rvt.vnpay.hash-secret:}") String secret,
            @Value("${rvt.vnpay.public-base-url:}") String baseUrl) {
        this.enabled=enabled;this.tmnCode=tmnCode.strip();this.secret=secret;
        this.baseUrl=baseUrl.strip().replaceAll("/+$","");
        if(enabled) {
            if(!this.tmnCode.matches("[A-Z0-9]{8}")||secret.length()<16)
                throw new IllegalArgumentException("VNPay: cần mã merchant và khóa bí mật hợp lệ trong biến môi trường.");
            try {
                var uri=URI.create(this.baseUrl);
                if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null
                    ||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().isEmpty())throw new IllegalArgumentException();
            } catch(IllegalArgumentException e) {
                throw new IllegalArgumentException("VNPay: public-base-url phải là địa chỉ HTTPS gốc, không kèm đường dẫn.");
            }
        }
    }
    public boolean enabled(){return enabled;}
    public String tmnCode(){return tmnCode;}
    public String secret(){return secret;}
    public String returnUrl(){return baseUrl+"/thanh-toan/vnpay/return";}
    public String returnUrl(String origin) {
        if(origin==null)return returnUrl();
        // No forwarded headers and no arbitrary return URL supplied by the browser.
        if(!java.util.Arrays.stream(returnOrigins.split(",")).map(String::strip).anyMatch(origin::equals))
            throw new IllegalArgumentException("Địa chỉ truy cập chưa được cấu hình cho VNPay. Vui lòng mở website bằng địa chỉ chính thức.");
        return origin+"/thanh-toan/vnpay/return";
    }
    public String ipnUrl(){return baseUrl+"/thanh-toan/vnpay/ipn";}
}
