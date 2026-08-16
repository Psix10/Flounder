package com.acme.sportplatform.events.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {
    boolean existsByPublicSlug(String publicSlug);
}