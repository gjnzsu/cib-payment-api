package com.cib.payment.api.infrastructure.simulator;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.RecallInvestigationOutcome;
import com.cib.payment.api.application.port.RecallInvestigationSimulator;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.RecallInvestigationStatus;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DeterministicRecallInvestigationSimulator implements RecallInvestigationSimulator {
    @Override
    public RecallInvestigationOutcome investigate(
            AuthorizationContext authorizationContext,
            String scenarioContext) {
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");

        if (scenarioContext == null || scenarioContext.isBlank()) {
            throw new ValidationFailureException("Recall investigation simulator scenario is required");
        }

        return switch (scenarioContext) {
            case "recall_accepted" -> new RecallInvestigationOutcome(
                    RecallInvestigationStatus.ACCEPTED,
                    "AC01",
                    "Recall accepted by correspondent simulator");
            case "recall_rejected" -> new RecallInvestigationOutcome(
                    RecallInvestigationStatus.REJECTED,
                    "NOAS",
                    "Recall rejected by correspondent simulator");
            case "investigation_pending" -> new RecallInvestigationOutcome(
                    RecallInvestigationStatus.PENDING,
                    "IPAY",
                    "Investigation pending correspondent response");
            default -> throw new ValidationFailureException(
                    "Unsupported recall investigation simulator scenario: " + scenarioContext);
        };
    }
}
