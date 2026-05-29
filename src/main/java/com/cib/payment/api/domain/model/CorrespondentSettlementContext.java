package com.cib.payment.api.domain.model;

import java.util.Optional;

public record CorrespondentSettlementContext(
        FiParty instructingAgent,
        FiParty instructedAgent,
        Optional<FiParty> correspondentOrIntermediaryBank,
        String settlementCurrency,
        AccountRelationshipRole accountRelationshipRole,
        String maskedSimulatedAccountReference) {
}
