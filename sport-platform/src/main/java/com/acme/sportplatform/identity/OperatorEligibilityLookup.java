package com.acme.sportplatform.identity;

import java.util.UUID;

public interface OperatorEligibilityLookup {

    boolean isActiveOperator(UUID userId);
}