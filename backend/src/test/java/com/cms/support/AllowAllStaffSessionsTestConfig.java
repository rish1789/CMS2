package com.cms.support;

import com.cms.identity.account.config.StaffSessionPolicy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 075-login-hardening: {@code @WebMvcTest} slices load the staff security chain without a database,
 * so they get a session policy that accepts every validly signed token. The real policy
 * (an active role at any clinic) is exercised end to end by {@code LoginHardeningTest}.
 */
@TestConfiguration
public class AllowAllStaffSessionsTestConfig {

    @Bean
    StaffSessionPolicy allowAllStaffSessions() {
        return accountId -> true;
    }
}
