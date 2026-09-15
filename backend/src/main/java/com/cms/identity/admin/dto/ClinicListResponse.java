package com.cms.identity.admin.dto;

import java.util.List;

public record ClinicListResponse(List<ClinicSummaryResponse> clinics, int page, int pageSize, long totalCount) {}
