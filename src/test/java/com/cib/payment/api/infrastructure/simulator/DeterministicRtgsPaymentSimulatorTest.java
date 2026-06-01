package com.cib.payment.api.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.RtgsClientSegment;
import com.cib.payment.api.domain.model.RtgsPaymentId;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import com.cib.payment.api.domain.model.RtgsPaymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicRtgsPaymentSimulatorTest {
    private final DeterministicRtgsPaymentSimulator simulator = new DeterministicRtgsPaymentSimulator();

    @Test
    void settledScenarioReturnsSettledOutcomeWithFinalityAndNoReason() {
        var outcome = simulator.process(
                acceptedRecord(),
                authorizationContext(),
                "rtgs_settled");

        assertThat(outcome.status()).isEqualTo(RtgsPaymentStatus.SETTLED);
        assertThat(outcome.settlementFinality()).isTrue();
        assertThat(outcome.reason()).isEmpty();
    }

    @Test
    void queuedForLiquidityScenarioReturnsQueuedOutcomeWithoutFinalityAndWithReason() {
        var outcome = simulator.process(
                acceptedRecord(),
                authorizationContext(),
                "rtgs_queued_for_liquidity");

        assertThat(outcome.status()).isEqualTo(RtgsPaymentStatus.QUEUED_FOR_LIQUIDITY);
        assertThat(outcome.settlementFinality()).isFalse();
        assertThat(outcome.reason()).hasValueSatisfying(reason -> {
            assertThat(reason.code()).isNotBlank();
            assertThat(reason.message()).containsIgnoringCase("liquidity");
        });
    }

    @Test
    void rejectedScenarioReturnsRejectedOutcomeWithoutFinalityAndWithReason() {
        var outcome = simulator.process(
                acceptedRecord(),
                authorizationContext(),
                "rtgs_rejected");

        assertThat(outcome.status()).isEqualTo(RtgsPaymentStatus.REJECTED);
        assertThat(outcome.settlementFinality()).isFalse();
        assertThat(outcome.reason()).hasValueSatisfying(reason -> {
            assertThat(reason.code()).isNotBlank();
            assertThat(reason.message()).containsIgnoringCase("rejected");
        });
    }

    @Test
    void unsupportedScenarioThrowsValidationFailure() {
        assertThatThrownBy(() -> simulator.process(
                        acceptedRecord(),
                        authorizationContext(),
                        "unsupported_rtgs_scenario"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported RTGS payment simulator scenario");
    }

    private RtgsPaymentRecord acceptedRecord() {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        return new RtgsPaymentRecord(
                new RtgsPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440601")),
                "rtgs-client-a",
                RtgsClientSegment.CORPORATE,
                "RTGS-REF-001",
                new RtgsPaymentRecord.CorporateParties(
                        new AccountReference("021000021", "123456789012", "Example Debtor LLC"),
                        new AccountReference("021000021", "210987654321", "Example Creditor LLC")),
                new Money("USD", "250000.00"),
                LocalDate.parse("2026-06-01"),
                "HIGH",
                "Treasury payment",
                RtgsPaymentStatus.ACCEPTED_FOR_SETTLEMENT,
                false,
                new CorrelationId("corr-rtgs-simulator"),
                now,
                now,
                Optional.empty());
    }

    private AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "rtgs-client-a",
                "rtgs-client-a",
                Set.of("rtgs-payments:create"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-06-01T00:00:00Z"),
                "jwt-id",
                new CorrelationId("corr-rtgs-simulator"));
    }
}
