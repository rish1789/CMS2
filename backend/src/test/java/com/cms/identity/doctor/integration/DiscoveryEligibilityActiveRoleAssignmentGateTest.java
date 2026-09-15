package com.cms.identity.doctor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * T032a: licenseVerified=true, visible=true, clinic verified=true, but the doctor's Role
 * Assignment at that clinic is deactivated (007) excludes the profile (FR-008, SC-004;
 * analyze fix I1 - a deactivated staff member's clinic link must not count).
 */
class DiscoveryEligibilityActiveRoleAssignmentGateTest extends AbstractDoctorIntegrationTest {

    @Test
    void deactivatedRoleAssignmentExcludesEvenAtAVerifiedClinic() {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-GATE-ACTIVE", true, true);
        linkDoctorToClinic(profile, clinic, false);

        assertThat(doctorProfileRepository.findDiscoveryEligible()).doesNotContain(profile);
    }
}
