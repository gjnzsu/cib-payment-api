package com.cib.payment.api.api;

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
@Import(MandateControllerIntegrationTest.JwtTestConfiguration.class)
class MandateControllerIntegrationTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    MandateControllerIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void createQueryAndCancelMandateJourneyUsesJsonCorrelationAndScopes() throws Exception {
        var createResponse = mockMvc.perform(post("/v1/mandates")
                        .header("Authorization", bearer("mandate-client-a", "mandates:create"))
                        .header("Idempotency-Key", "mandate-api-create")
                        .header("X-Correlation-ID", "corr-mandate-api-create")
                        .header("X-Mock-Scenario", "mandate_active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchMandateJson("MANDATE-US-API-001")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-mandate-api-create"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mandateId", notNullValue()))
                .andExpect(jsonPath("$.mandateReference", equalTo("MANDATE-US-API-001")))
                .andExpect(jsonPath("$.mandateProfile", equalTo("US_ACH_DEBIT_MANDATE")))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-mandate-api-create")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var mandateId = objectMapper.readTree(createResponse).get("mandateId").asText();

        mockMvc.perform(get("/v1/mandates/{mandateId}", mandateId)
                        .header("Authorization", bearer("mandate-client-a", "mandates:read"))
                        .header("X-Correlation-ID", "corr-mandate-api-status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-mandate-api-status"))
                .andExpect(jsonPath("$.mandateId", equalTo(mandateId)))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-mandate-api-create")));

        mockMvc.perform(post("/v1/mandates/{mandateId}/cancel", mandateId)
                        .header("Authorization", bearer("mandate-client-a", "mandates:cancel"))
                        .header("Idempotency-Key", "mandate-api-cancel")
                        .header("X-Correlation-ID", "corr-mandate-api-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"PAYER_REQUEST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandateId", equalTo(mandateId)))
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-mandate-api-create")));
    }

    @Test
    void cancelMandateAllowsMissingRequestBody() throws Exception {
        var createResponse = mockMvc.perform(post("/v1/mandates")
                        .header("Authorization", bearer("mandate-client-a", "mandates:create"))
                        .header("Idempotency-Key", "mandate-api-create-no-cancel-body")
                        .header("X-Correlation-ID", "corr-mandate-api-create-no-cancel-body")
                        .header("X-Mock-Scenario", "mandate_active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchMandateJson("MANDATE-US-API-NO-CANCEL-BODY")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var mandateId = objectMapper.readTree(createResponse).get("mandateId").asText();

        mockMvc.perform(post("/v1/mandates/{mandateId}/cancel", mandateId)
                        .header("Authorization", bearer("mandate-client-a", "mandates:cancel"))
                        .header("Idempotency-Key", "mandate-api-cancel-no-body")
                        .header("X-Correlation-ID", "corr-mandate-api-cancel-no-body")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")))
                .andExpect(jsonPath("$.reason.message", equalTo("Mandate was cancelled")));
    }

    @Test
    void mandateEndpointsRequireScopesAndIdempotency() throws Exception {
        mockMvc.perform(post("/v1/mandates")
                        .header("Authorization", bearer("mandate-client-a", "collections:create"))
                        .header("Idempotency-Key", "mandate-api-wrong-scope")
                        .header("X-Correlation-ID", "corr-mandate-api-wrong-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchMandateJson("MANDATE-US-API-SCOPE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-mandate-api-wrong-scope")));

        mockMvc.perform(post("/v1/mandates")
                        .header("Authorization", bearer("mandate-client-a", "mandates:create"))
                        .header("X-Correlation-ID", "corr-mandate-api-missing-idem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchMandateJson("MANDATE-US-API-MISSING-IDEM")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-mandate-api-missing-idem")));
    }

    @Test
    void collectionsRejectInactiveSystemMandateButAcceptExternalReference() throws Exception {
        mockMvc.perform(post("/v1/mandates")
                        .header("Authorization", bearer("mandate-client-a", "mandates:create"))
                        .header("Idempotency-Key", "mandate-api-pending")
                        .header("X-Correlation-ID", "corr-mandate-api-pending")
                        .header("X-Mock-Scenario", "mandate_pending_authorization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchMandateJson("MANDATE-US-PENDING")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("PENDING_AUTHORIZATION")));

        mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("mandate-client-a", "collections:create"))
                        .header("Idempotency-Key", "collection-api-inactive-mandate")
                        .header("X-Correlation-ID", "corr-collection-api-inactive-mandate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchCollectionJson("MANDATE-US-PENDING")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-collection-api-inactive-mandate")));

        mockMvc.perform(post("/v1/collections")
                        .header("Authorization", bearer("mandate-client-a", "collections:create"))
                        .header("Idempotency-Key", "collection-api-external-mandate")
                        .header("X-Correlation-ID", "corr-collection-api-external-mandate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(usAchCollectionJson("EXTERNAL-MANDATE-OK")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("COLLECTED")));
    }

    private String usAchMandateJson(String mandateReference) {
        return """
                {
                  "mandateProfile": "US_ACH_DEBIT_MANDATE",
                  "mandateReference": "%s",
                  "creditorName": "CIB Collection Services",
                  "debtorName": "Corporate Customer",
                  "creditorAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "123456789",
                    "accountName": "CIB Collections"
                  },
                  "debtorAccount": {
                    "bankCode": "111000025",
                    "accountNumber": "987654321",
                    "accountName": "Corporate Customer"
                  },
                  "maximumAmount": {
                    "currency": "USD",
                    "value": "1000.00"
                  },
                  "frequency": "VARIABLE",
                  "purpose": "MONTHLY_COLLECTION"
                }
                """.formatted(mandateReference);
    }

    private String usAchCollectionJson(String mandateReference) {
        return """
                {
                  "collectionProfile": "US_ACH_DIRECT_DEBIT_BATCH",
                  "collectionReference": "COLL-US-ACH-MANDATE-0001",
                  "mandateReference": "%s",
                  "creditorName": "CIB Collection Services",
                  "debtorName": "Corporate Customer",
                  "settlementAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "123456789",
                    "accountName": "CIB Collections"
                  },
                  "entries": [
                    {
                      "entryReference": "COLL-ENTRY-MANDATE-0001",
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
                    }
                  ]
                }
                """.formatted(mandateReference);
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
