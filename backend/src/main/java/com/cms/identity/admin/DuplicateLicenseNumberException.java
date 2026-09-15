package com.cms.identity.admin;

/**
 * 008 FR-007: an edited license number collides with a *different* Doctor Profile's
 * license number - the same global-uniqueness guarantee 007's onboarding dedup enforces.
 */
public class DuplicateLicenseNumberException extends RuntimeException {}
