package com.acme.sportplatform.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.api.MyProfileResponse;
import com.acme.sportplatform.identity.api.UpdateMyProfileRequest;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;

@Service
public class MyProfileService {

    private final ProfileRepository profiles;

    public MyProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public MyProfileResponse get(UUID userId) {
        return toResponse(findProfile(userId));
    }

    @Transactional
    public MyProfileResponse update(
            UUID userId,
            UpdateMyProfileRequest request
    ) {
        ProfileEntity profile = findProfile(userId);

        profile.setGender(request.gender());

        ProfileEntity saved = profiles.saveAndFlush(profile);

        return toResponse(saved);
    }

    private ProfileEntity findProfile(UUID userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        "identity.profile_not_found",
                        "User profile not found"
                ));
    }

    private MyProfileResponse toResponse(ProfileEntity profile) {
        return new MyProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getMiddleName(),
                profile.getBirthDate(),
                profile.getGender(),
                profile.getCity(),
                profile.getCountryCode(),
                profile.getClubName(),
                profile.getUpdatedAt()
        );
    }
}