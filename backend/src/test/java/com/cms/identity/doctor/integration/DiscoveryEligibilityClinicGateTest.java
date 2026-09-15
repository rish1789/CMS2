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

        assertThat(doctorProfileRepository.findDiscoveryEligible()).doesNotContain(profile);
    }
}
