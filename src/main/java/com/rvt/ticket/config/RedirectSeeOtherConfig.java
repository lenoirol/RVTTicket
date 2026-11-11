package com.rvt.ticket.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

/** Post/Redirect/Get: mọi "redirect:" sau POST trả HTTP 303 See Other để trình duyệt chắc chắn đổi sang GET. */
@Configuration
public class RedirectSeeOtherConfig {
    @Bean
    static BeanPostProcessor seeOtherRedirects() {
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof ThymeleafViewResolver r) r.setRedirectHttp10Compatible(false);
                return bean;
            }
        };
    }
}
