package com.cms.identity.doctor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T030: licenseVerified=true but visible=false excludes a profile (FR-008, SC-004). */
class DiscoveryEligibilityVisibilityGateTest extends AbstractDoctorIntegrationTest {

    @Test
    void invisibleProfileExcludesEvenWhenVerified() {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-GATE-VISIBILITY", true, false);
        linkDoctorToClinic(profile, clinic, true);

        assertThat(doctorProfileRepository.findDiscoveryEligible()).doesNotContain(profile);
    }
}
