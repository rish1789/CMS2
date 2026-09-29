package com.cms.booking.api;

import com.cms.booking.dto.ClinicBookingLimitOverrideHistoryEntryResponse;
import com.cms.booking.dto.ClinicBookingLimitOverrideResponse;
import com.cms.booking.dto.UpdateClinicBookingLimitOverrideRequest;
import com.cms.booking.service.ClinicBookingLimitOverrideService;
import com.cms.identity.account.config.SecurityConfig;
import java.util.List;
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
 * 060-booking-abuse-prevention (contracts/booking-protection.md #3, spec.md FR-004/FR-028, US5):
 * ClinicAdmin-only, clinic-scoped. Sits alongside {@code ClinicProtectionFlagController} in the
 * frontend's single "Booking Protection" admin area, though the data itself is stored in
 * {@code booking} (Decision 3 - it's read synchronously by the booking-limit check).
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/protection/limit-override")
public class ClinicBookingLimitOverrideController {

    private final ClinicBookingLimitOverrideService overrideService;

    public ClinicBookingLimitOverrideController(ClinicBookingLimitOverrideService overrideService) {
        this.overrideService = overrideService;
    }

    @GetMapping
    public ClinicBookingLimitOverrideResponse get(@PathVariable UUID clinicId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return overrideService.get(callerAccountId, clinicId);
    }

    @PutMapping
    public ClinicBookingLimitOverrideResponse update(
            @PathVariable UUID clinicId, @RequestBody UpdateClinicBookingLimitOverrideRequest request, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return overrideService.update(callerAccountId, clinicId, request.maxActiveAppointments(), callerAccountId.toString());
    }

    @DeleteMapping
    public void delete(@PathVariable UUID clinicId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        overrideService.delete(callerAccountId, clinicId, callerAccountId.toString());
    }

    @GetMapping("/history")
    public List<ClinicBookingLimitOverrideHistoryEntryResponse> history(@PathVariable UUID clinicId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return overrideService.history(callerAccountId, clinicId);
    }
}
