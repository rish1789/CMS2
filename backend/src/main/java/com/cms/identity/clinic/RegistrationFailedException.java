package com.cms.identity.clinic;

/**
 * Wraps any unexpected failure during the atomic create transaction (after all
 * validation has passed), per contracts/register-clinic.md's 500 REGISTRATION_FAILED
 * response - the transaction itself rolls back automatically via {@code @Transactional},
 * this exception only carries the failure across the service/controller boundary.
 */
public class RegistrationFailedException extends RuntimeException {

    public RegistrationFailedException(Throwable cause) {
        super("Registration failed", cause);
    }
}
