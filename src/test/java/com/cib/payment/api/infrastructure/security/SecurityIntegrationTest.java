package com.cib.payment.api.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
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
@Import(SecurityIntegrationTest.JwtTestConfiguration.class)
class SecurityIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    SecurityIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("X-Correlation-ID", "security-corr-401")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "security-corr-401"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required or invalid"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.correlationId").value("security-corr-401"));
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.expiredToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithWrongAudience())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingSubjectClaimReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenMissingClaim("sub"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithInvalidSignature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCreateScopeReturnsForbiddenOnPost() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "payments:read"))
                        .header("X-Correlation-ID", "security-corr-403")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-ID", "security-corr-403"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Authenticated client lacks the required scope"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.correlationId").value("security-corr-403"));
    }

    @Test
    void missingReadScopeReturnsForbiddenOnGet() throws Exception {
        mockMvc.perform(get("/v1/domestic-payments/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "payments:create")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingFiCreateScopeReturnsForbiddenOnPost() throws Exception {
        mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.fiTokenWithScopes("client-a", "payments:create"))
                        .header("X-Correlation-ID", "security-fi-create-403")
                        .contentType("application/pacs.009+xml")
                        .content("<Document/>"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-ID", "security-fi-create-403"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void missingFiReadScopeReturnsForbiddenOnGet() throws Exception {
        mockMvc.perform(get("/v1/fi-payments/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", "Bearer " + JwtTestSupport.fiTokenWithScopes("client-a", "payments:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingFiInvestigateScopeReturnsForbiddenOnRecallPost() throws Exception {
        mockMvc.perform(post("/v1/fi-payments/550e8400-e29b-41d4-a716-446655440000/recall-requests")
                        .header("Authorization", "Bearer " + JwtTestSupport.fiTokenWithScopes("client-a", "payments:create"))
                        .contentType("application/camt.056+xml")
                        .content("<Document/>"))
                .andExpect(status().isForbidden());
    }

    @Test
    void achCreateRequiresRailSpecificScopeBeforePayloadValidation() throws Exception {
        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "payments:create"))
                        .header("X-Correlation-ID", "security-ach-create-403")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-ID", "security-ach-create-403"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").value("security-ach-create-403"));
    }

    @Test
    void achCreateWithRailSpecificScopeReachesIdempotencyValidation() throws Exception {
        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "ach-batches:create"))
                        .header("X-Correlation-ID", "security-ach-create-valid-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAchBatchJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("security-ach-create-valid-scope"));
    }

    @Test
    void achReadRequiresRailSpecificScope() throws Exception {
        mockMvc.perform(get("/v1/ach-batches/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "payments:read"))
                        .header("X-Correlation-ID", "security-ach-read-403"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-ID", "security-ach-read-403"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void achReadWithRailSpecificScopeReachesStatusLookupWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(get("/v1/ach-batches/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "ach-batches:read"))
                        .header("X-Correlation-ID", "security-ach-read-valid-scope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("security-ach-read-valid-scope"));
    }

    @Test
    void rtgsCreateRequiresRailSpecificScopeBeforePayloadValidation() throws Exception {
        mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "payments:create"))
                        .header("X-Correlation-ID", "security-rtgs-create-403")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-ID", "security-rtgs-create-403"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").value("security-rtgs-create-403"));
    }

    @Test
    void rtgsCreateWithRailSpecificScopeReachesIdempotencyValidation() throws Exception {
        mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "rtgs-payments:create"))
                        .header("X-Correlation-ID", "security-rtgs-create-valid-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRtgsPaymentJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("security-rtgs-create-valid-scope"));
    }

    @Test
    void rtgsReadRequiresRailSpecificScope() throws Exception {
        mockMvc.perform(get("/v1/rtgs-payments/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "payments:read"))
                        .header("X-Correlation-ID", "security-rtgs-read-403"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-ID", "security-rtgs-read-403"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rtgsReadWithRailSpecificScopeReachesStatusLookupWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(get("/v1/rtgs-payments/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "rtgs-payments:read"))
                        .header("X-Correlation-ID", "security-rtgs-read-valid-scope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("security-rtgs-read-valid-scope"));
    }

    private String validAchBatchJson() {
        return """
                {
                  "batchReference": "ACH-SECURITY-0001",
                  "originatorName": "CIB Payroll Services",
                  "effectiveEntryDate": "2026-06-02",
                  "settlementAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "123456789",
                    "accountName": "CIB Operating"
                  },
                  "entries": [
                    {
                      "entryReference": "ACH-SECURITY-ENTRY-0001",
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
                    }
                  ]
                }
                """;
    }

    private String validRtgsPaymentJson() {
        return """
                {
                  "paymentReference": "RTGS-SECURITY-0001",
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

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
