package com.cms.booking.dto;

import java.util.List;

public record QueueSessionListResponse(List<QueueSessionResponse> sessions, int page, int pageSize, long totalCount) {}
