package com.cib.payment.api.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record IsoPaymentStatusReport(
        PaymentId paymentId,
        IsoPaymentCandidate originalCandidate,
        PaymentStatus internalStatus,
        Instant reportCreatedAt,
        CorrelationId correlationId,
        Optional<PaymentReason> reason) {

    public IsoPaymentStatusReport {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(originalCandidate, "originalCandidate must not be null");
        Objects.requireNonNull(internalStatus, "internalStatus must not be null");
        Objects.requireNonNull(reportCreatedAt, "reportCreatedAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        reason = reason == null ? Optional.empty() : reason;
    }
}
