package com.cms.identity.api;

import com.cms.identity.api.dto.RegisterClinicRequest;
import com.cms.identity.api.dto.RegisterClinicResponse;
import com.cms.identity.clinic.ClinicRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinics")
public class ClinicRegistrationController {

    private final ClinicRegistrationService clinicRegistrationService;

    public ClinicRegistrationController(ClinicRegistrationService clinicRegistrationService) {
        this.clinicRegistrationService = clinicRegistrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterClinicResponse> register(@Valid @RequestBody RegisterClinicRequest request) {
        RegisterClinicResponse response = clinicRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
