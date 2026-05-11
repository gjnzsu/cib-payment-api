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
        assertThat(yaml).contains("CreateDomesticPaymentRequest");
        assertThat(yaml).contains("PaymentResponse");
        assertThat(yaml).contains("PaymentStatusResponse");
        assertThat(yaml).contains("ErrorResponse");
        assertThat(yaml).contains("X-Correlation-ID");
        assertThat(yaml).contains("X-Mock-Scenario");
    }
}
