package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OpenApiContractTest {
    @Test
    void openApiContractDefinesPaymentEndpointsAndSchemas() throws Exception {
        var resource = new ClassPathResource("openapi/domestic-payment-api.yaml");
        var yaml = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml).contains("openapi: 3.0.3");
        assertThat(yaml).contains("/v1/domestic-payments:");
        assertThat(yaml).contains("/v1/domestic-payments/{paymentId}:");
        assertThat(yaml).contains("application/pain.001+xml");
        assertThat(yaml).contains("application/pain.002+xml");
        assertThat(yaml).contains("pain.001.001.09");
        assertThat(yaml).contains("pain.002.001.10");
        assertThat(yaml).contains("ACSC", "RJCT", "PDNG");
        assertThat(yaml).contains("HKD-only");
        assertThat(yaml).doesNotContain("CreateDomesticPaymentRequest");
        assertThat(yaml).doesNotContain("\n    PaymentResponse:");
        assertThat(yaml).doesNotContain("\n    PaymentStatusResponse:");
        assertThat(yaml).contains("ErrorResponse");
        assertThat(yaml).contains("X-Correlation-ID");
        assertThat(yaml).contains("X-Mock-Scenario");
        assertThat(yaml).contains("suspicious_proxy_or_account", "pending");
        assertThat(yaml).contains("pacs.008 is internal only");
    }

    @Test
    void openApiContractDefinesFiCorrespondentPaymentAndInvestigationArtifacts() throws Exception {
        var resource = new ClassPathResource("openapi/domestic-payment-api.yaml");
        var yaml = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml).contains("/v1/fi-payments:");
        assertThat(yaml).contains("/v1/fi-payments/{paymentId}:");
        assertThat(yaml).contains("/v1/fi-payments/{paymentId}/recall-requests:");
        assertThat(yaml).contains("application/pacs.009+xml");
        assertThat(yaml).contains("application/camt.056+xml");
        assertThat(yaml).contains("application/camt.029+xml");
        assertThat(yaml).contains("FiPaymentAcknowledgementResponse");
        assertThat(yaml).contains("FiPaymentStatusResponse");
        assertThat(yaml).contains("CorrespondentSettlementContext");
        assertThat(yaml).contains("RecallInvestigationSummary");
        assertThat(yaml).contains("fi-payments:create", "fi-payments:read", "fi-payments:investigate");
        assertThat(yaml).contains("FI status query does not require Idempotency-Key");
        assertThat(yaml).contains("fi_payment_accepted");
        assertThat(yaml).contains("fi_payment_rejected_unsupported_correspondent");
        assertThat(yaml).contains("fi_payment_pending_correspondent_review");
        assertThat(yaml).contains("recall_accepted", "recall_rejected", "investigation_pending");
        assertThat(yaml).contains("USD-only");
        assertThat(yaml).contains("simulator-only");
        assertThat(yaml).contains("no real SWIFT, CBPR+, correspondent banking, ledger, AML, sanctions, fraud, or settlement connectivity");
        assertThat(yaml).contains("NOSTRO", "VOSTRO", "LORO");
        assertThat(yaml).contains("maskedSimulatedAccountReference");
    }
}
