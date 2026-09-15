package com.cms.scheduling;

import java.util.UUID;

/** 013 FR-009: the named doctor has no active Role Assignment at the named clinic. */
public class DoctorNotStaffedAtClinicException extends RuntimeException {

    public DoctorNotStaffedAtClinicException(UUID doctorProfileId, UUID clinicId) {
        super("Doctor Profile " + doctorProfileId + " has no active Role Assignment at clinic " + clinicId);
    }
}
