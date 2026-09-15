package com.cms.patient.record.dto;

import java.util.List;

public record PatientSearchListResponse(
        List<PatientSearchResultResponse> patients, int page, int pageSize, long totalCount) {}
