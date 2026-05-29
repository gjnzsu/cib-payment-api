package com.cib.payment.api.api.dto;

import java.time.Instant;

public record RecallInvestigationSummaryResponse(
        String investigationId,
        String recallMessageId,
        String caseId,
        String originalPaymentReference,
        String status,
        String reasonCode,
        String reasonMessage,
        Instant createdAt,
        Instant updatedAt) {
}
