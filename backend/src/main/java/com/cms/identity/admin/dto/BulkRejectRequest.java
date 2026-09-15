package com.cms.identity.admin.dto;

import java.util.List;
import java.util.UUID;

/**
 * Body of a bulk reject action - one shared reason applied to every id in the batch, matching
 * the "these N are all spam/duplicates" use case the console's Reject-selected flow is for.
 */
public record BulkRejectRequest(List<UUID> ids, String reasonCode, String detail) {}
