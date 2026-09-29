package com.cms.identity.account.config;

import com.cms.identity.account.service.RejectedClinicAccessGate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 062-rejected-clinic-gating (research.md Decision 6): registers {@link
 * RejectedClinicAccessInterceptor} for {@code /api/v1/clinics/**}. The gate is taken through an
 * {@link ObjectProvider} on purpose: {@code @WebMvcTest} slices load every {@code WebMvcConfigurer}
 * but not services or repositories, so a hard dependency here would break every existing contract
 * slice (the bug class 004 already hit with a JWT filter). In a slice the gate is simply absent and
 * the interceptor is not registered; the full application context always has it.
 */
@Configuration
public class RejectedClinicAccessWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<RejectedClinicAccessGate> gateProvider;

    public RejectedClinicAccessWebConfig(ObjectProvider<RejectedClinicAccessGate> gateProvider) {
        this.gateProvider = gateProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RejectedClinicAccessGate gate = gateProvider.getIfAvailable();
        if (gate != null) {
            registry.addInterceptor(new RejectedClinicAccessInterceptor(gate)).addPathPatterns("/api/v1/clinics/**");
        }
    }
}
