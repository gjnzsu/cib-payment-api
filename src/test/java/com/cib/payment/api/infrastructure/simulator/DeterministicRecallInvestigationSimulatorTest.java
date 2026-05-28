package com.cib.payment.api.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.RecallInvestigationStatus;
import org.junit.jupiter.api.Test;

class DeterministicRecallInvestigationSimulatorTest {
    private final DeterministicRecallInvestigationSimulator simulator =
            new DeterministicRecallInvestigationSimulator();

    @Test
    void recallAcceptedScenarioMapsToAcceptedWithReasonDetails() {
        var outcome = simulator.investigate("recall_accepted");

        assertThat(outcome.status()).isEqualTo(RecallInvestigationStatus.ACCEPTED);
        assertThat(outcome.reasonCode()).contains("AC01");
        assertThat(outcome.reasonMessage()).contains("Recall accepted by correspondent simulator");
    }

    @Test
    void recallRejectedScenarioMapsToRejectedWithReasonDetails() {
        var outcome = simulator.investigate("recall_rejected");

        assertThat(outcome.status()).isEqualTo(RecallInvestigationStatus.REJECTED);
        assertThat(outcome.reasonCode()).contains("NOAS");
        assertThat(outcome.reasonMessage()).contains("Recall rejected by correspondent simulator");
    }

    @Test
    void investigationPendingScenarioMapsToPendingWithReasonDetails() {
        var outcome = simulator.investigate("investigation_pending");

        assertThat(outcome.status()).isEqualTo(RecallInvestigationStatus.PENDING);
        assertThat(outcome.reasonCode()).contains("IPAY");
        assertThat(outcome.reasonMessage()).contains("Investigation pending correspondent response");
    }

    @Test
    void blankScenarioFailsValidation() {
        assertThatThrownBy(() -> simulator.investigate(" "))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Recall investigation simulator scenario is required");
    }

    @Test
    void unsupportedScenarioFailsValidation() {
        assertThatThrownBy(() -> simulator.investigate("anything_else"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported recall investigation simulator scenario");
    }
}
