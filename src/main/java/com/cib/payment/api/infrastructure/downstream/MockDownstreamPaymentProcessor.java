package com.cib.payment.api.infrastructure.downstream;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.DownstreamPaymentOutcome;
import com.cib.payment.api.application.port.DownstreamPaymentProcessor;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.PaymentInstruction;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MockDownstreamPaymentProcessor implements DownstreamPaymentProcessor {
    private final PaymentObservability observability;

    public MockDownstreamPaymentProcessor(PaymentObservability observability) {
        this.observability = observability;
    }

    @Override
    public DownstreamPaymentOutcome process(
            PaymentInstruction instruction,
            AuthorizationContext authorizationContext,
            CorrelationId correlationId,
            String mockScenario) {
        var scenario = mockScenario == null || mockScenario.isBlank() ? "success" : mockScenario;
        var outcome = switch (scenario) {
            case "success" -> new DownstreamPaymentOutcome(PaymentStatus.COMPLETED, Optional.empty());
            case "rejection" -> new DownstreamPaymentOutcome(
                    PaymentStatus.REJECTED,
                    new PaymentReason("MOCK_REJECTION", "Payment rejected by downstream mock"));
            case "timeout" -> new DownstreamPaymentOutcome(
                    PaymentStatus.TIMEOUT,
                    new PaymentReason("MOCK_TIMEOUT", "Payment timed out in downstream mock"));
            case "internal_failure" -> new DownstreamPaymentOutcome(
                    PaymentStatus.FAILED,
                    new PaymentReason("MOCK_INTERNAL_FAILURE", "Payment failed in downstream mock"));
            default -> throw new ValidationFailureException("Unsupported mock scenario: " + scenario);
        };
        observability.downstreamOutcome(scenario, outcome.status(), correlationId);
        return outcome;
    }
}
