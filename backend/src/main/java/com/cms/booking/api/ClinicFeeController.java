package com.cms.booking.api;

import com.cms.booking.dto.ClinicDoctorFeesResponse;
import com.cms.booking.dto.SetClinicFeeRequest;
import com.cms.booking.service.ClinicFeeService;
import com.cms.identity.account.config.SecurityConfig;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 068-per-clinic-fees (contracts/clinic-fees-api.md): a doctor's prices at one clinic. On the
 * staff chain - {@code /api/v1/clinics/**} already requires a staff token.
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees")
public class ClinicFeeController {

    private final ClinicFeeService clinicFeeService;

    public ClinicFeeController(ClinicFeeService clinicFeeService) {
        this.clinicFeeService = clinicFeeService;
    }

    @GetMapping
    public ClinicDoctorFeesResponse get(
            @PathVariable UUID clinicId, @PathVariable UUID doctorProfileId, Authentication authentication) {
        return clinicFeeService.get(SecurityConfig.currentAccountId(authentication), clinicId, doctorProfileId);
    }

    @PutMapping("/default")
    public ClinicDoctorFeesResponse setDefault(
            @PathVariable UUID clinicId,
            @PathVariable UUID doctorProfileId,
            @RequestBody SetClinicFeeRequest request,
            Authentication authentication) {
        return clinicFeeService.setDefault(
                SecurityConfig.currentAccountId(authentication), clinicId, doctorProfileId, request.amount());
    }

    @PutMapping("/appointment-types/{appointmentTypeId}")
    public ClinicDoctorFeesResponse setTypePrice(
            @PathVariable UUID clinicId,
            @PathVariable UUID doctorProfileId,
            @PathVariable UUID appointmentTypeId,
            @RequestBody SetClinicFeeRequest request,
            Authentication authentication) {
        return clinicFeeService.setTypePrice(
                SecurityConfig.currentAccountId(authentication), clinicId, doctorProfileId, appointmentTypeId, request.amount());
    }

    @DeleteMapping("/appointment-types/{appointmentTypeId}")
    public ClinicDoctorFeesResponse removeTypePrice(
            @PathVariable UUID clinicId,
            @PathVariable UUID doctorProfileId,
            @PathVariable UUID appointmentTypeId,
            Authentication authentication) {
        return clinicFeeService.removeTypePrice(
                SecurityConfig.currentAccountId(authentication), clinicId, doctorProfileId, appointmentTypeId);
    }
}
