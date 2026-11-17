package com.acme.sportplatform.identity;

import java.util.UUID;

public interface OrganizerRoleProvisioning {

    void grantToActiveUser(UUID userId);
}