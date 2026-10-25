package com.acme.sportplatform.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.identity.OperatorEligibilityLookup;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;

@Service
@Transactional(readOnly = true)
public class OperatorEligibilityLookupService implements OperatorEligibilityLookup {

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public OperatorEligibilityLookupService(
            UserRepository userRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Override
    public boolean isActiveOperator(UUID userId) {
        if (userId == null) {
            return false;
        }

        return userRepository.findById(userId)
                .filter(user -> "active".equalsIgnoreCase(user.getStatus()))
                .map(user -> userRoleAssignmentRepository
                        .findGlobalRoleCodesByUserId(user.getId())
                        .stream()
                        .anyMatch(role -> "OPERATOR".equalsIgnoreCase(role)))
                .orElse(false);
    }
}