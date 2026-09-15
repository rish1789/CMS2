package com.cms.identity.doctor.dto;

import java.util.List;

public record DoctorListResponse(List<DoctorSummaryResponse> doctors, int page, int pageSize, long totalCount) {}
