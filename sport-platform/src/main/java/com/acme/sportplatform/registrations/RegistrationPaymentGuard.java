package com.acme.sportplatform.registrations;

import java.util.UUID;

public interface RegistrationPaymentGuard {

    boolean hasAnyPayment(UUID registrationId);
}