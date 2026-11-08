package com.acme.sportplatform.events;

import java.util.UUID;

public interface EventOperationAccess {

    boolean canManageEvent(UUID eventId, UUID userId);

    boolean isAssignedActiveOperator(UUID eventId, UUID userId);
}