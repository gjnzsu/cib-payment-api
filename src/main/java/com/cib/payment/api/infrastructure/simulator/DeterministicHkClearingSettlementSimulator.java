package com.cib.payment.api.infrastructure.simulator;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.HkClearingSettlementOutcome;
import com.cib.payment.api.application.port.HkClearingSettlementSimulator;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.InternalInterbankTransfer;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class DeterministicHkClearingSettlementSimulator implements HkClearingSettlementSimulator {
    private static final Set<String> KNOWN_PARTICIPANTS = Set.of("CIBBHKHH", "SUPPHKHH");

    @Override
    public HkClearingSettlementOutcome process(
            InternalInterbankTransfer transfer,
            AuthorizationContext authorizationContext,
            String scenarioContext) {
        Objects.requireNonNull(transfer, "transfer must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");

        if (!"HKD".equals(transfer.amount().currency())) {
            return rejected("HK_UNSUPPORTED_CURRENCY", "HK simulator only supports HKD clearing and settlement");
        }
        if (!isKnownParticipant(transfer.payerParticipantIdentifier())
                || !isKnownParticipant(transfer.payeeParticipantIdentifier())) {
            return rejected("HK_UNKNOWN_PARTICIPANT", "Unknown HK clearing participant");
        }

        var scenario = scenarioContext == null || scenarioContext.isBlank() ? "success" : scenarioContext;
        return switch (scenario) {
            case "success" -> new HkClearingSettlementOutcome(
                    HkClearingSettlementOutcome.Status.SETTLED,
                    Optional.empty());
            case "rejection" -> rejected(
                    "HK_CLEARING_REJECTION",
                    "Payment rejected by HK clearing simulator");
            case "suspicious_proxy_or_account" -> rejected(
                    "HK_SUSPICIOUS_PROXY_OR_ACCOUNT",
                    "Beneficiary proxy or account flagged by HK simulator");
            case "pending" -> new HkClearingSettlementOutcome(
                    HkClearingSettlementOutcome.Status.PENDING,
                    new PaymentReason("HK_PENDING_PROCESSING", "Payment remains pending in HK simulator"));
            case "timeout" -> new HkClearingSettlementOutcome(
                    HkClearingSettlementOutcome.Status.TIMEOUT,
                    new PaymentReason("HK_SIMULATOR_TIMEOUT", "HK simulator timed out before settlement"));
            case "internal_failure" -> new HkClearingSettlementOutcome(
                    HkClearingSettlementOutcome.Status.INTERNAL_FAILURE,
                    new PaymentReason("HK_SIMULATOR_INTERNAL_FAILURE", "HK simulator failed internally"));
            default -> throw new ValidationFailureException("Unsupported HK simulator scenario: " + scenario);
        };
    }

    private HkClearingSettlementOutcome rejected(String code, String message) {
        return new HkClearingSettlementOutcome(
                HkClearingSettlementOutcome.Status.REJECTED,
                new PaymentReason(code, message));
    }

    private boolean isKnownParticipant(String participantIdentifier) {
        return participantIdentifier != null && KNOWN_PARTICIPANTS.contains(participantIdentifier);
    }
}
