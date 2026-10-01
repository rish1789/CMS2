package com.cms.identity.account.config;

import java.util.UUID;

/**
 * 075-login-hardening (D-3C-1): whether a validly signed staff token may still act. The staff JWT
 * filter consults it on every request, so a staff member deactivated at their last clinic loses
 * their existing sessions at once rather than when the 12-hour token expires.
 */
public interface StaffSessionPolicy {

    boolean allows(UUID accountId);
}
