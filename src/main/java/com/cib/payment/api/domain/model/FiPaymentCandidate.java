package com.cib.payment.api.domain.model;

import java.time.LocalDate;
import java.util.Optional;

public record FiPaymentCandidate(
        FiPaymentIdentifiers identifiers,
        FiParty instructingParty,
        FiParty instructedParty,
        Optional<String> intermediaryBic,
        Money settlementAmount,
        String settlementCurrency,
        LocalDate settlementDate,
        String sourceMessageType) {
}
