package com.cms.patient.record.dto;

import java.util.List;

public record PatientClinicListResponse(
        List<PatientClinicSummaryResponse> clinics, int page, int pageSize, long totalCount) {}
