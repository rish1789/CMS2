package com.cms.identity.admin.dto;

import java.util.List;
import java.util.UUID;

/** Body of a bulk permanent-delete action - no reason needed (unlike reject), it's an irreversible cleanup, not a decision that needs recording. */
public record BulkDeleteRequest(List<UUID> ids) {}
