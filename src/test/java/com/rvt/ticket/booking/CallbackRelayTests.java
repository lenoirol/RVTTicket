package com.rvt.ticket.booking;

import com.rvt.ticket.payment.CallbackRelay;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CallbackRelayTests {
    HttpServer upstream;
    CallbackRelay relay;
    HttpClient client;
    AtomicReference<com.sun.net.httpserver.Headers> upstreamHeaders=new AtomicReference<>();
    AtomicReference<String> upstreamQuery=new AtomicReference<>();
    int responseStatus=200;String responseType="application/json",responseBody="{\"RspCode\":\"97\",\"Message\":\"Invalid signature\"}";
    @BeforeEach void start() throws Exception {
        upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        upstream.createContext("/",exchange->{
            try(exchange){upstreamHeaders.set(exchange.getRequestHeaders());upstreamQuery.set(exchange.getRequestURI().getRawQuery());
                exchange.getResponseHeaders().set("Content-Type",responseType);exchange.getResponseHeaders().set("Set-Cookie","sensitive-session=private");
                var bytes=responseBody.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(responseStatus,bytes.length);exchange.getResponseBody().write(bytes);}
        });upstream.start();
        relay=new CallbackRelay(0,URI.create("http://127.0.0.1:"+upstream.getAddress().getPort()),URI.create("http://100.112.242.8:8080"));relay.start();
        client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @AfterEach void stop(){if(relay!=null)relay.close();if(upstream!=null)upstream.stop(0);if(client!=null)client.close();}
    URI url(String path){return URI.create("http://127.0.0.1:"+relay.port()+path);}
    HttpResponse<String> get(String path) throws Exception {return client.send(HttpRequest.newBuilder(url(path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    @Test void entireWebsiteAdminAndEncodedPathsAreDenied() throws Exception {
        for(String path:List.of("/","/admin","/login","/tai-khoan","/actuator","/css/app.css","/thanh-toan/vnpay/ipn/","/thanh-toan/vnpay/ipn/admin","/thanh-toan/vnpay/%69pn","/thanh-toan/vnpay/ipn%2F..%2Fadmin"))
            assertEquals(403,get(path).statusCode(),path);
        assertNull(upstreamHeaders.get());
    }
    @Test void onlyGetIsAccepted() throws Exception {
        var response=client.send(HttpRequest.newBuilder(url(CallbackRelay.IPN)).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(405,response.statusCode());assertEquals("GET",response.headers().firstValue("Allow").orElseThrow());assertNull(upstreamHeaders.get());
    }
    @Test void ipnQueryIsPreservedButCookiesAuthorizationAndProxyHeadersAreNotForwarded() throws Exception {
        String query="?vnp_OrderInfo=Thanh+toan+RVT&vnp_Amount=31500000";
        var response=client.send(HttpRequest.newBuilder(url(CallbackRelay.IPN+query)).header("Cookie","JSESSIONID=private").header("Authorization","Bearer private").header("X-Forwarded-Host","evil.example").header("X-Forwarded-For","1.2.3.4").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());assertTrue(response.body().contains("97"));assertEquals(query.substring(1),upstreamQuery.get());
        for(String header:List.of("Cookie","Authorization","X-Forwarded-Host","X-Forwarded-For"))assertNull(upstreamHeaders.get().getFirst(header));
        assertTrue(response.headers().firstValue("Set-Cookie").isEmpty());assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
    }
    @Test void browserReturnRedirectsOnlyToFixedPrivateOrigin() throws Exception {
        String query="?vnp_TxnRef=RVT123&next=https%3A%2F%2Fevil.example";var response=get(CallbackRelay.RETURN+query);
        assertEquals(302,response.statusCode());assertEquals("http://100.112.242.8:8080"+CallbackRelay.RETURN+query,response.headers().firstValue("Location").orElseThrow());assertNull(upstreamHeaders.get());
    }
    @Test void upstreamErrorCannotExposeHtmlOrPrivateInformation() throws Exception {
        responseStatus=500;responseType="text/html";responseBody="private database details";var response=get(CallbackRelay.IPN);
        assertEquals(502,response.statusCode());assertFalse(response.body().contains("private database"));assertTrue(response.body().contains("99"));assertTrue(response.headers().firstValue("Set-Cookie").isEmpty());
    }
    @Test void onlyKnownIpnReplyFieldsCanReachPublicClient() throws Exception {
        responseBody="{\"RspCode\":\"00\",\"Message\":\"Confirm Success\",\"password\":\"private\"}";
        var response=get(CallbackRelay.IPN);assertEquals(200,response.statusCode());assertFalse(response.body().contains("private"));assertFalse(response.body().contains("password"));
        responseBody="{\"RspCode\":\"BAD\",\"Message\":\"unexpected\"}";assertEquals(502,get(CallbackRelay.IPN).statusCode());
    }
    @Test void originsMustBeLoopbackBackendAndPrivateReturnDestination(){
        for(String bad:List.of("http://example.com","https://127.0.0.1:8080","http://127.0.0.1:8080/admin"))
            assertThrows(IllegalArgumentException.class,()->new CallbackRelay(0,URI.create(bad),URI.create("http://100.112.242.8:8080")));
        for(String bad:List.of("https://example.com","http://100.112.242.8:8080/path","http://user@100.112.242.8:8080","http://100.1.1.1:8080"))
            assertThrows(IllegalArgumentException.class,()->new CallbackRelay(0,URI.create("http://127.0.0.1:8080"),URI.create(bad)));
    }
    @Test void relayBindsOnlyLoopbackAndOversizedQueryIsRejected() throws Exception {
        var response=get(CallbackRelay.IPN+"?x="+"a".repeat(8193));assertEquals(414,response.statusCode());assertNull(upstreamHeaders.get());
        assertEquals("127.0.0.1",url(CallbackRelay.IPN).getHost());
    }
}
