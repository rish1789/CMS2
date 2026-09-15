package com.cms.discovery;

import java.util.UUID;

/**
 * 035: one row per eligible (doctor, clinic) pairing returned by public discovery search.
 * Built directly by the JPQL projection in {@link DiscoveryResultRepository#search}, so its
 * constructor signature must match that query's {@code SELECT new
 * com.cms.discovery.DiscoveryResult(...)} clause exactly.
 *
 * <p>patient-search-advanced-filtering: {@code experienceYears} and {@code clinicCity} are new -
 * 035's original FR-006/FR-007 deliberately kept this minimal (no experience years) as a privacy
 * choice, but this feature explicitly requires filtering and displaying by experience, which
 * supersedes that constraint. License number, verification flags, and account credentials remain
 * excluded - nothing about exposing years-of-experience or a clinic's city implies those should be.
 */
public class DiscoveryResult {

    private final UUID doctorProfileId;
    private final String doctorName;
    private final String specialization;
    private final int experienceYears;
    private final UUID clinicId;
    private final String clinicName;
    private final String clinicAddress;
    private final String clinicCity;

    public DiscoveryResult(
            UUID doctorProfileId,
            String doctorName,
            String specialization,
            int experienceYears,
            UUID clinicId,
            String clinicName,
            String clinicAddress,
            String clinicCity) {
        this.doctorProfileId = doctorProfileId;
        this.doctorName = doctorName;
        this.specialization = specialization;
        this.experienceYears = experienceYears;
        this.clinicId = clinicId;
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
        this.clinicCity = clinicCity;
    }

    public UUID getDoctorProfileId() {
        return doctorProfileId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getClinicName() {
        return clinicName;
    }

    public String getClinicAddress() {
        return clinicAddress;
    }

    public String getClinicCity() {
        return clinicCity;
    }
}
