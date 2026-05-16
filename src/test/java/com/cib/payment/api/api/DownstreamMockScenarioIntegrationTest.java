package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DownstreamMockScenarioIntegrationTest.JwtTestConfiguration.class)
class DownstreamMockScenarioIntegrationTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    DownstreamMockScenarioIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @ParameterizedTest
    @CsvSource({
            "success,success-request.json,COMPLETED,",
            "rejection,rejection-request.json,REJECTED,MOCK_REJECTION",
            "timeout,timeout-request.json,TIMEOUT,MOCK_TIMEOUT",
            "internal_failure,internal-failure-request.json,FAILED,MOCK_INTERNAL_FAILURE"
    })
    void mockScenarioFixtureDrivesStatusOutcome(
            String scenario,
            String fixtureName,
            String expectedStatus,
            String expectedReasonCode) throws Exception {
        var paymentId = createPayment("scenario-key-" + scenario, scenario, fixture(fixtureName));

        var result = mockMvc.perform(get("/v1/domestic-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("payments:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId", equalTo(paymentId)))
                .andExpect(jsonPath("$.status", equalTo(expectedStatus)));

        if (expectedReasonCode == null) {
            result.andExpect(jsonPath("$.reason").doesNotExist());
        } else {
            result.andExpect(jsonPath("$.reason.code", equalTo(expectedReasonCode)));
        }
    }

    @Test
    void invalidFixtureReturnsValidationError() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-invalid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("invalid-request.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void idempotencyFixturesCoverReplayAndConflict() throws Exception {
        var first = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("idempotency-request.json")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("idempotency-request.json")))
                .andExpect(status().isAccepted())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(first));

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("idempotency-conflict-request.json")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")));
    }


    @Test
    void idempotencyReplayIgnoresMockScenarioHeaderChanges() throws Exception {
        var first = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-header-key")
                        .header("X-Mock-Scenario", "success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("idempotency-request.json")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-header-key")
                        .header("X-Mock-Scenario", "timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("idempotency-request.json")))
                .andExpect(status().isAccepted())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(first));
    }

    private String createPayment(String idempotencyKey, String scenario, String requestBody) throws Exception {
        var response = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Mock-Scenario", scenario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("paymentId").asText();
    }

    private String fixture(String fixtureName) throws Exception {
        return new ClassPathResource("fixtures/domestic-payments/" + fixtureName)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String... scopes) {
        return "Bearer " + JwtTestSupport.tokenWithScopes("client-a", scopes);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
