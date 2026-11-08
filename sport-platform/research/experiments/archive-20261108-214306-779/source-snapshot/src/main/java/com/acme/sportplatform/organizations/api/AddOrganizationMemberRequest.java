package com.acme.sportplatform.organizations.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AddOrganizationMemberRequest(
        @NotNull UUID userId,
        @NotBlank @Pattern(regexp = "OWNER|ADMIN") String memberRole
) {
}
