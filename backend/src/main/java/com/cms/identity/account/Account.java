package com.cms.identity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The staff login identity. email and staffCode are both globally unique across the
 * platform (FR-012, FR-013) - enforced here via {@code @Table} unique constraints
 * (translated by Hibernate into the same DB-level constraints the Flyway migration
 * already creates directly) so the invariant is closed at the data layer, not just
 * in application code (Constitution Principle IV).
 */
@Entity
@Table(
        name = "account",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_account_email", columnNames = "email"),
            @UniqueConstraint(name = "uq_account_staff_code", columnNames = "staff_code")
        })
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "staff_code", nullable = false)
    private String staffCode;

    @Column private String mobile;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
        // JPA
    }

    public Account(String name, String email, String passwordHash, String staffCode, String mobile) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.staffCode = staffCode;
        this.mobile = mobile;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getStaffCode() {
        return staffCode;
    }

    public String getMobile() {
        return mobile;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
