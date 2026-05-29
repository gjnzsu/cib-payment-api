package com.cib.payment.api.infrastructure.simulator;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.FiCorrespondentPaymentOutcome;
import com.cib.payment.api.application.port.FiCorrespondentPaymentSimulator;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.FiPaymentCandidate;
import com.cib.payment.api.domain.model.FiPaymentStatus;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DeterministicFiCorrespondentPaymentSimulator implements FiCorrespondentPaymentSimulator {
    @Override
    public FiCorrespondentPaymentOutcome process(
            FiPaymentCandidate candidate,
            CorrespondentSettlementContext settlementContext,
            AuthorizationContext authorizationContext,
            String scenarioContext) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(settlementContext, "settlementContext must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");

        if (scenarioContext == null || scenarioContext.isBlank()) {
            throw new ValidationFailureException("FI payment simulator scenario is required");
        }

        return switch (scenarioContext) {
            case "fi_payment_accepted" -> new FiCorrespondentPaymentOutcome(
                    FiPaymentStatus.SETTLED,
                    Optional.empty());
            case "fi_payment_pending_correspondent_review" -> new FiCorrespondentPaymentOutcome(
                    FiPaymentStatus.PROCESSING,
                    new PaymentReason(
                            "FI_CORRESPONDENT_REVIEW",
                            "FI payment is pending correspondent review"));
            case "fi_payment_rejected_unsupported_correspondent" -> new FiCorrespondentPaymentOutcome(
                    FiPaymentStatus.REJECTED,
                    new PaymentReason(
                            "FI_UNSUPPORTED_CORRESPONDENT",
                            "Unsupported correspondent route rejected by FI payment simulator"));
            default -> throw new ValidationFailureException(
                    "Unsupported FI payment simulator scenario: " + scenarioContext);
        };
    }
}
