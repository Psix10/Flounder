package com.acme.sportplatform.organizations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.OrganizerRoleProvisioning;
import com.acme.sportplatform.identity.UserAccountLookup;
import com.acme.sportplatform.organizations.api.OrganizationRegistrationReviewResponse;
import com.acme.sportplatform.organizations.infrastructure.jdbc.OrganizationMembershipStore;
import com.acme.sportplatform.organizations.infrastructure.jdbc.OrganizationRegistrationReviewStore;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;

@Service
public class OrganizationRegistrationReviewService {

    private final OrganizationRegistrationReviewStore requests;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipStore memberships;
    private final OrganizerRoleProvisioning roles;
    private final UserAccountLookup users;
    private final OrganizationInnGuard innGuard;

    public OrganizationRegistrationReviewService(
            OrganizationRegistrationReviewStore requests,
            OrganizationRepository organizations,
            OrganizationMembershipStore memberships,
            OrganizerRoleProvisioning roles,
            UserAccountLookup users,
            OrganizationInnGuard innGuard
    ) {
        this.requests = requests;
        this.organizations = organizations;
        this.memberships = memberships;
        this.roles = roles;
        this.users = users;
        this.innGuard = innGuard;
    }

    @Transactional
    public List<OrganizationRegistrationReviewResponse> list(
            String status,
            int page,
            int size
    ) {
        administrator();

        if (!Set.of("PENDING", "APPROVED", "REJECTED").contains(
                Objects.toString(status, "")
        )) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Expected status PENDING, APPROVED or REJECTED"
            );
        }

        return requests.list(
                status,
                size,
                offset(page, size)
        );
    }

    @Transactional
    public List<OrganizationRegistrationReviewResponse> mine(
            int page,
            int size
    ) {
        UUID actor = authenticatedUser();

        return requests.listForApplicant(
                actor,
                size,
                offset(page, size)
        );
    }

    @Transactional
    public OrganizationRegistrationReviewResponse approve(UUID id) {
        UUID actor = administrator();
        var request = locked(id);

        if ("APPROVED".equals(request.status())) {
            if (request.organizationId() == null) {
                throw error(
                        HttpStatus.CONFLICT,
                        "Legacy approval has no organization link; manual reconciliation is required"
                );
            }

            return request;
        }

        requirePending(request);

        String inn = innGuard.requireAvailable(request.inn());

        // All effects remain in this transaction until approval completes.
        roles.grantToActiveUser(request.applicantUserId());

        var now = OffsetDateTime.now(ZoneOffset.UTC);

        var organization = new OrganizationEntity();
        organization.setType(request.organizationType());
        organization.setName(request.organizationName());
        organization.setLegalName(request.legalName());
        organization.setInn(inn);
        organization.setContactEmail(request.contactEmail());
        organization.setContactPhone(request.contactPhone());
        organization.setMeta("{}");
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);

        organization = organizations.saveAndFlush(organization);

        UUID organizationId = organization.getId();

        var owner = memberships.insert(
                organizationId,
                request.applicantUserId(),
                "OWNER",
                actor
        );

        if (owner.isEmpty()) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Owner membership could not be created"
            );
        }

        return requests.complete(
                id,
                "APPROVED",
                null,
                organizationId,
                actor,
                now
        );
    }

    @Transactional
    public OrganizationRegistrationReviewResponse reject(
            UUID id,
            String reason
    ) {
        UUID actor = administrator();

        String normalizedReason = reason == null ? "" : reason.trim();

        if (normalizedReason.isEmpty()
                || normalizedReason.length() > 2000) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason must contain 1..2000 characters"
            );
        }

        var request = locked(id);

        if ("REJECTED".equals(request.status())
                && normalizedReason.equals(request.rejectionReason())) {
            return request;
        }

        requirePending(request);

        return requests.complete(
                id,
                "REJECTED",
                normalizedReason,
                null,
                actor,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private OrganizationRegistrationReviewResponse locked(UUID id) {
        if (id == null) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Registration ID is required"
            );
        }

        return requests.lock(id)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "Organization registration request not found"
                ));
    }

    private void requirePending(
            OrganizationRegistrationReviewResponse request
    ) {
        if (!"PENDING".equals(request.status())) {
            throw error(
                    HttpStatus.CONFLICT,
                    "The request has already been reviewed"
            );
        }
    }

    private UUID administrator() {
        UUID actor = authenticatedUser();

        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication.getAuthorities().stream().noneMatch(
                authority -> "ROLE_PLATFORM_ADMIN".equals(
                        authority.getAuthority()
                )
        )) {
            throw new AccessDeniedException(
                    "Platform administrator access is required"
            );
        }

        if (users.lock(actor)
                .filter(UserAccountLookup.Account::active)
                .isEmpty()) {
            throw new AccessDeniedException(
                    "An active administrator account is required"
            );
        }

        return actor;
    }

    private UUID authenticatedUser() {
        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                     instanceof AuthenticatedUserPrincipal principal)
                || principal.getUserId() == null) {
            throw new AccessDeniedException(
                    "Authenticated platform principal is required"
            );
        }

        return principal.getUserId();
    }

    private int offset(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Page must be 0..1000000 and size must be 1..100"
            );
        }

        return page * size;
    }

    private ResponseStatusException error(
            HttpStatus status,
            String message
    ) {
        return new ResponseStatusException(status, message);
    }
}