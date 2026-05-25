package com.cib.payment.api.api;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
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
@Import(CreateDomesticPaymentIntegrationTest.JwtTestConfiguration.class)
class CreateDomesticPaymentIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    CreateDomesticPaymentIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void customJsonPaymentInitiationIsUnsupportedUnderIsoOnlyContract() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "json-unsupported-key")
                        .header("X-Correlation-ID", "corr-json-unsupported")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-ID", "corr-json-unsupported"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-json-unsupported")));
    }

    @Test
    void missingIdempotencyKeyStillReturnsJsonValidationError() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .contentType("application/xml")
                        .content(fixture("iso/pain001-success.xml")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void jsonRequestDoesNotCreateAcceptedIdempotencyRecord() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "json-rejected-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "json-rejected-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonRequest().replace("1250.50", "1251.50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    private String fixture(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String... scopes) {
        return "Bearer " + JwtTestSupport.tokenWithScopes("client-a", scopes);
    }

    private String validJsonRequest() {
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
