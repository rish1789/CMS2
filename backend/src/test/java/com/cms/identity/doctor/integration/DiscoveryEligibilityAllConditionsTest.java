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

        assertThat(doctorProfileRepository.findDiscoveryEligible()).contains(profile);
    }
}
