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
@Import(AchSensitiveLoggingIntegrationTest.JwtTestConfiguration.class)
class AchSensitiveLoggingIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    AchSensitiveLoggingIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void achCreateFlowDoesNotLogRawAccountsBearerTokenOrJsonPayload(CapturedOutput output) throws Exception {
        var settlementAccount = "123456789012";
        var receiverAccountOne = "987654321012";
        var receiverAccountTwo = "987654321013";
        var rawJson = validJson(settlementAccount, receiverAccountOne, receiverAccountTwo);
        var token = JwtTestSupport.tokenWithScopes("ach-sensitive-client", "ach-batches:create");

        mockMvc.perform(post("/v1/ach-batches")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "ach-sensitive-logging")
                        .header("X-Correlation-ID", "corr-ach-sensitive")
                        .header("X-Mock-Scenario", "ach_direct_credit_settled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isAccepted());

        assertThat(output).contains("ach_batch_accepted");
        assertThat(output).contains("correlationId=corr-ach-sensitive");
        assertThat(output).contains("settlementAccount=********9012");
        assertThat(output).contains("receiverAccounts=[********1012, ********1013]");

        assertThat(output).doesNotContain("Bearer ");
        assertThat(output).doesNotContain(token);
        assertThat(output).doesNotContain(rawJson);
        assertThat(output).doesNotContain(settlementAccount);
        assertThat(output).doesNotContain(receiverAccountOne);
        assertThat(output).doesNotContain(receiverAccountTwo);
    }

    private String validJson(String settlementAccount, String receiverAccountOne, String receiverAccountTwo) {
        return """
                {
                  "batchReference": "ACH-BATCH-SENSITIVE-0001",
                  "originatorName": "CIB Payroll Services",
                  "effectiveEntryDate": "2026-06-02",
                  "settlementAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "%s",
                    "accountName": "CIB Operating"
                  },
                  "entries": [
                    {
                      "entryReference": "ACH-SENSITIVE-ENTRY-0001",
                      "receiverName": "Receiver One",
                      "receiverAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "%s",
                        "accountName": "Receiver One"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "125.40"
                      },
                      "purpose": "PAYROLL"
                    },
                    {
                      "entryReference": "ACH-SENSITIVE-ENTRY-0002",
                      "receiverName": "Receiver Two",
                      "receiverAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "%s",
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
                """.formatted(settlementAccount, receiverAccountOne, receiverAccountTwo);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
