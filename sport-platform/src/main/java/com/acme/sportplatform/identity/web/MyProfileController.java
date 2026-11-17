package com.acme.sportplatform.identity.web;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.identity.api.MyProfileResponse;
import com.acme.sportplatform.identity.api.UpdateMyProfileRequest;
import com.acme.sportplatform.identity.application.MyProfileService;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile/me")
@PreAuthorize("isAuthenticated()")
public class MyProfileController {

    private final MyProfileService profiles;

    public MyProfileController(MyProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public MyProfileResponse get(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return profiles.get(currentUserId(principal));
    }

    @PatchMapping
    public MyProfileResponse update(
            @AuthenticationPrincipal PlatformUserPrincipal principal,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return profiles.update(currentUserId(principal), request);
    }

    private UUID currentUserId(PlatformUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException(
                    "Authenticated platform principal is required"
            );
        }

        return principal.getUserId();
    }
}