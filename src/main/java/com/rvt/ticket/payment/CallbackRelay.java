package com.rvt.ticket.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** The only service exposed by Funnel. Never proxies the website, cookies, or arbitrary paths. */
public final class CallbackRelay implements AutoCloseable {
    public static final String IPN="/thanh-toan/vnpay/ipn",RETURN="/thanh-toan/vnpay/return";
    private final HttpServer server;
    private final ExecutorService pool=new ThreadPoolExecutor(8,8,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.CallerRunsPolicy());
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI upstream,privateOrigin;
    private final ObjectMapper json=new ObjectMapper();
    public CallbackRelay(int port,URI upstream,URI privateOrigin) throws IOException {
        if(!origin(upstream)||!"http".equals(upstream.getScheme())||!"127.0.0.1".equals(upstream.getHost()))
            throw new IllegalArgumentException("Callback upstream must be a loopback HTTP origin.");
        if(!origin(privateOrigin)||!Set.of("http","https").contains(privateOrigin.getScheme())
            ||!(privateOrigin.getHost().matches("100\\.(?:6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\.[0-9]{1,3}\\.[0-9]{1,3}")
                ||Set.of("localhost","127.0.0.1").contains(privateOrigin.getHost())))
            throw new IllegalArgumentException("Return destination must be the private Tailscale or local website origin.");
        this.upstream=upstream;this.privateOrigin=privateOrigin;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),32);
        server.createContext("/",this::handle);server.setExecutor(pool);
    }
    private static boolean origin(URI uri){return uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null&&uri.getPath().isEmpty();}
    public void start(){server.start();}
    public int port(){return server.getAddress().getPort();}
    private void handle(HttpExchange exchange) throws IOException {
        try(exchange) {
            var headers=exchange.getResponseHeaders();headers.set("Cache-Control","no-store");headers.set("X-Content-Type-Options","nosniff");headers.set("Referrer-Policy","no-referrer");
            String path=exchange.getRequestURI().getRawPath();
            if(!Set.of(IPN,RETURN).contains(path)){send(exchange,403,"text/plain;charset=UTF-8","Not exposed");return;}
            if(!"GET".equals(exchange.getRequestMethod())){headers.set("Allow","GET");send(exchange,405,"text/plain;charset=UTF-8","GET only");return;}
            if(exchange.getRequestHeaders().containsKey("Transfer-Encoding")
                ||!"0".equals(exchange.getRequestHeaders().getFirst("Content-Length")==null?"0":exchange.getRequestHeaders().getFirst("Content-Length"))) {
                send(exchange,400,"text/plain;charset=UTF-8","Request body not accepted");return;
            }
            String query=exchange.getRequestURI().getRawQuery();
            if(query!=null&&query.length()>8192){send(exchange,414,"text/plain;charset=UTF-8","Query too long");return;}
            String suffix=query==null?"":"?"+query;
            if(RETURN.equals(path)) {
                // The customer's existing private-site session is retained on the private origin.
                headers.set("Location",privateOrigin+RETURN+suffix);exchange.sendResponseHeaders(302,-1);return;
            }
            try {
                // Explicit URI and no forwarded headers: Host, cookies, authorization, X-Forwarded-* are ignored.
                var request=HttpRequest.newBuilder(URI.create(upstream+IPN+suffix)).timeout(Duration.ofSeconds(8)).GET().header("Accept","application/json").build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
                try(var stream=response.body()) {
                    byte[] bytes=stream.readNBytes(4097);
                    if(response.statusCode()!=200||bytes.length>4096||!response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))throw new IOException("Invalid upstream response");
                    var root=json.readTree(bytes);String code=root.path("RspCode").asText(),message=root.path("Message").asText();
                    if(!Set.of("00","01","02","04","97","99").contains(code)||message.length()>120)throw new IOException("Invalid IPN reply");
                    send(exchange,200,"application/json;charset=UTF-8",json.writeValueAsString(Map.of("RspCode",code,"Message",message)));
                }
            }catch(InterruptedException e){Thread.currentThread().interrupt();unavailable(exchange);}
            catch(IOException|IllegalArgumentException e){unavailable(exchange);}
        }
    }
    private void unavailable(HttpExchange exchange) throws IOException {send(exchange,502,"application/json;charset=UTF-8","{\"RspCode\":\"99\",\"Message\":\"Callback temporarily unavailable\"}");}
    private static void send(HttpExchange exchange,int status,String type,String content) throws IOException {
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
    }
    @Override public void close(){server.stop(1);pool.shutdownNow();client.close();}
}
