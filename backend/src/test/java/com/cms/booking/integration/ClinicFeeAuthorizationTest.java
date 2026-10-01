package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 068-per-clinic-fees US2 (FR-005/FR-006, SC-002): only an active ClinicAdmin of the clinic can
 * set that clinic's prices, and only for a doctor actively staffed there. Any active staff of the
 * clinic can read them. Another clinic's admin never can - even when the doctor also works there.
 */
class ClinicFeeAuthorizationTest extends AbstractBookingIntegrationTest {

    private static final String FEES = "/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees";
    private static final String AMOUNT_500 = """
            { "amount": 500.00 }
            """;

    private Clinic clinicA;
    private Clinic clinicB;
    private DoctorProfile doctor;
    private AppointmentType consultation;

    @BeforeEach
    void doctorAtTwoClinics() {
        clinicA = saveClinic();
        clinicB = saveClinic();
        doctor = saveDoctorStaffedAt(clinicA);
        linkDoctorToClinic(doctor, clinicB, true);
        consultation = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null));
    }

    @Test
    void theClinicsOwnAdminSetsAndClearsItsPrices() throws Exception {
        String token = clinicAdminToken(clinicA);

        putDefault(token, clinicA, doctor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicId").value(clinicA.getId().toString()))
                .andExpect(jsonPath("$.defaultFee").value(500.00))
                .andExpect(jsonPath("$.appointmentTypes[0].price").isEmpty())
                .andExpect(jsonPath("$.appointmentTypes[0].effectiveFee").value(500.00));

        mockMvc.perform(put(FEES + "/appointment-types/{typeId}", clinicA.getId(), doctor.getId(), consultation.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 800.00 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentTypes[0].price").value(800.00))
                .andExpect(jsonPath("$.appointmentTypes[0].effectiveFee").value(800.00));

        mockMvc.perform(delete(FEES + "/appointment-types/{typeId}", clinicA.getId(), doctor.getId(), consultation.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentTypes[0].price").isEmpty())
                .andExpect(jsonPath("$.appointmentTypes[0].effectiveFee").value(500.00));

        var fee = clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(clinicA.getId(), doctor.getId()).orElseThrow();
        assertThat(fee.getUpdatedByAccountId()).isNotNull();
    }

    @Test
    void settingTheDefaultTwiceReplacesRatherThanDuplicates() throws Exception {
        String token = clinicAdminToken(clinicA);

        putDefault(token, clinicA, doctor).andExpect(status().isOk());
        mockMvc.perform(put(FEES + "/default", clinicA.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 650.00 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultFee").value(650.00));

        assertThat(clinicDoctorFeeRepository.findAll()).hasSize(1);
    }

    @Test
    void anotherClinicsAdminIsForbiddenEvenWhenTheDoctorWorksThere() throws Exception {
        putDefault(clinicAdminToken(clinicA), clinicA, doctor).andExpect(status().isOk());

        putDefault(clinicAdminToken(clinicB), clinicA, doctor)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        var feeAtA = clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(clinicA.getId(), doctor.getId());
        assertThat(feeAtA).get().extracting(f -> f.getAmount().toPlainString()).isEqualTo("500.00");
    }

    @Test
    void theDoctorCannotSetTheirOwnClinicPrice() throws Exception {
        putDefault(doctorToken(doctor), clinicA, doctor)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void operationsStaffCanReadButNotWrite() throws Exception {
        String token = operationsToken(clinicA);

        putDefault(token, clinicA, doctor).andExpect(status().isForbidden());
        mockMvc.perform(get(FEES, clinicA.getId(), doctor.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultFee").isEmpty())
                .andExpect(jsonPath("$.appointmentTypes[0].effectiveFee").isEmpty());
    }

    @Test
    void staffOfAnotherClinicCannotRead() throws Exception {
        mockMvc.perform(get(FEES, clinicA.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedStaffToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAdminCannotPriceADoctorNotStaffedAtTheirClinic() throws Exception {
        Clinic clinicC = saveClinic();

        putDefault(clinicAdminToken(clinicC), clinicC, doctor)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void aNegativeAmountIsRejected() throws Exception {
        mockMvc.perform(put(FEES + "/default", clinicA.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinicA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": -1.00 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FEE_AMOUNT"));
    }

    @Test
    void anotherDoctorsTypeIsNotFound() throws Exception {
        DoctorProfile otherDoctor = saveDoctorStaffedAt(clinicA);
        AppointmentType otherType = appointmentTypeRepository.save(new AppointmentType(otherDoctor, "Other", null));

        mockMvc.perform(put(FEES + "/appointment-types/{typeId}", clinicA.getId(), doctor.getId(), otherType.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinicA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AMOUNT_500))
                .andExpect(status().isNotFound());
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(FEES, clinicA.getId(), doctor.getId())).andExpect(status().isUnauthorized());
    }

    private ResultActions putDefault(String token, Clinic clinic, DoctorProfile doctorProfile) throws Exception {
        return mockMvc.perform(put(FEES + "/default", clinic.getId(), doctorProfile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(AMOUNT_500));
    }

    private String operationsToken(Clinic clinic) {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        Account ops = accountRepository.save(new Account(
                "Ops " + suffix, "ops-" + suffix + "@example.com", passwordEncoder.encode("Str0ng!Pass"), "OP-" + suffix, null));
        roleAssignmentRepository.save(new RoleAssignment(ops, clinic, RoleAssignment.Role.Operations));
        return staffJwtService.issueToken(ops.getId());
    }
}
