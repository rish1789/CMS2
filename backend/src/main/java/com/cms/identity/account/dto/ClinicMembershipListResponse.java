package com.cms.identity.account.dto;

import java.util.List;

public record ClinicMembershipListResponse(
        List<ClinicMembershipResponse> clinics, int page, int pageSize, long totalCount) {}
