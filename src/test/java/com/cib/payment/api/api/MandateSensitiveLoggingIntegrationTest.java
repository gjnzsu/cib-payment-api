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
@Import(MandateSensitiveLoggingIntegrationTest.JwtTestConfiguration.class)
class MandateSensitiveLoggingIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    MandateSensitiveLoggingIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void mandateCreateFlowDoesNotLogRawAccountsAuthorizationReferenceOrJsonPayload(CapturedOutput output)
            throws Exception {
        var creditorAccount = "123456789012";
        var debtorAccount = "987654321012";
        var mandateReference = "MANDATE-SECRET-0000005678";
        var rawJson = validJson(creditorAccount, debtorAccount, mandateReference);
        var token = JwtTestSupport.tokenWithScopes("mandate-sensitive-client", "mandates:create");

        mockMvc.perform(post("/v1/mandates")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "mandate-sensitive-logging")
                        .header("X-Correlation-ID", "corr-mandate-sensitive")
                        .header("X-Mock-Scenario", "mandate_active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isAccepted());

        assertThat(output).doesNotContain("Bearer ");
        assertThat(output).doesNotContain(token);
        assertThat(output).doesNotContain(rawJson);
        assertThat(output).doesNotContain(creditorAccount);
        assertThat(output).doesNotContain(debtorAccount);
        assertThat(output).doesNotContain(mandateReference);
    }

    private String validJson(String creditorAccount, String debtorAccount, String mandateReference) {
        return """
                {
                  "mandateProfile": "US_ACH_DEBIT_MANDATE",
                  "mandateReference": "%s",
                  "creditorName": "CIB Collection Services",
                  "debtorName": "Corporate Customer",
                  "creditorAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "%s",
                    "accountName": "CIB Collections"
                  },
                  "debtorAccount": {
                    "bankCode": "111000025",
                    "accountNumber": "%s",
                    "accountName": "Corporate Customer"
                  },
                  "maximumAmount": {
                    "currency": "USD",
                    "value": "1000.00"
                  },
                  "frequency": "VARIABLE",
                  "purpose": "MONTHLY_COLLECTION"
                }
                """.formatted(mandateReference, creditorAccount, debtorAccount);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
