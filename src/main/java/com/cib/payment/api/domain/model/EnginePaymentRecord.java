package com.cib.payment.api.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EnginePaymentRecord(
        PaymentId paymentId,
        String clientId,
        IsoPaymentCandidate candidate,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt,
        CorrelationId correlationId,
        Optional<InternalInterbankTransfer> internalInterbankTransfer,
        Optional<String> latestStatusReportXml,
        Optional<PaymentReason> statusReason,
        String idempotencyReference) {

    public EnginePaymentRecord {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(idempotencyReference, "idempotencyReference must not be null");
        internalInterbankTransfer = internalInterbankTransfer == null ? Optional.empty() : internalInterbankTransfer;
        latestStatusReportXml = latestStatusReportXml == null ? Optional.empty() : latestStatusReportXml;
        statusReason = statusReason == null ? Optional.empty() : statusReason;
    }
}
