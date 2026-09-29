package com.cms.protection.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.protection.domain.SuspiciousActivityFlag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * 060-booking-abuse-prevention (spec.md FR-022, BR-004): a flag and its evidence created for one
 * clinic's activity are never visible to a ClinicAdmin at a different clinic. Written/compiled,
 * Docker-gated per this sandbox's standing Testcontainers limitation.
 */
class ProtectionFlagTenantIsolationTest extends AbstractProtectionIntegrationTest {

    @Test
    void aFlagAtClinicAIsInvisibleToClinicBsAdminButVisibleToClinicAsOwnAdmin() throws Exception {
        Clinic clinicA = saveClinic();
        Clinic clinicB = saveClinic();
        PatientAccount patient = savePatientAccount();
        SuspiciousActivityFlag flag = saveOutstandingFlag(patient, clinicA, "4 cancellations in the last 30 days");
        String clinicAAdminToken = clinicAdminToken(clinicA);
        String clinicBAdminToken = clinicAdminToken(clinicB);

        // Clinic A's own admin sees it.
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags", clinicA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.flags[0].id").value(flag.getId().toString()));

        // Clinic B's admin, querying their own clinic, never sees Clinic A's flag.
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags", clinicB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicBAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));

        // Clinic B's admin cannot fetch Clinic A's flag detail either - even by guessing its id
        // under their own clinic's path, it's a 404 (this codebase's established cross-tenant
        // "not found, not forbidden" convention), never Clinic A's evidence.
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags/{flagId}", clinicB.getId(), flag.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicBAdminToken))
                .andExpect(status().isNotFound());

        // ...and cannot reach it by querying Clinic A's own path either - they simply aren't
        // ClinicAdmin there.
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags/{flagId}", clinicA.getId(), flag.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicBAdminToken))
                .andExpect(status().isForbidden());
    }
}
