package com.cms.common;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link RequestIdFilter} at the servlet-container level, ahead of everything else
 * (including Spring Security's own filter chains) so every request - authenticated, rejected,
 * or rate-limited - gets a correlation id from the very first line logged about it.
 */
@Configuration
public class RequestLoggingConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
