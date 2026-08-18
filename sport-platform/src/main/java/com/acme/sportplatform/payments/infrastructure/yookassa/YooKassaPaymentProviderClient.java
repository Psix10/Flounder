package com.acme.sportplatform.payments.infrastructure.yookassa;

import java.math.RoundingMode;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaAmount;
import com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaConfirmation;
import com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaCreatePaymentRequest;
import com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaMetadata;
import com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaPaymentResponse;
import com.acme.sportplatform.payments.provider.PaymentProviderClient;
import com.acme.sportplatform.payments.provider.ProviderPaymentCommand;
import com.acme.sportplatform.payments.provider.ProviderPaymentResult;

@Component
@ConditionalOnProperty(
        name = "app.payments.provider",
        havingValue = "YOOKASSA"
)
public class YooKassaPaymentProviderClient
        implements PaymentProviderClient {

    private final YooKassaProperties properties;
    private final RestClient restClient;

    public YooKassaPaymentProviderClient(
            YooKassaProperties properties
    ) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(
                            properties.getShopId(),
                            properties.getSecretKey()
                    );
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setAccept(
                            java.util.List.of(MediaType.APPLICATION_JSON)
                    );
                })
                .build();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.YOOKASSA;
    }

    @Override
    public ProviderPaymentResult createPayment(
            ProviderPaymentCommand command
    ) {
        validateConfiguration();

        YooKassaCreatePaymentRequest request =
                new YooKassaCreatePaymentRequest(
                        new YooKassaAmount(
                                command.amount()
                                        .setScale(2, RoundingMode.HALF_UP)
                                        .toPlainString(),
                                command.currency()
                        ),
                        true,
                        new YooKassaConfirmation(
                                "redirect",
                                properties.getReturnUrl()
                        ),
                        "Sport event registration payment",
                        new YooKassaMetadata(
                                command.paymentId().toString(),
                                command.registrationId().toString()
                        )
                );

        YooKassaPaymentResponse response;

        try {
            response = restClient.post()
                    .uri("/payments")
                    .header(
                            "Idempotence-Key",
                            command.idempotencyKey().toString()
                    )
                    .body(request)
                    .retrieve()
                    .body(YooKassaPaymentResponse.class);
        } catch (RestClientException exception) {
            throw new BusinessException(
                    "payments.provider_unavailable",
                    "Payment provider is temporarily unavailable"
            );
        }

        if (response == null || response.id() == null
                || response.id().isBlank()) {
            throw new BusinessException(
                    "payments.provider_invalid_response",
                    "Payment provider returned an invalid response"
            );
        }

        return new ProviderPaymentResult(
                response.id(),
                response.confirmation() == null
                        ? null
                        : response.confirmation().confirmationUrl(),
                mapStatus(response.status()),
                response.expiresAt(),
                "{\"yookassaStatus\":\""
                        + escapeJson(response.status())
                        + "\"}"
        );
    }

    private void validateConfiguration() {
        if (isBlank(properties.getShopId())
                || isBlank(properties.getSecretKey())
                || isBlank(properties.getReturnUrl())) {
            throw new IllegalStateException(
                    "YooKassa configuration requires shopId, secretKey and returnUrl"
            );
        }
    }

    private PaymentStatus mapStatus(String providerStatus) {
        if ("succeeded".equals(providerStatus)) {
            return PaymentStatus.SUCCEEDED;
        }

        if ("canceled".equals(providerStatus)) {
            return PaymentStatus.CANCELED;
        }

        return PaymentStatus.PENDING;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String escapeJson(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\")
                        .replace("\"", "\\\"");
    }
}