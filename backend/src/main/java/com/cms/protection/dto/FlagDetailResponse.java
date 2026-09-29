package com.cms.protection.dto;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #2): `GET .../protection/flags/{flagId}`. */
public record FlagDetailResponse(FlagResponse flag, RecentActivityResponse recentActivity) {}
