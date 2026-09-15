package com.cms.patient.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T012: a token issued by {@link JwtService} carries a patient-only audience/scope
 * claim, and a stub staff-only authorization check structurally rejects it (FR-008).
 */
class JwtServiceScopeTest {

    private final JwtService jwtService = new JwtService("unit-test-only-secret-at-least-32-bytes-long-for-hs256");

    @Test
    void issuedTokenCarriesPatientAudience() {
        String token = jwtService.issueToken(UUID.randomUUID());

        Claims claims = jwtService.parse(token);

        assertThat(claims.getAudience()).containsExactly(JwtService.PATIENT_AUDIENCE);
        assertThat(jwtService.isPatientToken(token)).isTrue();
    }

    @Test
    void stubStaffOnlyCheckStructurallyRejectsAPatientToken() {
        String patientToken = jwtService.issueToken(UUID.randomUUID());

        // Stand-in for a future staff-only authorization check (003/004): it requires a
        // "staff" audience, which a patient token never has - this is the structural
        // guarantee FR-008 asks for, not a convention that could be forgotten elsewhere.
        boolean staffCheckPasses = stubStaffOnlyAuthorizationCheck(patientToken);

        assertThat(staffCheckPasses).isFalse();
    }

    private boolean stubStaffOnlyAuthorizationCheck(String token) {
        Claims claims = jwtService.parse(token);
        Set<String> audience = claims.getAudience();
        return audience != null && audience.contains("staff");
    }
}
