package com.cib.payment.api.api.dto;

import java.time.Instant;

public record MandateStatusResponse(
        String mandateId,
        String mandateReference,
        String mandateProfile,
        String creditorName,
        String debtorName,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentReasonResponse reason,
        PaymentLinksResponse links) {}
