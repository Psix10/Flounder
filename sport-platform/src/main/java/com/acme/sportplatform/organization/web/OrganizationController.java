package com.acme.sportplatform.organizations.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.acme.sportplatform.organizations.api.CreateOrganizationRequest;
import com.acme.sportplatform.organizations.api.OrganizationResponse;
import com.acme.sportplatform.organizations.application.OrganizationService;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('platform_admin') or hasAuthority('organizer')")
    public ResponseEntity<OrganizationResponse> createOrganization(
            @RequestBody @Valid CreateOrganizationRequest request
    ) {
        OrganizationResponse response = organizationService.createOrganization(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('platform_admin') or hasAuthority('organizer')")
    public ResponseEntity<OrganizationResponse> getOrganization(@PathVariable UUID id) {
        OrganizationResponse response = organizationService.getOrganization(id);
        return ResponseEntity.ok(response);
    }
}