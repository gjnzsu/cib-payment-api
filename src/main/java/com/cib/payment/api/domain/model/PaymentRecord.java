package com.cib.payment.api.domain.model;

import java.time.Instant;
import java.util.Optional;

public record PaymentRecord(
        PaymentId paymentId,
        String clientId,
        PaymentInstruction instruction,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt,
        CorrelationId correlationId,
        Optional<PaymentReason> reason
) {
}
