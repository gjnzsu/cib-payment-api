package com.cib.payment.api.infrastructure.downstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentInstruction;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockDownstreamPaymentProcessorTest {
    private final MockDownstreamPaymentProcessor processor = new MockDownstreamPaymentProcessor(PaymentObservability.noop());

    @Test
    void nullScenarioDefaultsToCompleted() {
        assertThat(processor.process(instruction(), authorizationContext(), correlationId(), null).status())
                .isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void successScenarioMapsToCompleted() {
        assertThat(processor.process(instruction(), authorizationContext(), correlationId(), "success").status())
                .isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void rejectionScenarioMapsToRejectedWithReason() {
        var outcome = processor.process(instruction(), authorizationContext(), correlationId(), "rejection");

        assertThat(outcome.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(outcome.reason()).hasValueSatisfying(reason -> assertThat(reason.code()).isEqualTo("MOCK_REJECTION"));
    }

    @Test
    void timeoutScenarioMapsToTimeoutWithReason() {
        var outcome = processor.process(instruction(), authorizationContext(), correlationId(), "timeout");

        assertThat(outcome.status()).isEqualTo(PaymentStatus.TIMEOUT);
        assertThat(outcome.reason()).hasValueSatisfying(reason -> assertThat(reason.code()).isEqualTo("MOCK_TIMEOUT"));
    }

    @Test
    void internalFailureScenarioMapsToFailedWithReason() {
        var outcome = processor.process(instruction(), authorizationContext(), correlationId(), "internal_failure");

        assertThat(outcome.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outcome.reason()).hasValueSatisfying(reason -> assertThat(reason.code()).isEqualTo("MOCK_INTERNAL_FAILURE"));
    }

    @Test
    void unsupportedScenarioFailsValidation() {
        assertThatThrownBy(() -> processor.process(instruction(), authorizationContext(), correlationId(), "other"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported mock scenario");
    }

    private PaymentInstruction instruction() {
        return new PaymentInstruction(
                new AccountReference("CIBBMYKL", "1234567890", "Acme Treasury"),
                new AccountReference("PAYBMYKL", "9876543210", "Supplier Sdn Bhd"),
                new Money("MYR", "1250.50"),
                "INV-2026-0001",
                "Invoice payment",
                null);
    }

    private AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "client-a",
                "client-a",
                Set.of("payments:create"),
                null,
                Map.of(),
                null,
                null,
                correlationId());
    }

    private CorrelationId correlationId() {
        return new CorrelationId("corr-123");
    }
}
