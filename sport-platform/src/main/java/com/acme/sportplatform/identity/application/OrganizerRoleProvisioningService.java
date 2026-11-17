package com.acme.sportplatform.identity.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.OrganizerRoleProvisioning;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Service
public class OrganizerRoleProvisioningService
        implements OrganizerRoleProvisioning {

    private final AssignRoleToUserUseCase assignRole;

    @PersistenceContext
    private EntityManager entityManager;

    public OrganizerRoleProvisioningService(
            AssignRoleToUserUseCase assignRole
    ) {
        this.assignRole = assignRole;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void grantToActiveUser(UUID userId) {
        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                     instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId() == null
                || authentication.getAuthorities().stream().noneMatch(
                        authority -> "ROLE_PLATFORM_ADMIN".equals(
                                authority.getAuthority()
                        )
                )) {
            throw new AccessDeniedException(
                    "Identified platform administrator is required"
            );
        }

        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Applicant user ID is required"
            );
        }

        UserEntity user = entityManager.find(
                UserEntity.class,
                userId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Applicant user not found"
            );
        }

        entityManager.refresh(
                user,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an active applicant can become an organizer"
            );
        }

        assignRole.execute(userId, "organizer");
        entityManager.flush();
    }
}