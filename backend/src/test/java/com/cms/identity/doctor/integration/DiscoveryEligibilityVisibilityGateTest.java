package com.cms.identity.doctor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T030: licenseVerified=true but visible=false excludes a profile (FR-008, SC-004). */
class DiscoveryEligibilityVisibilityGateTest extends AbstractDoctorIntegrationTest {

    @Test
    void invisibleProfileExcludesEvenWhenVerified() {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-GATE-VISIBLE", true, false);
        linkDoctorToClinic(profile, clinic, true);

        // Compare by id: the repository returns fresh instances and DoctorProfile has no equals(),
        // so an instance comparison would never match (and doesNotContain would pass vacuously).
        assertThat(doctorProfileRepository.findDiscoveryEligible())
                .extracting(p -> p.getId())
                .doesNotContain(profile.getId());
    }
}
