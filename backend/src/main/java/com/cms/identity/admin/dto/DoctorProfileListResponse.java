package com.cms.identity.admin.dto;

import java.util.List;

public record DoctorProfileListResponse(
        List<DoctorProfileSummaryResponse> doctors, int page, int pageSize, long totalCount) {}
