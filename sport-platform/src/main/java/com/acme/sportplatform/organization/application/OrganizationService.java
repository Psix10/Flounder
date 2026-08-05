package com.acme.sportplatform.organizations.application;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.organizations.api.CreateOrganizationRequest;
import com.acme.sportplatform.organizations.api.OrganizationResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        OrganizationEntity entity = new OrganizationEntity();
        entity.setType(request.type());
        entity.setName(request.name());
        entity.setLegalName(request.legalName());
        entity.setInn(request.inn());
        entity.setContactEmail(request.contactEmail());
        entity.setContactPhone(request.contactPhone());
        entity.setMeta("{}");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        OrganizationEntity saved = organizationRepository.save(entity);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID id) {
        OrganizationEntity entity = organizationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "organizations.organization_not_found",
                        "Organization not found"
                ));

        return mapToResponse(entity);
    }

    private OrganizationResponse mapToResponse(OrganizationEntity entity) {
        return new OrganizationResponse(
                entity.getId(),
                entity.getType(),
                entity.getName(),
                entity.getLegalName(),
                entity.getInn(),
                entity.getContactEmail(),
                entity.getContactPhone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}