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

        // Compare by id: the repository returns fresh instances and DoctorProfile has no equals(),
        // so an instance comparison would never match (and doesNotContain would pass vacuously).
        assertThat(doctorProfileRepository.findDiscoveryEligible())
                .extracting(p -> p.getId())
                .doesNotContain(profile.getId());
    }
}
