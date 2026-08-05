package com.acme.sportplatform.organizations.infrastructure.jpa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
}