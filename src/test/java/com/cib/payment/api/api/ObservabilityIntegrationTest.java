package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "logging.level.org.springframework=INFO")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(ObservabilityIntegrationTest.JwtTestConfiguration.class)
class ObservabilityIntegrationTest {
    private final MockMvc mockMvc;
    private final MeterRegistry meterRegistry;

    @Autowired
    ObservabilityIntegrationTest(MockMvc mockMvc, MeterRegistry meterRegistry) {
        this.mockMvc = mockMvc;
        this.meterRegistry = meterRegistry;
    }

    @Test
    void successfulPaymentCreationEmitsMaskedStructuredLogsAndMetrics(CapturedOutput output) throws Exception {
        var requestCounter = counter("payment.api.requests", "operation", "create", "result", "accepted");
        var acceptedCounter = counter("payment.accepted");
        var downstreamCounter = counter("payment.downstream.outcomes", "scenario", "success", "status", "COMPLETED");
        var statusCounter = counter("payment.status.distribution", "status", "COMPLETED");

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "obs-success-key")
                        .header("X-Correlation-ID", "corr-obs-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId", equalTo("corr-obs-success")));

        assertThat(counter("payment.api.requests", "operation", "create", "result", "accepted"))
                .isEqualTo(requestCounter + 1.0);
        assertThat(counter("payment.accepted")).isEqualTo(acceptedCounter + 1.0);
        assertThat(counter("payment.downstream.outcomes", "scenario", "success", "status", "COMPLETED"))
                .isEqualTo(downstreamCounter + 1.0);
        assertThat(counter("payment.status.distribution", "status", "COMPLETED"))
                .isEqualTo(statusCounter + 1.0);
        assertThat(timerCount("payment.api.latency", "operation", "create")).isGreaterThan(0);

        assertThat(output).contains("payment_creation_accepted");
        assertThat(output).contains("downstream_mock_outcome");
        assertThat(output).contains("correlationId=corr-obs-success");
        assertThat(output).contains("debtorAccount=******7890");
        assertThat(output).contains("creditorAccount=******3210");
        assertThat(output).doesNotContain("1234567890");
        assertThat(output).doesNotContain("9876543210");
    }

    @Test
    void idempotencyReplayAndConflictEmitMetrics() throws Exception {
        var replayCounter = counter("payment.idempotency.replays");
        var conflictCounter = counter("payment.idempotency.conflicts");

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "obs-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "obs-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "obs-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1234567890", "9876543210", "1251.50")))
                .andExpect(status().isConflict());

        assertThat(counter("payment.idempotency.replays")).isEqualTo(replayCounter + 1.0);
        assertThat(counter("payment.idempotency.conflicts")).isEqualTo(conflictCounter + 1.0);
    }

    @Test
    void validationAndAuthenticationFailuresEmitMetrics() throws Exception {
        var validationCounter = counter("payment.validation.failures");
        var unauthorizedCounter = counter("payment.auth.failures", "result", "unauthorized");
        var forbiddenCounter = counter("payment.auth.failures", "result", "forbidden");

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/domestic-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isForbidden());

        assertThat(counter("payment.validation.failures")).isEqualTo(validationCounter + 1.0);
        assertThat(counter("payment.auth.failures", "result", "unauthorized")).isEqualTo(unauthorizedCounter + 1.0);
        assertThat(counter("payment.auth.failures", "result", "forbidden")).isEqualTo(forbiddenCounter + 1.0);
    }

    private double counter(String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private long timerCount(String name, String... tags) {
        var timer = meterRegistry.find(name).tags(tags).timer();
        return timer == null ? 0 : timer.count();
    }

    private String bearer(String... scopes) {
        return "Bearer " + JwtTestSupport.tokenWithScopes("client-a", scopes);
    }

    private String validRequest(String debtorAccountNumber, String creditorAccountNumber, String amount) {
        return """
                {
                  "debtorAccount": {
                    "bankCode": "CIBBMYKL",
                    "accountNumber": "%s",
                    "accountName": "Acme Treasury"
                  },
                  "creditorAccount": {
                    "bankCode": "PAYBMYKL",
                    "accountNumber": "%s",
                    "accountName": "Supplier Sdn Bhd"
                  },
                  "amount": {
                    "currency": "MYR",
                    "value": "%s"
                  },
                  "paymentReference": "INV-2026-0001",
                  "remittanceInformation": "Invoice payment"
                }
                """.formatted(debtorAccountNumber, creditorAccountNumber, amount);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
