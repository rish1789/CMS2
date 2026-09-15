package com.cms.booking;

import com.cms.booking.dto.AppointmentTypeResponse;
import com.cms.booking.dto.CreateAppointmentTypeRequest;
import com.cms.booking.dto.SetDefaultFeeRequest;
import com.cms.identity.account.SecurityConfig;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctors/{doctorProfileId}")
public class BookingController {

    private final AppointmentTypeService appointmentTypeService;

    public BookingController(AppointmentTypeService appointmentTypeService) {
        this.appointmentTypeService = appointmentTypeService;
    }

    @PostMapping("/appointment-types")
    public ResponseEntity<AppointmentTypeResponse> create(
            @PathVariable UUID doctorProfileId,
            @Valid @RequestBody CreateAppointmentTypeRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        AppointmentType appointmentType =
                appointmentTypeService.create(callerAccountId, doctorProfileId, request.name(), request.feeOverride());
        return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentTypeResponse.of(appointmentType));
    }

    @GetMapping("/appointment-types")
    public List<AppointmentTypeResponse> list(@PathVariable UUID doctorProfileId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return appointmentTypeService.list(callerAccountId, doctorProfileId).stream()
                .map(AppointmentTypeResponse::of)
                .toList();
    }

    @PutMapping("/default-fee")
    public SetDefaultFeeResponse setDefaultFee(
            @PathVariable UUID doctorProfileId,
            @Valid @RequestBody SetDefaultFeeRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        DoctorDefaultFee doctorDefaultFee =
                appointmentTypeService.setDefaultFee(callerAccountId, doctorProfileId, request.amount());
        return new SetDefaultFeeResponse(doctorDefaultFee.getDoctorProfile().getId(), doctorDefaultFee.getAmount());
    }

    public record SetDefaultFeeResponse(UUID doctorProfileId, BigDecimal amount) {}
}
