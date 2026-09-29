package com.cms.identity.admin.exception;

import java.util.UUID;

/**
 * Defensive only - every clinic (verified or not) is expected to have exactly one active
 * ClinicAdmin RoleAssignment created at registration time (ClinicRegistrationService), the
 * same invariant {@code RoleAssignmentRepository.deleteByClinic_Id}'s own javadoc documents.
 * Onboarding can never create a second ClinicAdmin for a clinic (StaffOnboardingService
 * rejects that role value outright), so this should only ever fire against data corruption.
 */
public class ClinicAdminAccountNotFoundException extends RuntimeException {

    public ClinicAdminAccountNotFoundException(UUID clinicId) {
        super("No active ClinicAdmin account found for clinic " + clinicId);
    }
}
