package com.cms.identity.account;

import com.cms.identity.clinic.Clinic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "role_assignment")
public class RoleAssignment {

    public enum Role {
        ClinicAdmin,
        Doctor,
        Operations
    }

    /** Employee deactivation modal: why this Role Assignment was deactivated. */
    public enum DeactivationReason {
        RESIGNED,
        SERVICE_NOT_REQUIRED
    }

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "deactivation_reason")
    private DeactivationReason deactivationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RoleAssignment() {
        // JPA
    }

    public RoleAssignment(Account account, Clinic clinic, Role role) {
        this.account = account;
        this.clinic = clinic;
        this.role = role;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    /** 005-last-active-clinicadmin-protection: the caller MUST have already checked the last-active-ClinicAdmin invariant. */
    public void deactivate(DeactivationReason reason) {
        this.active = false;
        this.deactivationReason = reason;
    }

    public DeactivationReason getDeactivationReason() {
        return deactivationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
