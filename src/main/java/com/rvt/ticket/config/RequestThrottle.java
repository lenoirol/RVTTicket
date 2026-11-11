package com.rvt.ticket.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Modest in-process protection for a single-node private coursework deployment. */
public final class RequestThrottle extends OncePerRequestFilter {
    private static final long WINDOW=300_000;
    private final Clock clock;
    private final Map<String,Window> buckets=new HashMap<>();
    private record Window(long start,int count){}
    public RequestThrottle(){this(Clock.systemUTC());}
    public RequestThrottle(Clock clock){this.clock=clock;}
    public synchronized boolean allow(String address,String action){
        long now=clock.millis();buckets.entrySet().removeIf(e->now-e.getValue().start()>=WINDOW);
        String key=address+":"+action;var prior=buckets.get(key);
        if(prior==null){if(buckets.size()>=4096)return false;buckets.put(key,new Window(now,1));return true;}
        int maximum=action.equals("/login")?30:action.equals("/dang-ky")?15:40;
        if(prior.count()>=maximum)return false;
        buckets.put(key,new Window(prior.start(),prior.count()+1));return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException{
        String path=request.getServletPath();
        if("POST".equals(request.getMethod())&&Set.of("/login","/dang-ky","/tra-cuu").contains(path)&&!allow(request.getRemoteAddr(),path)){
            response.setStatus(429);response.setHeader("Retry-After","300");response.setHeader("Cache-Control","no-store");response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!doctype html><html lang=vi><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Thử lại sau · RVT Ticket</title><link rel=stylesheet href=/css/fonts.css><link rel=stylesheet href=/css/app.css><main class='container narrow page'><h1>Vui lòng thử lại sau</h1><p>Có quá nhiều lần đăng nhập, đăng ký hoặc tra cứu từ kết nối này. Hãy chờ tối đa 5 phút rồi thử lại.</p><a href=/ class='button'>Về trang chủ</a></main></html>");return;
        }
        // Deliberately ignore X-Forwarded-For: no trusted public proxy has been configured.
        chain.doFilter(request,response);
    }
}
