package com.cib.payment.api.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.FiParty;
import com.cib.payment.api.domain.model.FiPaymentCandidate;
import com.cib.payment.api.domain.model.FiPaymentIdentifiers;
import com.cib.payment.api.domain.model.FiPaymentStatus;
import com.cib.payment.api.domain.model.Money;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterministicFiCorrespondentPaymentSimulatorTest {
    private final FiCorrespondentRouteProfile routeProfile = new FiCorrespondentRouteProfile();
    private final DeterministicFiCorrespondentPaymentSimulator simulator =
            new DeterministicFiCorrespondentPaymentSimulator();

    @Test
    void acceptedScenarioSettlesPaymentWithoutReason() {
        var outcome = simulator.process(
                candidate("CIBBHKHH", "CORRUS33", "USD"),
                routeProfile.derive("CIBBHKHH", "CORRUS33", "USD"),
                authorizationContext(),
                "fi_payment_accepted");

        assertThat(outcome.status()).isEqualTo(FiPaymentStatus.SETTLED);
        assertThat(outcome.reason()).isEmpty();
    }

    @Test
    void pendingCorrespondentReviewScenarioKeepsReasonDetails() {
        var outcome = simulator.process(
                candidate("CIBBHKHH", "CORRUS33", "USD"),
                routeProfile.derive("CIBBHKHH", "CORRUS33", "USD"),
                authorizationContext(),
                "fi_payment_pending_correspondent_review");

        assertThat(outcome.status()).isEqualTo(FiPaymentStatus.PROCESSING);
        assertThat(outcome.reason()).hasValueSatisfying(reason -> {
            assertThat(reason.code()).isEqualTo("FI_CORRESPONDENT_REVIEW");
            assertThat(reason.message()).contains("correspondent review");
        });
    }

    @Test
    void unsupportedCorrespondentScenarioRejectsWithReasonDetails() {
        var outcome = simulator.process(
                candidate("CIBBHKHH", "LOROUS33", "USD"),
                routeProfile.derive("CIBBHKHH", "LOROUS33", "USD"),
                authorizationContext(),
                "fi_payment_rejected_unsupported_correspondent");

        assertThat(outcome.status()).isEqualTo(FiPaymentStatus.REJECTED);
        assertThat(outcome.reason()).hasValueSatisfying(reason -> {
            assertThat(reason.code()).isEqualTo("FI_UNSUPPORTED_CORRESPONDENT");
            assertThat(reason.message()).contains("Unsupported correspondent");
        });
    }

    @Test
    void rejectsUnsupportedScenario() {
        assertThatThrownBy(() -> simulator.process(
                        candidate("CIBBHKHH", "CORRUS33", "USD"),
                        routeProfile.derive("CIBBHKHH", "CORRUS33", "USD"),
                        authorizationContext(),
                        "unsupported"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported FI payment simulator scenario");
    }

    @Test
    void rejectsBlankScenario() {
        assertThatThrownBy(() -> simulator.process(
                        candidate("CIBBHKHH", "CORRUS33", "USD"),
                        routeProfile.derive("CIBBHKHH", "CORRUS33", "USD"),
                        authorizationContext(),
                        "  "))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("FI payment simulator scenario is required");
    }

    private FiPaymentCandidate candidate(String instructingAgent, String instructedAgent, String currency) {
        return new FiPaymentCandidate(
                new FiPaymentIdentifiers("FI-MSG-20260528-0001", "FI-INSTR-0001", "FI-E2E-0001"),
                new FiParty(instructingAgent),
                new FiParty(instructedAgent),
                Optional.empty(),
                new Money(currency, "100000.00"),
                currency,
                LocalDate.parse("2026-05-28"),
                "pacs.009.001.08");
    }

    private AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "fi-client-a",
                "fi-client-a",
                Set.of("fi-payments:create"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-05-28T00:00:00Z"),
                "jwt-id",
                new CorrelationId("corr-fi-simulator"));
    }
}
