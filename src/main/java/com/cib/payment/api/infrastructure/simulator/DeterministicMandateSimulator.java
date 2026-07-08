package com.cib.payment.api.infrastructure.simulator;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.MandateSimulator;
import com.cib.payment.api.application.port.MandateSimulatorOutcome;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.MandateRecord;
import com.cib.payment.api.domain.model.MandateStatus;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DeterministicMandateSimulator implements MandateSimulator {
    @Override
    public MandateSimulatorOutcome process(
            MandateRecord acceptedRecord,
            AuthorizationContext authorizationContext,
            String scenario) {
        Objects.requireNonNull(acceptedRecord, "acceptedRecord must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");
        if (scenario == null || scenario.isBlank()) {
            throw new ValidationFailureException("Mandate simulator scenario is required");
        }

        return switch (scenario) {
            case "mandate_active" -> new MandateSimulatorOutcome(MandateStatus.ACTIVE, Optional.empty());
            case "mandate_pending_authorization" -> new MandateSimulatorOutcome(
                    MandateStatus.PENDING_AUTHORIZATION,
                    new PaymentReason("MANDATE_AUTHORIZATION_PENDING", "Mandate authorization is pending payer approval"));
            case "mandate_rejected_by_payer" -> new MandateSimulatorOutcome(
                    MandateStatus.REJECTED,
                    new PaymentReason("MANDATE_REJECTED_BY_PAYER", "Mandate authorization was rejected by payer"));
            case "mandate_expired" -> new MandateSimulatorOutcome(
                    MandateStatus.EXPIRED,
                    new PaymentReason("MANDATE_EXPIRED", "Mandate authorization expired before activation"));
            case "mandate_timeout" -> new MandateSimulatorOutcome(
                    MandateStatus.TIMEOUT,
                    new PaymentReason("MANDATE_TIMEOUT", "Mandate simulator timed out"));
            case "mandate_internal_failure" -> new MandateSimulatorOutcome(
                    MandateStatus.FAILED,
                    new PaymentReason("MANDATE_INTERNAL_FAILURE", "Mandate simulator failed internally"));
            default -> throw new ValidationFailureException("Unsupported mandate simulator scenario: " + scenario);
        };
    }
}
