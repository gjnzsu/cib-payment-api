package com.cib.payment.api.infrastructure.simulator;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AccountRelationshipRole;
import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.FiParty;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FiCorrespondentRouteProfile {
    private static final String SUPPORTED_CURRENCY = "USD";
    private static final Map<RouteKey, RouteProfileEntry> ROUTES = Map.of(
            new RouteKey("CIBBHKHH", "CORRUS33"),
            new RouteProfileEntry(
                    AccountRelationshipRole.NOSTRO,
                    "CORRUS33",
                    "nostro-usd-corrus33-****1234"),
            new RouteKey("VOSTUS33", "CIBBHKHH"),
            new RouteProfileEntry(
                    AccountRelationshipRole.VOSTRO,
                    "VOSTUS33",
                    "vostro-usd-vostus33-****5678"),
            new RouteKey("CIBBHKHH", "LOROUS33"),
            new RouteProfileEntry(
                    AccountRelationshipRole.LORO,
                    "LOROUS33",
                    "loro-usd-lorous33-****9012"));

    public CorrespondentSettlementContext derive(String instructingAgent, String instructedAgent, String currency) {
        var normalizedCurrency = normalize(currency);
        if (!SUPPORTED_CURRENCY.equals(normalizedCurrency)) {
            throw new ValidationFailureException("Only USD FI correspondent routes are supported");
        }

        var normalizedInstructingAgent = normalize(instructingAgent);
        var normalizedInstructedAgent = normalize(instructedAgent);
        var entry = ROUTES.get(new RouteKey(normalizedInstructingAgent, normalizedInstructedAgent));
        if (entry == null) {
            throw new ValidationFailureException("Unsupported FI correspondent route");
        }

        return new CorrespondentSettlementContext(
                new FiParty(normalizedInstructingAgent),
                new FiParty(normalizedInstructedAgent),
                Optional.of(new FiParty(entry.correspondentOrIntermediaryBank())),
                SUPPORTED_CURRENCY,
                entry.accountRelationshipRole(),
                entry.maskedSimulatedAccountReference());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private record RouteKey(String instructingAgent, String instructedAgent) {
    }

    private record RouteProfileEntry(
            AccountRelationshipRole accountRelationshipRole,
            String correspondentOrIntermediaryBank,
            String maskedSimulatedAccountReference) {
    }
}
