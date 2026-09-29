package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 053: real-bug-fix - create/list previously had no way to correct a mistyped name or fee
 * override afterward. Mirrors AppointmentTypeConfigAuthorizationTest's own authorization matrix,
 * applied to the new PUT rename endpoint.
 */
class AppointmentTypeRenameTest extends AbstractBookingIntegrationTest {

    private static final String RENAME_BODY = """
            { "name": "General Consultation", "feeOverride": 250.00 }
            """;

    @Test
    void doctorsOwnTokenCanRenameTheirOwnAppointmentType() throws Exception {
        var clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeService.create(doctor.getAccount().getId(), doctor.getId(), "Gauresh Kumar", null);
        String token = doctorToken(doctor);

        mockMvc.perform(put("/api/v1/doctors/{doctorId}/appointment-types/{typeId}", doctor.getId(), type.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENAME_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("General Consultation"))
                .andExpect(jsonPath("$.feeOverride").value(250.00));

        AppointmentType persisted = appointmentTypeRepository.findById(type.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("General Consultation");
        assertThat(persisted.getFeeOverride()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void clinicAdminAtDoctorsClinicCanRename() throws Exception {
        var clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeService.create(doctor.getAccount().getId(), doctor.getId(), "Typo Name", null);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(put("/api/v1/doctors/{doctorId}/appointment-types/{typeId}", doctor.getId(), type.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENAME_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedStaffMemberIsForbidden() throws Exception {
        var clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeService.create(doctor.getAccount().getId(), doctor.getId(), "Typo Name", null);
        String token = unrelatedStaffToken();

        mockMvc.perform(put("/api/v1/doctors/{doctorId}/appointment-types/{typeId}", doctor.getId(), type.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENAME_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void renamingAnAppointmentTypeBelongingToADifferentDoctorIsNotFound() throws Exception {
        var clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        DoctorProfile otherDoctor = saveDoctorStaffedAt(clinic);
        AppointmentType type =
                appointmentTypeService.create(otherDoctor.getAccount().getId(), otherDoctor.getId(), "Typo Name", null);
        String token = doctorToken(doctor);

        mockMvc.perform(put("/api/v1/doctors/{doctorId}/appointment-types/{typeId}", doctor.getId(), type.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENAME_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("APPOINTMENT_TYPE_NOT_FOUND"));
    }
}
