package com.cib.payment.api.api.dto;

import java.time.Instant;

public record RtgsPaymentResponse(
        String paymentId,
        String rail,
        String clientSegment,
        String status,
        boolean settlementFinality,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentReasonResponse reason,
        PaymentLinksResponse links) {}
