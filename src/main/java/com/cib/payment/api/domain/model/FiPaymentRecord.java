package com.cib.payment.api.domain.model;

import java.time.Instant;
import java.util.Optional;

public record FiPaymentRecord(
        FiPaymentId paymentId,
        String ownerClientId,
        FiPaymentIdentifiers identifiers,
        FiParty instructingParty,
        FiParty instructedParty,
        Money settlementAmount,
        String settlementCurrency,
        FiPaymentStatus status,
        CorrespondentSettlementContext correspondentSettlementContext,
        CorrelationId correlationId,
        Instant createdAt,
        Instant updatedAt,
        Optional<PaymentReason> reason) {
}
