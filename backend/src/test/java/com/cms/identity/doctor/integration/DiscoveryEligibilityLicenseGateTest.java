package com.cms.identity.doctor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T029: licenseVerified=false excludes a profile regardless of visible/clinic.verified (FR-008, SC-004). */
class DiscoveryEligibilityLicenseGateTest extends AbstractDoctorIntegrationTest {

    @Test
    void unverifiedLicenseExcludesEvenWithVisibleAndVerifiedClinic() {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-GATE-LICENSE", false, true);
        linkDoctorToClinic(profile, clinic, true);

        // Compare by id: the repository returns fresh instances and DoctorProfile has no equals(),
        // so an instance comparison would never match (and doesNotContain would pass vacuously).
        assertThat(doctorProfileRepository.findDiscoveryEligible())
                .extracting(p -> p.getId())
                .doesNotContain(profile.getId());
    }
}
