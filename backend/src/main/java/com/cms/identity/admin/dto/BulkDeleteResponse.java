package com.cms.identity.admin.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partial-failure-safe result of a bulk permanent delete: every id that was actually deleted,
 * and a per-id reason for every one that wasn't (not found, not rejected, or blocked by real
 * attached data) - a stale selection or one record with real activity never fails the whole batch.
 */
public record BulkDeleteResponse(List<UUID> succeeded, Map<UUID, String> failed) {}
