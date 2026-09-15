package com.cms.identity.api.dto;

import java.util.UUID;

public record RegisterClinicResponse(UUID clinicId, String clinicName, boolean verified, AdminInfo admin) {

    public record AdminInfo(UUID accountId, String email, String staffCode) {}
}
