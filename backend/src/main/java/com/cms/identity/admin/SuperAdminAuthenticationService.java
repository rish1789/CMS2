package com.cms.identity.admin;

import java.util.Optional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The one contract {@code com.cms.identity.account} (specifically {@code
 * StaffAuthController}, 040-super-admin-rbac-login) is allowed to call into this module
 * through, to check whether a submitted identifier/password pair is the configured Super
 * Admin credential. Composes the existing {@code superAdminUserDetailsService} bean
 * (003-super-admin-verification's in-memory credential, random-password-if-unconfigured
 * safety behavior) and {@link SuperAdminJwtService} internally - neither is exposed
 * outside this package directly, keeping this module's identity mechanism ("entirely
 * separate" from staff/patient, per spec.md) encapsulated behind a single method rather
 * than reached into as two raw beans (Constitution III).
 */
@Component
public class SuperAdminAuthenticationService {

    private final UserDetailsService superAdminUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final SuperAdminJwtService superAdminJwtService;

    public SuperAdminAuthenticationService(
            UserDetailsService superAdminUserDetailsService,
            PasswordEncoder passwordEncoder,
            SuperAdminJwtService superAdminJwtService) {
        this.superAdminUserDetailsService = superAdminUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.superAdminJwtService = superAdminJwtService;
    }

    /** @return an issued Super Admin JWT if {@code identifier}/{@code password} match the configured credential; empty otherwise. */
    public Optional<String> authenticate(String identifier, String password) {
        UserDetails superAdmin;
        try {
            superAdmin = superAdminUserDetailsService.loadUserByUsername(identifier);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, superAdmin.getPassword())) {
            return Optional.empty();
        }
        return Optional.of(superAdminJwtService.issueToken(superAdmin.getUsername()));
    }
}
