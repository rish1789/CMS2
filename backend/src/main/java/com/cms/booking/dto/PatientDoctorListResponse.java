package com.cms.booking.dto;

import java.util.List;

public record PatientDoctorListResponse(List<PatientDoctorSummaryResponse> doctors, int page, int pageSize, long totalCount) {}
