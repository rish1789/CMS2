package com.cms.identity.doctor.dto;

import com.cms.identity.doctor.DoctorProfile;
import java.util.UUID;

public record DoctorSummaryResponse(UUID doctorProfileId, String name, String staffCode, String specialization) {

    public static DoctorSummaryResponse from(DoctorProfile doctorProfile) {
        return new DoctorSummaryResponse(
                doctorProfile.getId(),
                doctorProfile.getAccount().getName(),
                doctorProfile.getAccount().getStaffCode(),
                doctorProfile.getSpecialization());
    }
}
