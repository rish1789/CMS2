package com.cms.identity.doctor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T031: licenseVerified=true, visible=true, but the only clinic is unverified excludes a profile (FR-008, SC-004). */
class DiscoveryEligibilityClinicGateTest extends AbstractDoctorIntegrationTest {

    @Test
    void unverifiedClinicExcludesEvenWhenVerifiedAndVisible() {
        var clinic = saveClinic("Sunrise Clinic", false);
        var profile = saveDoctorProfile("LIC-GATE-CLINIC", true, true);
        linkDoctorToClinic(profile, clinic, true);

        // Compare by id: the repository returns fresh instances and DoctorProfile has no equals(),
        // so an instance comparison would never match (and doesNotContain would pass vacuously).
        assertThat(doctorProfileRepository.findDiscoveryEligible())
                .extracting(p -> p.getId())
                .doesNotContain(profile.getId());
    }
}
