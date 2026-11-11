package com.rvt.ticket.config;

import com.rvt.ticket.payment.CallbackRelay;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="rvt.vnpay.relay.enabled",havingValue="true")
public class VnpayRelaySetup {
    private final int port,webPort;
    private final String privateUrl;
    private CallbackRelay relay;
    public VnpayRelaySetup(@Value("${rvt.vnpay.relay.port:8091}") int port,
            @Value("${server.port:8080}") int webPort,
            @Value("${rvt.vnpay.relay.private-base-url}") String privateUrl){this.port=port;this.webPort=webPort;this.privateUrl=privateUrl;}
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() throws IOException {
        if(relay!=null)return;
        relay=new CallbackRelay(port,URI.create("http://127.0.0.1:"+webPort),URI.create(privateUrl.replaceAll("/+$","")));relay.start();
    }
    @PreDestroy public synchronized void stop(){if(relay!=null)relay.close();}
}
