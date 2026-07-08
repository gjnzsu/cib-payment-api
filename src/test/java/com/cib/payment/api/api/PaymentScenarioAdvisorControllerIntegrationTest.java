package com.cib.payment.api.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PaymentScenarioAdvisorControllerIntegrationTest.JwtTestConfiguration.class)
class PaymentScenarioAdvisorControllerIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    PaymentScenarioAdvisorControllerIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void listsAdvisorScenariosWithoutBearerTokenOrIdempotencyKey() throws Exception {
        mockMvc.perform(get("/v1/payment-scenario-advisor/scenarios")
                        .header("X-Correlation-ID", "corr-advisor-list")
                        .header("Idempotency-Key", "ignored-for-advisor")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-advisor-list"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.scenarios.length()", equalTo(4)))
                .andExpect(jsonPath("$.scenarios[0].scenarioId", equalTo("urgent-supplier-payment")))
                .andExpect(jsonPath("$.scenarios[0].recommendedRail", equalTo("RTP")))
                .andExpect(jsonPath("$.scenarios[0].simulatorOnly", equalTo(true)))
                .andExpect(jsonPath("$.scenarios[0].requiresUserConfirmation", equalTo(true)));
    }

    @Test
    void returnsAdvisorScenarioDetailWithSimulationPlanAndFeedbackReport() throws Exception {
        mockMvc.perform(get("/v1/payment-scenario-advisor/scenarios/vendor-batch-payment")
                        .header("X-Correlation-ID", "corr-advisor-detail")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-advisor-detail"))
                .andExpect(jsonPath("$.scenarioId", equalTo("vendor-batch-payment")))
                .andExpect(jsonPath("$.recommendation.rail", equalTo("ACH")))
                .andExpect(jsonPath("$.recommendation.arrangement", equalTo("BATCH_CLEARING_NET_SETTLEMENT")))
                .andExpect(jsonPath("$.recommendation.matchedFactors", hasItem("MULTIPLE_PAYMENTS")))
                .andExpect(jsonPath("$.recommendationIntent.paymentCount", equalTo(250)))
                .andExpect(jsonPath("$.recommendationIntent.amountSummary.currency", equalTo("USD")))
                .andExpect(jsonPath("$.simulationPlan.endpoint", equalTo("/v1/ach-batches")))
                .andExpect(jsonPath("$.simulationPlan.mockScenario", equalTo("ach_direct_credit_settled")))
                .andExpect(jsonPath("$.simulationPlan.requiredScopes", hasItem("ach-batches:create")))
                .andExpect(jsonPath("$.simulationPlan.idempotencyRequired", equalTo(true)))
                .andExpect(jsonPath("$.simulationPlan.simulatorOnly", equalTo(true)))
                .andExpect(jsonPath("$.simulationPlan.requiresUserConfirmation", equalTo(true)))
                .andExpect(jsonPath("$.feedbackReport.validationStatus", equalTo("VALIDATED")))
                .andExpect(jsonPath("$.feedbackReport.expectedOutcome", equalTo("SETTLED")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-advisor-detail")));
    }

    @Test
    void returnsConsistentJsonErrorForUnknownScenario() throws Exception {
        mockMvc.perform(get("/v1/payment-scenario-advisor/scenarios/not-real")
                        .header("X-Correlation-ID", "corr-advisor-missing")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("ADVISOR_SCENARIO_NOT_FOUND")))
                .andExpect(jsonPath("$.message", equalTo("Advisor scenario was not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-advisor-missing")));
    }

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        mockMvc.perform(get("/v1/payment-scenario-advisor/scenarios/high-value-treasury-transfer")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.recommendation.rail", equalTo("RTGS")));
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
