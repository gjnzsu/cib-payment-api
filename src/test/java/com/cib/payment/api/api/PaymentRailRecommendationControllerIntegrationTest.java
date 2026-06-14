package com.cib.payment.api.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
@Import(PaymentRailRecommendationControllerIntegrationTest.JwtTestConfiguration.class)
class PaymentRailRecommendationControllerIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    PaymentRailRecommendationControllerIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void recommendsRtpWithCorrelationAndNoIdempotencyRequirement() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-rtp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSegment": "CORPORATE",
                                  "paymentPurpose": "SUPPLIER_PAYMENT",
                                  "paymentCount": 1,
                                  "amountSummary": {
                                    "currency": "USD",
                                    "totalAmount": "250.00",
                                    "maxSingleAmount": "250.00"
                                  },
                                  "debtorCountry": "US",
                                  "creditorCountry": "US",
                                  "urgency": "IMMEDIATE",
                                  "creditorType": "CORPORATE",
                                  "requiresFinality": false,
                                  "batchPreferred": false,
                                  "costSensitivity": "MEDIUM",
                                  "fiToFi": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-rec-rtp"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.recommendationId", notNullValue()))
                .andExpect(jsonPath("$.recommendationStatus", equalTo("RECOMMENDED")))
                .andExpect(jsonPath("$.recommendedOption.rail", equalTo("RTP")))
                .andExpect(jsonPath("$.recommendedOption.arrangement", equalTo("DOMESTIC_REAL_TIME_CLEARING")))
                .andExpect(jsonPath("$.confidenceLevel", equalTo("HIGH")))
                .andExpect(jsonPath("$.matchedFactors", hasItem("IMMEDIATE_URGENCY")))
                .andExpect(jsonPath("$.nextApiGuidance.endpoint", equalTo("/v1/domestic-payments")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-rec-rtp")));
    }

    @Test
    void recommendsAchWithMissingMaxSingleAmountWarning() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-ach-warning")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSegment": "CORPORATE",
                                  "paymentCount": 250,
                                  "amountSummary": {
                                    "currency": "USD",
                                    "totalAmount": "125000.00"
                                  },
                                  "debtorCountry": "US",
                                  "creditorCountry": "US",
                                  "urgency": "STANDARD",
                                  "batchPreferred": true,
                                  "costSensitivity": "HIGH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedOption.rail", equalTo("ACH")))
                .andExpect(jsonPath("$.warnings[0].code", equalTo("MAX_SINGLE_AMOUNT_NOT_PROVIDED")))
                .andExpect(jsonPath("$.nextApiGuidance.endpoint", equalTo("/v1/ach-batches")));
    }

    @Test
    void recommendsAchWithRtgsAlternativeWhenBatchHasFinalityConflict() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-ach-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSegment": "CORPORATE",
                                  "paymentCount": 4,
                                  "amountSummary": {
                                    "currency": "USD",
                                    "totalAmount": "500000.00",
                                    "maxSingleAmount": "250000.00"
                                  },
                                  "debtorCountry": "US",
                                  "creditorCountry": "US",
                                  "urgency": "SAME_DAY",
                                  "requiresFinality": true,
                                  "batchPreferred": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedOption.rail", equalTo("ACH")))
                .andExpect(jsonPath("$.warnings[0].code", equalTo("BATCH_HIGH_VALUE_ENTRY_REVIEW")))
                .andExpect(jsonPath("$.alternatives[0].rail", equalTo("RTGS")))
                .andExpect(jsonPath("$.alternatives[0].arrangement", equalTo("DOMESTIC_INTERBANK_GROSS_SETTLEMENT")));
    }

    @Test
    void recommendsRtgsAndFiCorrespondentForArrangementSpecificIntents() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-rtgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSegment": "CORPORATE",
                                  "paymentCount": 1,
                                  "amountSummary": {
                                    "currency": "USD",
                                    "totalAmount": "250000.00",
                                    "maxSingleAmount": "250000.00"
                                  },
                                  "debtorCountry": "US",
                                  "creditorCountry": "US",
                                  "urgency": "SAME_DAY",
                                  "requiresFinality": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedOption.rail", equalTo("RTGS")))
                .andExpect(jsonPath("$.recommendedOption.arrangement", equalTo("DOMESTIC_INTERBANK_GROSS_SETTLEMENT")))
                .andExpect(jsonPath("$.nextApiGuidance.endpoint", equalTo("/v1/rtgs-payments")));

        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("fi-client-rec", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-fi-corr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSegment": "FI",
                                  "paymentCount": 1,
                                  "amountSummary": {
                                    "currency": "USD",
                                    "totalAmount": "1000000.00",
                                    "maxSingleAmount": "1000000.00"
                                  },
                                  "debtorCountry": "US",
                                  "creditorCountry": "US",
                                  "urgency": "SAME_DAY",
                                  "fiToFi": true,
                                  "arrangementPreference": "CORRESPONDENT_ACCOUNT_PATH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedOption.rail", equalTo("FI_CORRESPONDENT")))
                .andExpect(jsonPath("$.recommendedOption.arrangement", equalTo("CORRESPONDENT_ACCOUNT_PATH")))
                .andExpect(jsonPath("$.nextApiGuidance.endpoint", equalTo("/v1/fi-payments")));
    }

    @Test
    void recommendsRtgsForFiDomesticGrossSettlementIntent() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("fi-client-rec", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-fi-rtgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSegment": "FI",
                                  "paymentCount": 1,
                                  "amountSummary": {
                                    "currency": "USD",
                                    "totalAmount": "750000.00",
                                    "maxSingleAmount": "750000.00"
                                  },
                                  "debtorCountry": "US",
                                  "creditorCountry": "US",
                                  "urgency": "SAME_DAY",
                                  "requiresFinality": true,
                                  "fiToFi": true,
                                  "arrangementPreference": "DOMESTIC_INTERBANK_GROSS_SETTLEMENT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedOption.rail", equalTo("RTGS")))
                .andExpect(jsonPath("$.recommendedOption.clientSegment", equalTo("FI")))
                .andExpect(jsonPath("$.recommendedOption.arrangement", equalTo("DOMESTIC_INTERBANK_GROSS_SETTLEMENT")))
                .andExpect(jsonPath("$.nextApiGuidance.endpoint", equalTo("/v1/rtgs-payments")));
    }

    @Test
    void returnsUnsupportedResultForValidCrossBorderIntent() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-cross-border")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSegment": "CORPORATE",
                                  "paymentCount": 1,
                                  "amountSummary": {
                                    "currency": "USD",
                                    "totalAmount": "1000.00"
                                  },
                                  "debtorCountry": "US",
                                  "creditorCountry": "GB",
                                  "urgency": "SAME_DAY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationStatus", equalTo("UNSUPPORTED")))
                .andExpect(jsonPath("$.recommendedOption").doesNotExist())
                .andExpect(jsonPath("$.nextApiGuidance").doesNotExist())
                .andExpect(jsonPath("$.warnings[0].code", equalTo("CROSS_BORDER_NOT_SUPPORTED")));
    }

    @Test
    void returnsUnsupportedResultForValidNonUsdIntent() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-non-usd")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSegment": "CORPORATE",
                                  "paymentCount": 1,
                                  "amountSummary": {
                                    "currency": "EUR",
                                    "totalAmount": "1000.00"
                                  },
                                  "debtorCountry": "US",
                                  "creditorCountry": "US",
                                  "urgency": "SAME_DAY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationStatus", equalTo("UNSUPPORTED")))
                .andExpect(jsonPath("$.recommendedOption").doesNotExist())
                .andExpect(jsonPath("$.nextApiGuidance").doesNotExist())
                .andExpect(jsonPath("$.warnings[0].code", equalTo("NON_USD_NOT_SUPPORTED")));
    }

    @Test
    void rejectsMissingRecommendationScopeAndInvalidRequestShape() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payments:create"))
                        .header("X-Correlation-ID", "corr-rec-forbidden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rtpJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-rec-forbidden")));

        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payment-rail-recommendations:create"))
                        .header("X-Correlation-ID", "corr-rec-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-rec-invalid")));
    }

    @Test
    void requiresAuthenticationButDoesNotRequireIdempotencyAndGeneratesCorrelationId() throws Exception {
        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rtpJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHORIZED")));

        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", bearer("client-rec-a", "payment-rail-recommendations:create"))
                        .header("Idempotency-Key", "idem-rec-ignored")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rtpJson()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.recommendedOption.rail", equalTo("RTP")));
    }

    private String rtpJson() {
        return """
                {
                  "clientSegment": "CORPORATE",
                  "paymentCount": 1,
                  "amountSummary": {
                    "currency": "USD",
                    "totalAmount": "250.00"
                  },
                  "debtorCountry": "US",
                  "creditorCountry": "US",
                  "urgency": "IMMEDIATE"
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
