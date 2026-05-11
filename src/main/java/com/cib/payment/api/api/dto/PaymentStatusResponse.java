package com.cib.payment.api.api.dto;

import java.time.Instant;

public record PaymentStatusResponse(
        String paymentId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentReasonResponse reason,
        PaymentLinksResponse links) {}
