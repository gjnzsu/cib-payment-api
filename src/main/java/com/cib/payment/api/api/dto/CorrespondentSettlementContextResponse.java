package com.cib.payment.api.api.dto;

public record CorrespondentSettlementContextResponse(
        String instructingAgentBic,
        String instructedAgentBic,
        String correspondentOrIntermediaryBic,
        String settlementCurrency,
        String accountRelationshipRole,
        String maskedSimulatedAccountReference) {
}
