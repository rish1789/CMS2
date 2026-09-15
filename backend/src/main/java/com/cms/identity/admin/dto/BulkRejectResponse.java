package com.cms.identity.admin.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partial-failure-safe result of a bulk reject: every id that succeeded, and a per-id reason
 * for every one that didn't (not found, or already verified) - a stale selection never fails
 * the whole batch.
 */
public record BulkRejectResponse(List<UUID> succeeded, Map<UUID, String> failed) {}
