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
        assertThat(yaml).doesNotContain("PaymentResponse");
        assertThat(yaml).doesNotContain("PaymentStatusResponse");
        assertThat(yaml).contains("ErrorResponse");
        assertThat(yaml).contains("X-Correlation-ID");
        assertThat(yaml).contains("X-Mock-Scenario");
        assertThat(yaml).contains("suspicious_proxy_or_account", "pending");
        assertThat(yaml).contains("pacs.008 is internal only");
    }
}
