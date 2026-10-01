package com.cms.booking.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.api.ClinicFeeController;
import com.cms.booking.dto.ClinicDoctorFeesResponse;
import com.cms.booking.dto.ClinicDoctorFeesResponse.AppointmentTypeFee;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.exception.DoctorProfileNotFoundException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.exception.InvalidFeeAmountException;
import com.cms.booking.service.ClinicFeeService;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 068-per-clinic-fees (contracts/clinic-fees-api.md): the clinic-scoped fee endpoints' success and error shapes. */
@WebMvcTest(controllers = ClinicFeeController.class)
@Import({BookingExceptionHandler.class, SecurityConfig.class, StaffAuthenticationEntryPoint.class, StaffJwtService.class})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class ClinicFeeControllerContractTest {

    private static final String FEES = "/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockitoBean
    private ClinicFeeService clinicFeeService;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID typeId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private ClinicDoctorFeesResponse fees(BigDecimal defaultFee, BigDecimal price) {
        BigDecimal effective = price != null ? price : defaultFee;
        return new ClinicDoctorFeesResponse(
                clinicId, doctorId, defaultFee, List.of(new AppointmentTypeFee(typeId, "Consultation", price, effective)));
    }

    private String bearer() {
        return "Bearer " + staffJwtService.issueToken(accountId);
    }

    @Test
    void getReturnsTheClinicsDefaultAndTypePrices() throws Exception {
        when(clinicFeeService.get(accountId, clinicId, doctorId)).thenReturn(fees(new BigDecimal("500.00"), null));

        mockMvc.perform(get(FEES, clinicId, doctorId).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicId").value(clinicId.toString()))
                .andExpect(jsonPath("$.doctorProfileId").value(doctorId.toString()))
                .andExpect(jsonPath("$.defaultFee").value(500.00))
                .andExpect(jsonPath("$.appointmentTypes[0].appointmentTypeId").value(typeId.toString()))
                .andExpect(jsonPath("$.appointmentTypes[0].name").value("Consultation"))
                .andExpect(jsonPath("$.appointmentTypes[0].price").isEmpty())
                .andExpect(jsonPath("$.appointmentTypes[0].effectiveFee").value(500.00));
    }

    @Test
    void putDefaultReturnsTheUpdatedFees() throws Exception {
        when(clinicFeeService.setDefault(eq(accountId), eq(clinicId), eq(doctorId), any()))
                .thenReturn(fees(new BigDecimal("500.00"), null));

        mockMvc.perform(put(FEES + "/default", clinicId, doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultFee").value(500.00));
    }

    @Test
    void putTypePriceReturnsTheUpdatedFees() throws Exception {
        when(clinicFeeService.setTypePrice(eq(accountId), eq(clinicId), eq(doctorId), eq(typeId), any()))
                .thenReturn(fees(new BigDecimal("500.00"), new BigDecimal("800.00")));

        mockMvc.perform(put(FEES + "/appointment-types/{typeId}", clinicId, doctorId, typeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":800.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentTypes[0].price").value(800.00))
                .andExpect(jsonPath("$.appointmentTypes[0].effectiveFee").value(800.00));
    }

    @Test
    void deleteTypePriceReturnsTheUpdatedFees() throws Exception {
        when(clinicFeeService.removeTypePrice(accountId, clinicId, doctorId, typeId))
                .thenReturn(fees(new BigDecimal("500.00"), null));

        mockMvc.perform(delete(FEES + "/appointment-types/{typeId}", clinicId, doctorId, typeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentTypes[0].price").isEmpty());
    }

    @Test
    void anInvalidAmountIs400() throws Exception {
        when(clinicFeeService.setDefault(eq(accountId), eq(clinicId), eq(doctorId), any()))
                .thenThrow(new InvalidFeeAmountException());

        mockMvc.perform(put(FEES + "/default", clinicId, doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FEE_AMOUNT"));
    }

    @Test
    void aCallerWhoIsNotThisClinicsAdminIs403() throws Exception {
        when(clinicFeeService.setDefault(eq(accountId), eq(clinicId), eq(doctorId), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(put(FEES + "/default", clinicId, doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void anUnknownDoctorIs404() throws Exception {
        when(clinicFeeService.get(accountId, clinicId, doctorId)).thenThrow(new DoctorProfileNotFoundException(doctorId));

        mockMvc.perform(get(FEES, clinicId, doctorId).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void noTokenIs401() throws Exception {
        mockMvc.perform(get(FEES, clinicId, doctorId)).andExpect(status().isUnauthorized());
    }
}
