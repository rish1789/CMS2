package com.cms.protection.dto;

import java.util.List;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #2): `GET .../protection/flags`. */
public record FlagListResponse(List<FlagResponse> flags, int page, int pageSize, long totalCount) {}
