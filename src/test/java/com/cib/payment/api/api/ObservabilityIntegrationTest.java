package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO",
        "logging.level.org.springframework.web=INFO"
})
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
        var requestCounter = counter("payment.api.requests", "operation", "create", "result", "ok");
        var admittedCounter = counter("payment.iso.initiation.admitted");
        var mappedCounter = counter("payment.engine.mapped");
        var simulatorCounter = counter("payment.hk.simulator.outcomes", "scenario", "success", "status", "COMPLETED");
        var pain002Counter = counter("payment.pain002.generated", "status", "COMPLETED");

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "obs-success-key")
                        .header("X-Correlation-ID", "corr-obs-success")
                        .header("X-Mock-Scenario", "success")
                        .contentType("application/pain.001+xml")
                        .accept("application/pain.002+xml")
                        .content(validPain001("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isOk());

        assertThat(counter("payment.api.requests", "operation", "create", "result", "ok"))
                .isEqualTo(requestCounter + 1.0);
        assertThat(counter("payment.iso.initiation.admitted")).isEqualTo(admittedCounter + 1.0);
        assertThat(counter("payment.engine.mapped")).isEqualTo(mappedCounter + 1.0);
        assertThat(counter("payment.hk.simulator.outcomes", "scenario", "success", "status", "COMPLETED"))
                .isEqualTo(simulatorCounter + 1.0);
        assertThat(counter("payment.pain002.generated", "status", "COMPLETED"))
                .isEqualTo(pain002Counter + 1.0);
        assertThat(timerCount("payment.api.latency", "operation", "create", "result", "ok")).isGreaterThan(0);

        assertThat(output).contains("iso_payment_initiation_admitted");
        assertThat(output).contains("engine_payment_mapped");
        assertThat(output).contains("hk_simulator_outcome");
        assertThat(output).contains("pain002_generated");
        assertThat(output).contains("correlationId=corr-obs-success");
        assertThat(output).contains("debtorAccount=******7890");
        assertThat(output).contains("creditorAccount=******3210");
        assertThat(output).doesNotContain("<Document");
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
                        .contentType("application/pain.001+xml")
                        .content(validPain001("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "obs-idempotency-key")
                        .contentType("application/pain.001+xml")
                        .content(validPain001("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "obs-idempotency-key")
                        .contentType("application/pain.001+xml")
                        .content(validPain001("1234567890", "9876543210", "1251.50")))
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
                        .contentType("application/pain.001+xml")
                        .content(validPain001("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/domestic-payments")
                        .contentType("application/pain.001+xml")
                        .content(validPain001("1234567890", "9876543210", "1250.50")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:read"))
                        .contentType("application/pain.001+xml")
                        .content(validPain001("1234567890", "9876543210", "1250.50")))
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

    private String validPain001(String debtorAccountNumber, String creditorAccountNumber, String amount) throws Exception {
        return new ClassPathResource("iso/pain001-success.xml")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("000123456789", debtorAccountNumber)
                .replace("000987654321", creditorAccountNumber)
                .replace("1250.00", amount);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
