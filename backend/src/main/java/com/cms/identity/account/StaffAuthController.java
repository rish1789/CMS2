package com.cms.identity.account;

import com.cms.identity.account.dto.StaffLoginRequest;
import com.cms.identity.account.dto.StaffLoginResponse;
import com.cms.identity.api.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for POST /api/v1/staff/login. All resolution logic (Super Admin precedence,
 * email/staff-code lookup, credential matching, JWT issuance) lives in {@link StaffAuthService} -
 * see its Javadoc for the business rules and feature history.
 */
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff Auth", description = "The \"Clinic Portal\" login shared by ClinicAdmin/Doctor/Operations staff and the Super Admin.")
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    public StaffAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @Operation(
            summary = "Log in as staff (or Super Admin)",
            description =
                    "Authenticates by email OR staff code, plus password. The configured Super Admin "
                            + "credential is checked first; on no match, falls back to a staff Account lookup "
                            + "by email, then by staff code. Every failure mode (unknown identifier, wrong "
                            + "password, wrong Super Admin password) returns the identical 401 shape, so no "
                            + "information leaks about which check failed. On success, issues a JWT scoped to "
                            + "the resolved identity (\"STAFF\" or \"SUPER_ADMIN\") - send it as "
                            + "`Authorization: Bearer <token>` on subsequent requests.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Authenticated - a JWT and the resolved identity are returned.",
                content = @Content(schema = @Schema(implementation = StaffLoginResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "`identifier` or `password` was missing/blank.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Unknown identifier, or the password didn't match (identical body either way).",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected server error (see the app-wide error-handling fallback).",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public StaffLoginResponse login(@Valid @RequestBody StaffLoginRequest request) {
        return staffAuthService.login(request);
    }
}
