package com.cms.identity.account.config;

import com.cms.identity.account.repository.RoleAssignmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 075-login-hardening (D-3C-1): a staff session lives while the account holds at least one active role. */
@Component
public class ActiveRoleStaffSessionPolicy implements StaffSessionPolicy {

    private final RoleAssignmentRepository roleAssignmentRepository;

    public ActiveRoleStaffSessionPolicy(RoleAssignmentRepository roleAssignmentRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Override
    public boolean allows(UUID accountId) {
        return roleAssignmentRepository.existsByAccount_IdAndActiveTrue(accountId);
    }
}
