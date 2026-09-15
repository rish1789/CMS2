package com.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Relocated here from {@code com.cms.identity.IdentityApplication} by
 * 002-patient-account-login: {@code @SpringBootApplication}'s implicit component scan
 * only covers its own package and below, so with the application class inside
 * {@code com.cms.identity} the sibling {@code com.cms.patient} and {@code com.cms.common}
 * packages (both new in this feature) were never scanned - a real bug, not just a test
 * config issue, since it meant none of this feature's beans were ever registered at
 * runtime either. Living at {@code com.cms} (the common parent of every module) fixes
 * this for good, not just for the packages that happen to exist today.
 */
@SpringBootApplication
public class CmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmsApplication.class, args);
    }
}
