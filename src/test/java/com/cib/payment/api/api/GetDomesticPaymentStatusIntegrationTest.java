package com.cib.payment.api.api;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@Import(GetDomesticPaymentStatusIntegrationTest.JwtTestConfiguration.class)
class GetDomesticPaymentStatusIntegrationTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    GetDomesticPaymentStatusIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void createThenGetReturnsCurrentStoredStatus() throws Exception {
        var paymentId = createPayment("client-a", "status-key-1", "rejection");

        mockMvc.perform(get("/v1/domestic-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("client-a", "payments:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId", equalTo(paymentId)))
                .andExpect(jsonPath("$.status", equalTo("REJECTED")))
                .andExpect(jsonPath("$.reason.code", equalTo("MOCK_REJECTION")))
                .andExpect(jsonPath("$.links.self", equalTo("/v1/domestic-payments/" + paymentId)));
    }

    @Test
    void unknownUuidReturnsNotFound() throws Exception {
        mockMvc.perform(get("/v1/domestic-payments/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", bearer("client-a", "payments:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")));
    }

    @Test
    void malformedUuidReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/domestic-payments/not-a-uuid")
                        .header("Authorization", bearer("client-a", "payments:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void crossClientLookupReturnsNotFound() throws Exception {
        var paymentId = createPayment("client-a", "status-key-2", "success");

        mockMvc.perform(get("/v1/domestic-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("client-b", "payments:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")));
    }

    @Test
    void getDoesNotRequireIdempotencyKey() throws Exception {
        var paymentId = createPayment("client-a", "status-key-3", "success");

        mockMvc.perform(get("/v1/domestic-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("client-a", "payments:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")));
    }

    private String createPayment(String clientId, String idempotencyKey, String scenario) throws Exception {
        var response = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer(clientId, "payments:create"))
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Mock-Scenario", scenario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("paymentId").asText();
    }

    private String bearer(String clientId, String... scopes) {
        return "Bearer " + JwtTestSupport.tokenWithScopes(clientId, scopes);
    }

    private String validRequest() {
        return """
                {
                  "debtorAccount": {
                    "bankCode": "CIBBMYKL",
                    "accountNumber": "1234567890",
                    "accountName": "Acme Treasury"
                  },
                  "creditorAccount": {
                    "bankCode": "PAYBMYKL",
                    "accountNumber": "9876543210",
                    "accountName": "Supplier Sdn Bhd"
                  },
                  "amount": {
                    "currency": "MYR",
                    "value": "1250.50"
                  },
                  "paymentReference": "INV-2026-0001"
                }
                """;
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
