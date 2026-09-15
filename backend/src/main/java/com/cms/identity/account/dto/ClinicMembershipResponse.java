package com.cms.identity.account.dto;

import com.cms.identity.account.RoleAssignment;
import java.util.UUID;

/** 041-staff-console-pickers FR-001: one row per active RoleAssignment the caller holds. */
public record ClinicMembershipResponse(UUID clinicId, String name, String address, String role) {

    public static ClinicMembershipResponse from(RoleAssignment roleAssignment) {
        return new ClinicMembershipResponse(
                roleAssignment.getClinic().getId(),
                roleAssignment.getClinic().getName(),
                roleAssignment.getClinic().getAddress(),
                roleAssignment.getRole().name());
    }
}
