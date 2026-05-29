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
        Instant updatedAt,
        String originalResponseXml,
        String originalResponseJson
) {
    public IdempotencyRecord(
            String clientId,
            String idempotencyKey,
            String requestFingerprint,
            PaymentId paymentId,
            PaymentStatus status,
            CorrelationId correlationId,
            Instant createdAt,
            Instant updatedAt,
            String originalResponseXml) {
        this(clientId, idempotencyKey, requestFingerprint, paymentId, status, correlationId, createdAt, updatedAt, originalResponseXml, null);
    }

    public IdempotencyRecord(
            String clientId,
            String idempotencyKey,
            String requestFingerprint,
            PaymentId paymentId,
            PaymentStatus status,
            CorrelationId correlationId,
            Instant createdAt,
            Instant updatedAt) {
        this(clientId, idempotencyKey, requestFingerprint, paymentId, status, correlationId, createdAt, updatedAt, null, null);
    }
}
