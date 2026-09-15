package com.cms.patient.api;

import com.cms.patient.account.PatientAccountService;
import com.cms.patient.api.dto.ErrorResponse;
import com.cms.patient.api.dto.LoginRequest;
import com.cms.patient.api.dto.LoginResponse;
import com.cms.patient.api.dto.SignupRequest;
import com.cms.patient.api.dto.SignupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Implements contracts/patient-account.md's two public endpoints. */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patient Account", description = "Self-service signup and login for the global Patient Account identity - entirely separate from staff auth (no shared credentials or JWT signing key).")
public class PatientAccountController {

    private final PatientAccountService patientAccountService;

    public PatientAccountController(PatientAccountService patientAccountService) {
        this.patientAccountService = patientAccountService;
    }

    @Operation(
            summary = "Create a Patient Account",
            description =
                    "Registers a new, global (not clinic-scoped) Patient Account identity. `mobile` is "
                            + "optional but, if provided, must match the Indian numbering plan. The password "
                            + "must satisfy the platform's password policy - a violation lists every failed "
                            + "rule, not just the first. Email uniqueness is enforced independently of the "
                            + "staff Account system (no cross-system collision checks). On success, use POST "
                            + "/api/v1/patients/login to obtain a session token - signup itself does not "
                            + "return one.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Account created.",
                content = @Content(schema = @Schema(implementation = SignupResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description =
                        "A required field was missing, the password failed policy (see `failedRules`), or "
                                + "`mobile` didn't match the Indian numbering plan.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "`email` is already registered to another Patient Account.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "The account could not be persisted (a non-uniqueness database failure).",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = patientAccountService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Log in to a Patient Account",
            description =
                    "Authenticates by email and password. An unknown email and a correct-email-wrong-password "
                            + "both return the identical 401 shape, so no information leaks about whether a "
                            + "given email is registered. On success, issues a JWT scoped to this Patient "
                            + "Account - send it as `Authorization: Bearer <token>` on subsequent "
                            + "/api/v1/patients/** requests that require authentication.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Authenticated - a JWT and the account's identity are returned.",
                content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "`email` or `password` was missing/blank.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Unknown email, or the password didn't match (identical body either way).",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected server error (see the app-wide error-handling fallback).",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(patientAccountService.authenticate(request));
    }
}
