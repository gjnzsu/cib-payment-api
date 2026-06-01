package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
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
@Import(AchBatchControllerIntegrationTest.JwtTestConfiguration.class)
class AchBatchControllerIntegrationTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    AchBatchControllerIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void postAchBatchReturnsAcceptedJsonAndOwnerStatusWithCorrelation() throws Exception {
        var createResponse = mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "ach-batches:create"))
                        .header("Idempotency-Key", "ach-api-create")
                        .header("X-Correlation-ID", "corr-ach-api-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-ach-api-create"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.batchId", notNullValue()))
                .andExpect(jsonPath("$.rail", equalTo("ACH")))
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED_FOR_CLEARING")))
                .andExpect(jsonPath("$.entryCount", equalTo(2)))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-create")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var batchId = objectMapper.readTree(createResponse).get("batchId").asText();

        mockMvc.perform(get("/v1/ach-batches/{batchId}", batchId)
                        .header("Authorization", bearer("ach-client-a", "ach-batches:read"))
                        .header("X-Correlation-ID", "corr-ach-api-status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-ach-api-status"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.batchId", equalTo(batchId)))
                .andExpect(jsonPath("$.batchReference", equalTo("ACH-BATCH-20260601-0001")))
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED_FOR_CLEARING")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-create")))
                .andExpect(jsonPath("$.links.self", equalTo("/v1/ach-batches/" + batchId)));
    }

    @Test
    void missingIdempotencyKeyConflictReplayAndForeignOwnerAreHandled() throws Exception {
        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "ach-batches:create"))
                        .header("X-Correlation-ID", "corr-ach-api-missing-idem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-missing-idem")));

        var first = mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "ach-batches:create"))
                        .header("Idempotency-Key", "ach-api-replay")
                        .header("X-Correlation-ID", "corr-ach-api-original")
                        .header("X-Mock-Scenario", "ach_direct_credit_accepted")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var replay = mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "ach-batches:create"))
                        .header("Idempotency-Key", "ach-api-replay")
                        .header("X-Correlation-ID", "corr-ach-api-replay")
                        .header("X-Mock-Scenario", "ach_direct_credit_accepted")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-ach-api-replay"))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-original")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(replay).isEqualTo(first);

        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "ach-batches:create"))
                        .header("Idempotency-Key", "ach-api-replay")
                        .header("X-Correlation-ID", "corr-ach-api-conflict")
                        .header("X-Mock-Scenario", "ach_direct_credit_partially_returned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-conflict")));

        var batchId = objectMapper.readTree(first).get("batchId").asText();
        mockMvc.perform(get("/v1/ach-batches/{batchId}", batchId)
                        .header("Authorization", bearer("ach-client-b", "ach-batches:read"))
                        .header("X-Correlation-ID", "corr-ach-api-foreign-owner")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-foreign-owner")));
    }

    @Test
    void defaultAndPartiallyReturnedScenariosReturnExpectedAchStatuses() throws Exception {
        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "ach-batches:create"))
                        .header("Idempotency-Key", "ach-api-default-scenario")
                        .header("X-Correlation-ID", "corr-ach-api-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED_FOR_CLEARING")));

        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "ach-batches:create"))
                        .header("Idempotency-Key", "ach-api-partial")
                        .header("X-Correlation-ID", "corr-ach-api-partial")
                        .header("X-Mock-Scenario", "ach_direct_credit_partially_returned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("PARTIALLY_RETURNED")))
                .andExpect(jsonPath("$.reason.code", equalTo("ACH_PARTIAL_RETURN")))
                .andExpect(jsonPath("$.entries[0].status", equalTo("RETURNED")))
                .andExpect(jsonPath("$.entries[0].reason.code", equalTo("ACH_ENTRY_RETURNED")))
                .andExpect(jsonPath("$.entries[1].status", equalTo("SETTLED")));
    }

    @Test
    void achCreateAndReadRequireRailSpecificScopes() throws Exception {
        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "payments:create"))
                        .header("Idempotency-Key", "ach-api-wrong-create-scope")
                        .header("X-Correlation-ID", "corr-ach-api-wrong-create-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-wrong-create-scope")));

        mockMvc.perform(get("/v1/ach-batches/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", bearer("ach-client-a", "payments:read"))
                        .header("X-Correlation-ID", "corr-ach-api-wrong-read-scope")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-wrong-read-scope")));
    }

    @Test
    void duplicateEntryReferenceReturnsValidationDetails() throws Exception {
        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", bearer("ach-client-a", "ach-batches:create"))
                        .header("Idempotency-Key", "ach-api-duplicate-entry")
                        .header("X-Correlation-ID", "corr-ach-api-duplicate-entry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(duplicateEntryReferenceJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details[0].field", equalTo("entries[1].entryReference")))
                .andExpect(jsonPath("$.details[0].message", equalTo("Duplicate ACH entryReference")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-ach-api-duplicate-entry")));
    }

    private String validJson() {
        return """
                {
                  "batchReference": "ACH-BATCH-20260601-0001",
                  "originatorName": "CIB Payroll Services",
                  "effectiveEntryDate": "2026-06-02",
                  "settlementAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "123456789",
                    "accountName": "CIB Operating"
                  },
                  "entries": [
                    {
                      "entryReference": "ACH-ENTRY-0001",
                      "receiverName": "Receiver One",
                      "receiverAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "987654321",
                        "accountName": "Receiver One"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "125.40"
                      },
                      "purpose": "PAYROLL"
                    },
                    {
                      "entryReference": "ACH-ENTRY-0002",
                      "receiverName": "Receiver Two",
                      "receiverAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "987654322",
                        "accountName": "Receiver Two"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "225.10"
                      },
                      "purpose": "PAYROLL"
                    }
                  ]
                }
                """;
    }

    private String duplicateEntryReferenceJson() {
        return """
                {
                  "batchReference": "ACH-BATCH-20260601-0002",
                  "originatorName": "CIB Payroll Services",
                  "effectiveEntryDate": "2026-06-02",
                  "settlementAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "123456789",
                    "accountName": "CIB Operating"
                  },
                  "entries": [
                    {
                      "entryReference": "ACH-ENTRY-DUP",
                      "receiverName": "Receiver One",
                      "receiverAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "987654321",
                        "accountName": "Receiver One"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "125.40"
                      },
                      "purpose": "PAYROLL"
                    },
                    {
                      "entryReference": "ACH-ENTRY-DUP",
                      "receiverName": "Receiver Two",
                      "receiverAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "987654322",
                        "accountName": "Receiver Two"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "225.10"
                      },
                      "purpose": "PAYROLL"
                    }
                  ]
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
