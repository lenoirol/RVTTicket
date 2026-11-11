package com.rvt.ticket.config;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Carries the credential version in the session, never in a browser field. */
public final class CustomerIdentity extends User {
    private static final long serialVersionUID = 1L;
    private final int version;
    public CustomerIdentity(String username,String hash,boolean enabled,int version){this(username,hash,enabled,version,"TRAVELER");}
    /** ROLE_CUSTOMER giữ tương thích; ROLE_TRAVELER hoặc ROLE_AGENT là vai trò RBAC theo đề. */
    public CustomerIdentity(String username,String hash,boolean enabled,int version,String role){
        super(username,hash,enabled,true,true,true,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"),new SimpleGrantedAuthority("AGENT".equals(role)?"ROLE_AGENT":"ROLE_TRAVELER")));
        this.version=version;
    }
    public int version(){return version;}
}
