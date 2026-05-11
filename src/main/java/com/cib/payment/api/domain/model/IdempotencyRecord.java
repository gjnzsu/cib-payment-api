package com.cib.payment.api.domain.model;

import java.time.Instant;

public record IdempotencyRecord(
        String clientId,
        String idempotencyKey,
        String requestFingerprint,
        PaymentId paymentId,
        PaymentStatus status,
        CorrelationId correlationId,
        Instant createdAt,
        Instant updatedAt
) {
}
