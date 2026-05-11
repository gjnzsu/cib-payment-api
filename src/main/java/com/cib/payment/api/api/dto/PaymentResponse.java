package com.cib.payment.api.api.dto;

import java.time.Instant;

public record PaymentResponse(
        String paymentId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentLinksResponse links) {}
