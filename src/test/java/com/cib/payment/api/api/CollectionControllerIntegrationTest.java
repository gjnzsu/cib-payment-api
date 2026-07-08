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
@Import(CollectionControllerIntegrationTest.JwtTestConfiguration.class)
class CollectionControllerIntegrationTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    CollectionControllerIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void postCollectionReturnsAcceptedJsonAndOwnerStatusWithCorrelation() throws Exception {
        var createResponse = mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("collection-client-a", "collections:create"))
                        .header("Idempotency-Key", "collection-api-create")
                        .header("X-Correlation-ID", "corr-collection-api-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchDebitJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-collection-api-create"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.collectionId", notNullValue()))
                .andExpect(jsonPath("$.collectionProfile", equalTo("US_ACH_DIRECT_DEBIT_BATCH")))
                .andExpect(jsonPath("$.status", equalTo("COLLECTED")))
                .andExpect(jsonPath("$.entryCount", equalTo(2)))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-create")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var collectionId = objectMapper.readTree(createResponse).get("collectionId").asText();

        mockMvc.perform(get("/v1/collections/{collectionId}", collectionId)
                        .header("Authorization", bearer("collection-client-a", "collections:read"))
                        .header("X-Correlation-ID", "corr-collection-api-status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-collection-api-status"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.collectionId", equalTo(collectionId)))
                .andExpect(jsonPath("$.collectionReference", equalTo("COLL-US-ACH-20260707-0001")))
                .andExpect(jsonPath("$.status", equalTo("COLLECTED")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-create")))
                .andExpect(jsonPath("$.links.self", equalTo("/v1/collections/" + collectionId)));
    }

    @Test
    void hkFpsCollectionScenarioReturnsCompletedStatus() throws Exception {
        mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("collection-client-a", "collections:create"))
                        .header("Idempotency-Key", "collection-api-hk")
                        .header("X-Correlation-ID", "corr-collection-api-hk")
                        .header("X-Mock-Scenario", "hk_fps_collection_completed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(hkFpsJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.collectionProfile", equalTo("HK_FPS_DIRECT_DEBIT")))
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")))
                .andExpect(jsonPath("$.totalAmount.currency", equalTo("HKD")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-hk")));
    }

    @Test
    void missingIdempotencyKeyConflictReplayAndForeignOwnerAreHandled() throws Exception {
        mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("collection-client-a", "collections:create"))
                        .header("X-Correlation-ID", "corr-collection-api-missing-idem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchDebitJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-missing-idem")));

        var first = mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("collection-client-a", "collections:create"))
                        .header("Idempotency-Key", "collection-api-replay")
                        .header("X-Correlation-ID", "corr-collection-api-original")
                        .header("X-Mock-Scenario", "us_ach_debit_collected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchDebitJson()))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var replay = mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("collection-client-a", "collections:create"))
                        .header("Idempotency-Key", "collection-api-replay")
                        .header("X-Correlation-ID", "corr-collection-api-replay")
                        .header("X-Mock-Scenario", "us_ach_debit_collected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchDebitJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-original")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(replay).isEqualTo(first);

        mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("collection-client-a", "collections:create"))
                        .header("Idempotency-Key", "collection-api-replay")
                        .header("X-Correlation-ID", "corr-collection-api-conflict")
                        .header("X-Mock-Scenario", "us_ach_debit_partially_returned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchDebitJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-conflict")));

        var collectionId = objectMapper.readTree(first).get("collectionId").asText();
        mockMvc.perform(get("/v1/collections/{collectionId}", collectionId)
                        .header("Authorization", bearer("collection-client-b", "collections:read"))
                        .header("X-Correlation-ID", "corr-collection-api-foreign-owner")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-foreign-owner")));
    }

    @Test
    void collectionCreateAndReadRequireCollectionScopes() throws Exception {
        mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("collection-client-a", "payments:create"))
                        .header("Idempotency-Key", "collection-api-wrong-create-scope")
                        .header("X-Correlation-ID", "corr-collection-api-wrong-create-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchDebitJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-wrong-create-scope")));

        mockMvc.perform(get("/v1/collections/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", bearer("collection-client-a", "payments:read"))
                        .header("X-Correlation-ID", "corr-collection-api-wrong-read-scope")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-wrong-read-scope")));
    }

    private String usAchDebitJson() {
        return """
                {
                  "collectionProfile": "US_ACH_DIRECT_DEBIT_BATCH",
                  "collectionReference": "COLL-US-ACH-20260707-0001",
                  "mandateReference": "MANDATE-US-001",
                  "creditorName": "CIB Collection Services",
                  "debtorName": "Corporate Customer",
                  "settlementAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "123456789",
                    "accountName": "CIB Collections"
                  },
                  "entries": [
                    {
                      "entryReference": "COLL-ENTRY-0001",
                      "payerName": "Payer One",
                      "payerAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "987654321",
                        "accountName": "Payer One"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "125.40"
                      },
                      "purpose": "INVOICE_COLLECTION"
                    },
                    {
                      "entryReference": "COLL-ENTRY-0002",
                      "payerName": "Payer Two",
                      "payerAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "987654322",
                        "accountName": "Payer Two"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "225.10"
                      },
                      "purpose": "INVOICE_COLLECTION"
                    }
                  ]
                }
                """;
    }

    private String hkFpsJson() {
        return """
                {
                  "collectionProfile": "HK_FPS_DIRECT_DEBIT",
                  "collectionReference": "COLL-HK-FPS-20260707-0001",
                  "mandateReference": "EDDA-HK-001",
                  "creditorName": "Sample Merchant HK",
                  "debtorName": "Sample Payer HK",
                  "payerBankCode": "004",
                  "payerAlias": "FPS-PROXY-ALIAS",
                  "amount": {
                    "currency": "HKD",
                    "value": "350.00"
                  },
                  "purpose": "MONTHLY_TUTORIAL_FEE",
                  "entries": []
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
