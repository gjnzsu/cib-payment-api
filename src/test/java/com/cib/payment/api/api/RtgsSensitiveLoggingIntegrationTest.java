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
@Import(RtgsSensitiveLoggingIntegrationTest.JwtTestConfiguration.class)
class RtgsSensitiveLoggingIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    RtgsSensitiveLoggingIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void rtgsCreateFlowDoesNotLogRawAccountsBearerTokenOrJsonPayload(CapturedOutput output) throws Exception {
        var debtorAccount = "123456789012";
        var creditorAccount = "987654321012";
        var rawJson = corporateJson(debtorAccount, creditorAccount);
        var token = JwtTestSupport.tokenWithScopes("rtgs-sensitive-client", "rtgs-payments:create");

        mockMvc.perform(post("/v1/rtgs-payments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "rtgs-sensitive-logging")
                        .header("X-Correlation-ID", "corr-rtgs-sensitive")
                        .header("X-Mock-Scenario", "rtgs_settled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isAccepted());

        assertThat(output).contains("rtgs_payment_accepted");
        assertThat(output).contains("correlationId=corr-rtgs-sensitive");
        assertThat(output).contains("debtorAccount=********9012");
        assertThat(output).contains("creditorAccount=********1012");

        assertThat(output).doesNotContain("Bearer ");
        assertThat(output).doesNotContain(token);
        assertThat(output).doesNotContain(rawJson);
        assertThat(output).doesNotContain(debtorAccount);
        assertThat(output).doesNotContain(creditorAccount);
    }

    private String corporateJson(String debtorAccount, String creditorAccount) {
        return """
                {
                  "paymentReference": "RTGS-SENSITIVE-0001",
                  "clientSegment": "CORPORATE",
                  "debtorAccount": {
                    "bankCode": "CITIUS33",
                    "accountNumber": "%s",
                    "accountName": "Acme Operating"
                  },
                  "creditorAccount": {
                    "bankCode": "BOFAUS3N",
                    "accountNumber": "%s",
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
                """.formatted(debtorAccount, creditorAccount);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
