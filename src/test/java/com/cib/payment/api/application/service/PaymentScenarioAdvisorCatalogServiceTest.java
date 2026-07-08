package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.PaymentRail;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentScenarioAdvisorCatalogServiceTest {
    private final PaymentScenarioAdvisorCatalogService service = new PaymentScenarioAdvisorCatalogService();

    @Test
    void listsFourBusinessFacingAdvisorScenarios() {
        var scenarios = service.listScenarios();

        assertThat(scenarios).hasSize(4);
        assertThat(scenarios).extracting(scenario -> scenario.scenarioId().value())
                .containsExactly(
                        "urgent-supplier-payment",
                        "vendor-batch-payment",
                        "high-value-treasury-transfer",
                        "fi-correspondent-settlement");
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.simulatorOnly()).isTrue();
            assertThat(scenario.requiresUserConfirmation()).isTrue();
        });
    }

    @Test
    void mapsEachScenarioToExpectedRecommendedRailAndSimulatorEndpoint() {
        assertScenario(
                "urgent-supplier-payment",
                PaymentRail.RTP,
                "/v1/domestic-payments",
                "success",
                "ACSC");
        assertScenario(
                "vendor-batch-payment",
                PaymentRail.ACH,
                "/v1/ach-batches",
                "ach_direct_credit_settled",
                "SETTLED");
        assertScenario(
                "high-value-treasury-transfer",
                PaymentRail.RTGS,
                "/v1/rtgs-payments",
                "rtgs_settled",
                "SETTLED");
        assertScenario(
                "fi-correspondent-settlement",
                PaymentRail.FI_CORRESPONDENT,
                "/v1/fi-payments",
                "fi_payment_accepted",
                "ACCEPTED");
    }

    @Test
    void returnsRecommendationIntentWithoutSensitivePaymentData() {
        var scenario = service.getScenario("urgent-supplier-payment").orElseThrow();

        assertThat(scenario.recommendationIntent().amountSummary().currency()).isEqualTo("USD");
        assertThat(scenario.recommendationIntent().debtorCountry()).isEqualTo("US");
        assertThat(scenario.recommendationIntent().creditorCountry()).isEqualTo("US");
        assertThat(scenario.recommendationIntent().paymentCount()).isEqualTo(1);
        assertThat(scenario.toString())
                .doesNotContain("000111222333")
                .doesNotContain("Bearer ")
                .doesNotContain("PRIVATE KEY");
    }

    @Test
    void returnsEmptyForUnknownScenario() {
        assertThat(service.getScenario("unknown-scenario")).isEmpty();
    }

    private void assertScenario(
            String scenarioId,
            PaymentRail expectedRail,
            String expectedEndpoint,
            String expectedMockScenario,
            String expectedStatus) {
        var scenario = service.getScenario(scenarioId).orElseThrow();

        assertThat(scenario.recommendation().rail()).isEqualTo(expectedRail);
        assertThat(scenario.simulationPlan().endpoint()).isEqualTo(expectedEndpoint);
        assertThat(scenario.simulationPlan().mockScenario()).isEqualTo(expectedMockScenario);
        assertThat(scenario.feedbackReport().expectedOutcome()).isEqualTo(expectedStatus);
        assertThat(scenario.simulationPlan().requiredScopes()).isNotEmpty();
        assertThat(scenario.simulationPlan().requiredHeaders()).contains("Authorization", "X-Correlation-ID");
    }
}
