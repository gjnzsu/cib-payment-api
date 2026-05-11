package com.cib.payment.api.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import com.jayway.jsonpath.JsonPath;
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
@Import(CreateDomesticPaymentIntegrationTest.JwtTestConfiguration.class)
class CreateDomesticPaymentIntegrationTest {
    private static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final MockMvc mockMvc;

    @Autowired
    CreateDomesticPaymentIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void validRequestReturnsAcceptedPaymentResponse() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "create-key-1")
                        .header("X-Correlation-ID", "corr-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1250.50", "MYR")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-create-1"))
                .andExpect(jsonPath("$.paymentId", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-create-1")))
                .andExpect(jsonPath("$.links.status", matchesPattern("^/v1/domestic-payments/" + UUID_PATTERN.substring(1))));
    }

    @Test
    void sameIdempotencyKeyAndSameBodyReplaysOriginalResponse() throws Exception {
        var first = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "create-key-2")
                        .header("X-Correlation-ID", "corr-create-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1250.50", "MYR")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "create-key-2")
                        .header("X-Correlation-ID", "corr-create-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1250.50", "MYR")))
                .andExpect(status().isAccepted())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).isEqualTo(first));
    }

    @Test
    void sameIdempotencyKeyAndDifferentBodyReturnsConflict() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "create-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1250.50", "MYR")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "create-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1251.50", "MYR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    void sameIdempotencyKeyCanBeUsedByDifferentClients() throws Exception {
        var first = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearerForClient("client-a", "payments:create"))
                        .header("Idempotency-Key", "create-key-client-scoped")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1250.50", "MYR")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var second = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearerForClient("client-b", "payments:create"))
                        .header("Idempotency-Key", "create-key-client-scoped")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1250.50", "MYR")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(first, "$.paymentId"))
                .isNotEqualTo(JsonPath.<String>read(second, "$.paymentId"));
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1250.50", "MYR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void unsupportedCurrencyReturnsSemanticError() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "create-key-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1250.50", "USD")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", equalTo("SEMANTIC_PAYMENT_ERROR")));
    }

    @Test
    void unknownTopLevelFieldReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "create-key-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "debtorAccount": {"bankCode":"CIBBMYKL","accountNumber":"1234567890","accountName":"Acme Treasury"},
                                  "creditorAccount": {"bankCode":"PAYBMYKL","accountNumber":"9876543210","accountName":"Supplier Sdn Bhd"},
                                  "amount": {"currency":"MYR","value":"1250.50"},
                                  "paymentReference": "INV-2026-0001",
                                  "unsupported": "value"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    private String bearer(String... scopes) {
        return bearerForClient("client-a", scopes);
    }

    private String bearerForClient(String clientId, String... scopes) {
        return "Bearer " + JwtTestSupport.tokenWithScopes(clientId, scopes);
    }

    private String validRequest(String amount, String currency) {
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
                    "currency": "%s",
                    "value": "%s"
                  },
                  "paymentReference": "INV-2026-0001",
                  "remittanceInformation": "Invoice payment"
                }
                """.formatted(currency, amount);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
