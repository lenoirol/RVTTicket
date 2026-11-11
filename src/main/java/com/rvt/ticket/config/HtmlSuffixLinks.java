package com.rvt.ticket.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;

/** Mọi liên kết Thymeleaf @{...} trỏ tới trang nội bộ được thêm đuôi ".html" (xem {@link HtmlSuffixFilter}). */
@Configuration
public class HtmlSuffixLinks {
    @Bean
    static BeanPostProcessor htmlSuffixLinkBuilder() {
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof SpringTemplateEngine engine) engine.setLinkBuilder(new SuffixLinkBuilder());
                return bean;
            }
        };
    }

    static final class SuffixLinkBuilder extends StandardLinkBuilder {
        @Override
        protected String processLink(IExpressionContext context, String link) {
            return super.processLink(context, HtmlSuffixFilter.withSuffix(link, null));
        }
    }
}
