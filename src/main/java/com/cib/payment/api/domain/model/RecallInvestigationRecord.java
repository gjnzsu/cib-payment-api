package com.cib.payment.api.domain.model;

import java.time.Instant;
import java.util.Optional;

public record RecallInvestigationRecord(
        RecallInvestigationId investigationId,
        FiPaymentId fiPaymentId,
        String ownerClientId,
        String recallMessageId,
        String caseId,
        String originalPaymentReference,
        RecallInvestigationStatus status,
        Optional<String> reasonCode,
        Optional<String> reasonMessage,
        CorrespondentSettlementContext settlementContext,
        CorrelationId correlationId,
        Instant createdAt,
        Instant updatedAt,
        Optional<String> renderedCamt029Xml) {
}
