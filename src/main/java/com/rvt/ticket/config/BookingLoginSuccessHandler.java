package com.rvt.ticket.config;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/** Only resume an internal GET booking route, never a saved POST or an arbitrary URL/host. */
public final class BookingLoginSuccessHandler implements AuthenticationSuccessHandler {
    public static String pending(HttpServletRequest request,HttpServletResponse response){
        var saved=new HttpSessionRequestCache().getRequest(request,response);
        if(saved==null||!"GET".equals(saved.getMethod()))return null;
        try{String path=URI.create(saved.getRedirectUrl()).getPath();String context=request.getContextPath();
            if(!context.isEmpty()){if(!path.startsWith(context+"/"))return null;path=path.substring(context.length());}
            return path.matches("/dat-ve/[1-9][0-9]{0,9}")?context+path:null;
        }catch(IllegalArgumentException e){return null;}
    }
    @Override public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws IOException{
        boolean customer=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CUSTOMER"));
        String target=customer?pending(request,response):null;
        new HttpSessionRequestCache().removeRequest(request,response);
        response.sendRedirect(target==null?request.getContextPath()+"/workspace":target);
    }
}
