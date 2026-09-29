package com.cms.identity.doctor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T032: all four conditions true includes the profile (FR-008, SC-004). */
class DiscoveryEligibilityAllConditionsTest extends AbstractDoctorIntegrationTest {

    @Test
    void allConditionsTrueIncludesTheProfile() {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-GATE-ALL", true, true);
        linkDoctorToClinic(profile, clinic, true);

        // Compare by id: the repository returns fresh instances and DoctorProfile has no equals(),
        // so an instance comparison would never match (and doesNotContain would pass vacuously).
        assertThat(doctorProfileRepository.findDiscoveryEligible())
                .extracting(p -> p.getId())
                .contains(profile.getId());
    }
}
