package com.cib.payment.api.domain.model;

import java.time.Instant;
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
        String idempotencyReference,
        String scenarioContext) {
}
