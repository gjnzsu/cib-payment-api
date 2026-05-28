package com.cib.payment.api.api.dto;

import java.time.Instant;

public record FiPaymentStatusResponse(
        String paymentId,
        String status,
        String messageId,
        String instructionId,
        String originalPaymentReference,
        String instructingAgentBic,
        String instructedAgentBic,
        String settlementCurrency,
        CorrespondentSettlementContextResponse correspondentSettlementContext,
        RecallInvestigationSummaryResponse recallInvestigation,
        PaymentReasonResponse reason,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentLinksResponse links) {
}
