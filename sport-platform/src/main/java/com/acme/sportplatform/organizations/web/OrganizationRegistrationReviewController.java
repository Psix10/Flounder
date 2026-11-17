package com.acme.sportplatform.organizations.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.organizations.api.OrganizationRegistrationReviewResponse;
import com.acme.sportplatform.organizations.application.OrganizationRegistrationReviewService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/organization-registration-requests")
public class OrganizationRegistrationReviewController {

    private final OrganizationRegistrationReviewService service;

    public OrganizationRegistrationReviewController(
            OrganizationRegistrationReviewService service
    ) {
        this.service = service;
    }

    public record RejectRequest(
            @NotBlank
            @Size(max = 2000)
            String reason
    ) { }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<OrganizationRegistrationReviewResponse> list(
            @RequestParam(name = "status", defaultValue = "PENDING")
            String status,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "50")
            int size
    ) {
        return service.list(status, page, size);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<OrganizationRegistrationReviewResponse> mine(
            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "50")
            int size
    ) {
        return service.mine(page, size);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public OrganizationRegistrationReviewResponse approve(
            @PathVariable("id") UUID id
    ) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public OrganizationRegistrationReviewResponse reject(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectRequest request
    ) {
        return service.reject(id, request.reason());
    }
}