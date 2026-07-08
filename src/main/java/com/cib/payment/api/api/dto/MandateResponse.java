package com.cib.payment.api.api.dto;

import java.time.Instant;

public record MandateResponse(
        String mandateId,
        String mandateReference,
        String mandateProfile,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentReasonResponse reason,
        PaymentLinksResponse links) {}
