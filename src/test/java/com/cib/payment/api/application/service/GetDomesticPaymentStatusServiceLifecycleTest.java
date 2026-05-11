package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentInstruction;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.infrastructure.persistence.InMemoryPaymentStatusRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GetDomesticPaymentStatusServiceLifecycleTest {
    private final InMemoryPaymentStatusRepository repository = new InMemoryPaymentStatusRepository();
    private final GetDomesticPaymentStatusService service =
            new GetDomesticPaymentStatusService(repository, PaymentObservability.noop());

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void statusQueryReturnsEverySupportedPaymentLifecycleStatus(PaymentStatus status) {
        var record = repository.save(paymentRecord(status, reasonFor(status)));

        var response = service.getStatus(record.paymentId().value().toString(), authorizationContext("client-a"));

        assertThat(response.paymentId()).isEqualTo(record.paymentId().value().toString());
        assertThat(response.status()).isEqualTo(status.name());
        assertThat(response.correlationId()).isEqualTo("corr-lifecycle");
        assertThat(response.links().self()).isEqualTo("/v1/domestic-payments/" + record.paymentId().value());
        if (reasonFor(status).isPresent()) {
            assertThat(response.reason()).isNotNull();
            assertThat(response.reason().code()).isEqualTo(reasonFor(status).get().code());
        } else {
            assertThat(response.reason()).isNull();
        }
    }

    @Test
    void statusQueryReturnsNotFoundForUnknownPaymentId() {
        var unknownPaymentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        assertThatThrownBy(() -> service.getStatus(unknownPaymentId.toString(), authorizationContext("client-a")))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    private Optional<PaymentReason> reasonFor(PaymentStatus status) {
        return switch (status) {
            case REJECTED -> Optional.of(new PaymentReason("MOCK_REJECTION", "Payment rejected by downstream mock"));
            case FAILED -> Optional.of(new PaymentReason("MOCK_INTERNAL_FAILURE", "Payment failed in downstream mock"));
            case TIMEOUT -> Optional.of(new PaymentReason("MOCK_TIMEOUT", "Payment timed out in downstream mock"));
            case ACCEPTED, PROCESSING, COMPLETED -> Optional.empty();
        };
    }

    private PaymentRecord paymentRecord(PaymentStatus status, Optional<PaymentReason> reason) {
        var now = Instant.parse("2026-05-09T00:00:00Z");
        return new PaymentRecord(
                new PaymentId(UUID.randomUUID()),
                "client-a",
                new PaymentInstruction(
                        new AccountReference("CIBBMYKL", "1234567890", "Acme Treasury"),
                        new AccountReference("PAYBMYKL", "9876543210", "Supplier Sdn Bhd"),
                        new Money("MYR", "1250.50"),
                        "INV-2026-0001",
                        "Invoice payment",
                        null),
                status,
                now,
                now,
                new CorrelationId("corr-lifecycle"),
                reason);
    }

    private AuthorizationContext authorizationContext(String clientId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("payments:read"),
                null,
                Map.of(),
                Instant.parse("2026-05-09T00:00:00Z"),
                "jwt-lifecycle",
                new CorrelationId("corr-request"));
    }
}
