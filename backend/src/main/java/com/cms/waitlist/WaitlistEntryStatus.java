package com.cms.waitlist;

public enum WaitlistEntryStatus {
    WAITING,
    OFFERED,
    /** 032: a successful self-service claim converted this entry into a confirmed Booking. Terminal. */
    CLAIMED,
    /** 032: the offer lapsed with no claim, or the patient explicitly declined it. Terminal. */
    EXPIRED
}
