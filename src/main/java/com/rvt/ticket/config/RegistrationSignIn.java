package com.rvt.ticket.config;

import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Component;

/** Sign in only after the server has successfully created the customer's account. */
@Component
public class RegistrationSignIn {
    private final UserDetailsService users;
    public RegistrationSignIn(UserDetailsService users){this.users=users;}
    public void signIn(String username,HttpServletRequest request,HttpServletResponse response){
        var user=users.loadUserByUsername(username);
        var authentication=UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities());
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication,request,response);
        new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository()).onAuthentication(authentication,request,response);
        new HttpSessionRequestCache().removeRequest(request,response);
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
    }
}
