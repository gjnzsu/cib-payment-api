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
@Import(CollectionSensitiveLoggingIntegrationTest.JwtTestConfiguration.class)
class CollectionSensitiveLoggingIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    CollectionSensitiveLoggingIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void collectionCreateFlowDoesNotLogRawAccountsAuthorizationReferenceOrJsonPayload(CapturedOutput output)
            throws Exception {
        var settlementAccount = "123456789012";
        var payerAccountOne = "987654321012";
        var payerAccountTwo = "987654321013";
        var mandateReference = "MANDATE-SECRET-0000001234";
        var rawJson = validJson(settlementAccount, payerAccountOne, payerAccountTwo, mandateReference);
        var token = JwtTestSupport.tokenWithScopes("collection-sensitive-client", "collections:create");

        mockMvc.perform(post("/v1/collections")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "collection-sensitive-logging")
                        .header("X-Correlation-ID", "corr-collection-sensitive")
                        .header("X-Mock-Scenario", "us_ach_debit_collected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isAccepted());

        assertThat(output).contains("collection_accepted");
        assertThat(output).contains("correlationId=corr-collection-sensitive");
        assertThat(output).contains("mandateReference=*********************1234");
        assertThat(output).contains("settlementAccount=********9012");
        assertThat(output).contains("payerAccounts=[********1012, ********1013]");

        assertThat(output).doesNotContain("Bearer ");
        assertThat(output).doesNotContain(token);
        assertThat(output).doesNotContain(rawJson);
        assertThat(output).doesNotContain(settlementAccount);
        assertThat(output).doesNotContain(payerAccountOne);
        assertThat(output).doesNotContain(payerAccountTwo);
        assertThat(output).doesNotContain(mandateReference);
    }

    private String validJson(
            String settlementAccount,
            String payerAccountOne,
            String payerAccountTwo,
            String mandateReference) {
        return """
                {
                  "collectionProfile": "US_ACH_DIRECT_DEBIT_BATCH",
                  "collectionReference": "COLL-SENSITIVE-0001",
                  "mandateReference": "%s",
                  "creditorName": "CIB Collection Services",
                  "debtorName": "Corporate Customer",
                  "settlementAccount": {
                    "bankCode": "021000021",
                    "accountNumber": "%s",
                    "accountName": "CIB Collections"
                  },
                  "entries": [
                    {
                      "entryReference": "COLL-SENSITIVE-ENTRY-0001",
                      "payerName": "Payer One",
                      "payerAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "%s",
                        "accountName": "Payer One"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "125.40"
                      },
                      "purpose": "INVOICE_COLLECTION"
                    },
                    {
                      "entryReference": "COLL-SENSITIVE-ENTRY-0002",
                      "payerName": "Payer Two",
                      "payerAccount": {
                        "bankCode": "111000025",
                        "accountNumber": "%s",
                        "accountName": "Payer Two"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "225.10"
                      },
                      "purpose": "INVOICE_COLLECTION"
                    }
                  ]
                }
                """.formatted(mandateReference, settlementAccount, payerAccountOne, payerAccountTwo);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
