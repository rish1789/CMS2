package com.cms.booking.api;

import com.cms.booking.dto.FrontDeskWalkInRequest;
import com.cms.booking.dto.FrontDeskWalkInResponse;
import com.cms.booking.service.FrontDeskWalkInService;
import com.cms.identity.account.config.SecurityConfig;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 063-front-desk-walk-in (contract section 1): the front-desk walk-in registration, clinic-level (not per session). */
@RestController
public class FrontDeskWalkInController {

    private final FrontDeskWalkInService frontDeskWalkInService;

    public FrontDeskWalkInController(FrontDeskWalkInService frontDeskWalkInService) {
        this.frontDeskWalkInService = frontDeskWalkInService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/walk-ins")
    public ResponseEntity<FrontDeskWalkInResponse> register(
            @PathVariable UUID clinicId, @Valid @RequestBody FrontDeskWalkInRequest request, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        FrontDeskWalkInService.Registration registration = frontDeskWalkInService.register(
                callerAccountId,
                clinicId,
                new FrontDeskWalkInService.RegisterInput(
                        request.sessionId(),
                        request.patientId(),
                        request.patientName(),
                        request.patientPhone(),
                        request.patientEmail(),
                        request.appointmentTypeId(),
                        request.visitReason(),
                        request.visitReasonDetail(),
                        request.confirmDuplicate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(FrontDeskWalkInResponse.of(registration));
    }
}
