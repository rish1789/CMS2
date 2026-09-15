package com.cms.identity.staff;

/**
 * Thrown when deactivating a ClinicAdmin Role Assignment would leave its clinic with
 * zero active ClinicAdmins (FR-003, FR-004). No override exists for any role, including
 * Super Admin - Super Admin has no path to this endpoint at all (see
 * com.cms.identity.account.SecurityConfig / com.cms.identity.admin.SuperAdminSecurityConfig).
 */
public class LastActiveClinicAdminException extends RuntimeException {

    public LastActiveClinicAdminException() {
        super("Cannot deactivate the clinic's last active ClinicAdmin");
    }
}
