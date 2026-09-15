package com.cms.notification;

/**
 * 037 FR-006: the pluggable "send" contract - deliberately unaware of {@link NotificationEvent}
 * or {@link com.cms.patient.account.PatientAccount}, so a future real-provider
 * implementation is a drop-in replacement with no change to the caller.
 */
public interface NotificationSender {

    void send(String channel, String recipient, String message);
}
