package com.cms.identity.staff;

/**
 * 007 FR-002a: the submitted license number matches an existing Doctor Profile, but the
 * submitted specialization doesn't match it (case-insensitive, trimmed) - a conflict,
 * not the same doctor re-onboarding.
 */
public class SpecializationMismatchException extends RuntimeException {}
