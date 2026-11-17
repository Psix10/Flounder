package com.acme.sportplatform.payments.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByRegistrationId(UUID registrationId);
    Optional<PaymentEntity> findByProviderAndExternalPaymentId(
            String provider, String externalPaymentId
    );
    Optional<PaymentEntity> findByIdempotencyKey(UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> findForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.registrationId = :registrationId")
    Optional<PaymentEntity> findForUpdateByRegistrationId(
            @Param("registrationId") UUID registrationId
    );
}
