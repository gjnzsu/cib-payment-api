package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO",
        "logging.level.org.springframework.web=INFO"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(PaymentRailRecommendationSensitiveLoggingIntegrationTest.JwtTestConfiguration.class)
class PaymentRailRecommendationSensitiveLoggingIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    PaymentRailRecommendationSensitiveLoggingIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void recommendationFlowLogsCorrelationWithoutRawPayloadBearerTokenOrUnexpectedSensitiveValues(
            CapturedOutput output) throws Exception {
        var token = JwtTestSupport.tokenWithScopes("rec-sensitive-client", "payment-rail-recommendations:create");
        var rawJson = rtpJson();

        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Correlation-ID", "corr-rec-sensitive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk());

        assertThat(output).contains("payment_rail_recommendation_generated");
        assertThat(output).contains("correlationId=corr-rec-sensitive");
        assertThat(output).contains("recommendationStatus=RECOMMENDED");
        assertThat(output).contains("rail=RTP");

        assertThat(output).doesNotContain("Bearer ");
        assertThat(output).doesNotContain(token);
        assertThat(output).doesNotContain(rawJson);
    }

    @Test
    void invalidRecommendationRequestDoesNotLogUnexpectedSensitiveFieldValues(CapturedOutput output)
            throws Exception {
        var sensitiveValue = "1234567890123456";
        var rawJson = """
                {
                  "clientSegment": "CORPORATE",
                  "paymentCount": 1,
                  "amountSummary": {
                    "currency": "USD",
                    "totalAmount": "250.00"
                  },
                  "debtorCountry": "US",
                  "creditorCountry": "US",
                  "urgency": "IMMEDIATE",
                  "debtorAccountNumber": "%s"
                }
                """.formatted(sensitiveValue);
        var token = JwtTestSupport.tokenWithScopes("rec-sensitive-client", "payment-rail-recommendations:create");

        mockMvc.perform(post("/v1/payment-rail-recommendations")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Correlation-ID", "corr-rec-sensitive-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest());

        assertThat(output).contains("validation_failure");
        assertThat(output).contains("correlationId=corr-rec-sensitive-invalid");
        assertThat(output).doesNotContain("Bearer ");
        assertThat(output).doesNotContain(token);
        assertThat(output).doesNotContain(rawJson);
        assertThat(output).doesNotContain(sensitiveValue);
    }

    private String rtpJson() {
        return """
                {
                  "clientSegment": "CORPORATE",
                  "paymentCount": 1,
                  "amountSummary": {
                    "currency": "USD",
                    "totalAmount": "250.00",
                    "maxSingleAmount": "250.00"
                  },
                  "debtorCountry": "US",
                  "creditorCountry": "US",
                  "urgency": "IMMEDIATE",
                  "requiresFinality": false
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
