package com.acme.sportplatform.organizations.web;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.acme.sportplatform.organizations.api.*;
import com.acme.sportplatform.organizations.application.OrganizationMembershipService;
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/members")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class OrganizationMemberController {
    private final OrganizationMembershipService service;
    public OrganizationMemberController(OrganizationMembershipService service){this.service=service;}
    @PostMapping
    public ResponseEntity<OrganizationMemberResponse> assign(@PathVariable UUID organizationId,
            @RequestBody @Valid AddOrganizationMemberRequest request) {
        var result=service.assign(organizationId,request);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.member());
    }
    @GetMapping
    public List<OrganizationMemberResponse> list(@PathVariable UUID organizationId) {
        return service.list(organizationId);
    }
}
