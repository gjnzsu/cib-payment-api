package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@Import(RtgsPaymentControllerIntegrationTest.JwtTestConfiguration.class)
class RtgsPaymentControllerIntegrationTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    RtgsPaymentControllerIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void corporateCreateAndOwnerStatusReturnJsonWithCorrelationAndSettlementFinality() throws Exception {
        var create = createRtgsPayment(
                "rtgs-client-a",
                "rtgs-api-corp",
                "corr-rtgs-api-corp",
                "rtgs_settled",
                corporateJson());
        var paymentId = create.get("paymentId").asText();

        assertThat(create.get("rail").asText()).isEqualTo("RTGS");
        assertThat(create.get("clientSegment").asText()).isEqualTo("CORPORATE");
        assertThat(create.get("status").asText()).isEqualTo("SETTLED");
        assertThat(create.get("settlementFinality").asBoolean()).isTrue();
        assertThat(create.get("correlationId").asText()).isEqualTo("corr-rtgs-api-corp");
        assertThat(create.at("/links/status").asText()).isEqualTo("/v1/rtgs-payments/" + paymentId);

        mockMvc.perform(get("/v1/rtgs-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("rtgs-client-a", "rtgs-payments:read"))
                        .header("X-Correlation-ID", "corr-rtgs-api-status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-rtgs-api-status"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paymentId", equalTo(paymentId)))
                .andExpect(jsonPath("$.paymentReference", equalTo("RTGS-2026-0001")))
                .andExpect(jsonPath("$.clientSegment", equalTo("CORPORATE")))
                .andExpect(jsonPath("$.debtorAccount.accountNumber", equalTo("123456789")))
                .andExpect(jsonPath("$.status", equalTo("SETTLED")))
                .andExpect(jsonPath("$.settlementFinality", equalTo(true)))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-rtgs-api-corp")))
                .andExpect(jsonPath("$.links.self", equalTo("/v1/rtgs-payments/" + paymentId)));
    }

    @Test
    void defaultScenarioMissingIdempotencyReplayAndScenarioConflictAreHandled() throws Exception {
        mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", bearer("rtgs-client-a", "rtgs-payments:create"))
                        .header("X-Correlation-ID", "corr-rtgs-api-missing-idem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(corporateJson()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-ID", "corr-rtgs-api-missing-idem"))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-rtgs-api-missing-idem")));

        var first = mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", bearer("rtgs-client-a", "rtgs-payments:create"))
                        .header("Idempotency-Key", "rtgs-api-replay")
                        .header("X-Correlation-ID", "corr-rtgs-api-original")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(corporateJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("SETTLED")))
                .andExpect(jsonPath("$.reason", nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var replay = mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", bearer("rtgs-client-a", "rtgs-payments:create"))
                        .header("Idempotency-Key", "rtgs-api-replay")
                        .header("X-Correlation-ID", "corr-rtgs-api-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(corporateJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-rtgs-api-replay"))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-rtgs-api-original")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(replay).isEqualTo(first);

        mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", bearer("rtgs-client-a", "rtgs-payments:create"))
                        .header("Idempotency-Key", "rtgs-api-replay")
                        .header("X-Correlation-ID", "corr-rtgs-api-conflict")
                        .header("X-Mock-Scenario", "rtgs_rejected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(corporateJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-rtgs-api-conflict")));
    }

    @Test
    void fiRtgsScenariosReturnExpectedStatusFinalityAndReason() throws Exception {
        var settled = createRtgsPayment(
                "rtgs-fi-client-a",
                "rtgs-api-fi-settled",
                "corr-rtgs-api-fi-settled",
                "rtgs_settled",
                fiJson());
        assertThat(settled.get("clientSegment").asText()).isEqualTo("FI");
        assertThat(settled.get("settlementFinality").asBoolean()).isTrue();

        mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", bearer("rtgs-fi-client-a", "rtgs-payments:create"))
                        .header("Idempotency-Key", "rtgs-api-fi-queued")
                        .header("X-Correlation-ID", "corr-rtgs-api-fi-queued")
                        .header("X-Mock-Scenario", "rtgs_queued_for_liquidity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fiJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.clientSegment", equalTo("FI")))
                .andExpect(jsonPath("$.status", equalTo("QUEUED_FOR_LIQUIDITY")))
                .andExpect(jsonPath("$.settlementFinality", equalTo(false)))
                .andExpect(jsonPath("$.reason.code", equalTo("RTGS_LIQUIDITY_QUEUE")));

        mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", bearer("rtgs-fi-client-a", "rtgs-payments:create"))
                        .header("Idempotency-Key", "rtgs-api-rejected")
                        .header("X-Correlation-ID", "corr-rtgs-api-rejected")
                        .header("X-Mock-Scenario", "rtgs_rejected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fiJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("REJECTED")))
                .andExpect(jsonPath("$.settlementFinality", equalTo(false)))
                .andExpect(jsonPath("$.reason.code", equalTo("RTGS_PAYMENT_REJECTED")));
    }

    @Test
    void statusQueryIsOwnerOnlyAndGeneratedCorrelationAppearsInHeaderAndBody() throws Exception {
        var response = mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", bearer("rtgs-client-owner", "rtgs-payments:create"))
                        .header("Idempotency-Key", "rtgs-api-generated-correlation")
                        .header("X-Mock-Scenario", "rtgs_settled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(corporateJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andReturn()
                .getResponse();
        var generatedCorrelationId = response.getHeader("X-Correlation-ID");
        var paymentId = objectMapper.readTree(response.getContentAsString()).get("paymentId").asText();
        assertThat(objectMapper.readTree(response.getContentAsString()).get("correlationId").asText())
                .isEqualTo(generatedCorrelationId);

        mockMvc.perform(get("/v1/rtgs-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("rtgs-client-other", "rtgs-payments:read"))
                        .header("X-Correlation-ID", "corr-rtgs-api-foreign")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-rtgs-api-foreign")));
    }

    private JsonNode createRtgsPayment(
            String clientId,
            String idempotencyKey,
            String correlationId,
            String scenario,
            String content) throws Exception {
        var response = mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", bearer(clientId, "rtgs-payments:create"))
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Correlation-ID", correlationId)
                        .header("X-Mock-Scenario", scenario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", correlationId))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private String corporateJson() {
        return """
                {
                  "paymentReference": "RTGS-2026-0001",
                  "clientSegment": "CORPORATE",
                  "debtorAccount": {
                    "bankCode": "CITIUS33",
                    "accountNumber": "123456789",
                    "accountName": "Acme Operating"
                  },
                  "creditorAccount": {
                    "bankCode": "BOFAUS3N",
                    "accountNumber": "987654321",
                    "accountName": "Beta Supplier"
                  },
                  "amount": {
                    "currency": "USD",
                    "value": "1250.50"
                  },
                  "requestedSettlementDate": "2026-06-05",
                  "settlementPriority": "URGENT",
                  "purpose": "Treasury transfer"
                }
                """;
    }

    private String fiJson() {
        return """
                {
                  "paymentReference": "RTGS-2026-0002",
                  "clientSegment": "FI",
                  "instructingAgentBic": "CITIUS33XXX",
                  "instructedAgentBic": "IRVTUS3NXXX",
                  "amount": {
                    "currency": "USD",
                    "value": "5000000.00"
                  },
                  "requestedSettlementDate": "2026-06-05",
                  "settlementPriority": "NORMAL",
                  "purpose": "FI settlement"
                }
                """;
    }

    private String bearer(String clientId, String... scopes) {
        return "Bearer " + JwtTestSupport.tokenWithScopes(clientId, scopes);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
