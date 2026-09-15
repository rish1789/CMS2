package com.cms.booking.dto;

import com.cms.identity.doctor.DoctorProfile;
import java.util.UUID;

/**
 * Patient-facing doctor summary, backing the doctor picker that replaces the raw
 * {@code doctorProfileId} text field on {@code JoinWaitlistForm}. Omits staffCode (an internal
 * clinic-ops detail a patient has no reason to see) - specialization and experienceYears
 * disambiguate two same-named doctors instead, mirroring what Discovery search already shows.
 */
public record PatientDoctorSummaryResponse(UUID doctorProfileId, String name, String specialization, int experienceYears) {

    public static PatientDoctorSummaryResponse from(DoctorProfile doctorProfile) {
        return new PatientDoctorSummaryResponse(
                doctorProfile.getId(),
                doctorProfile.getAccount().getName(),
                doctorProfile.getSpecialization(),
                doctorProfile.getExperienceYears());
    }
}
