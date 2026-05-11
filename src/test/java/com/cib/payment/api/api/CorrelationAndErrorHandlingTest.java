package com.cib.payment.api.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.cib.payment.api.application.port.PaymentObservability;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CorrelationAndErrorHandlingTest.TestController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
@Import({
        CorrelationAndErrorHandlingTest.TestController.class,
        CorrelationAndErrorHandlingTest.TestConfiguration.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class
})
class CorrelationAndErrorHandlingTest {
    private final MockMvc mockMvc;

    @Autowired
    CorrelationAndErrorHandlingTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void inboundCorrelationIdIsReturned() throws Exception {
        mockMvc.perform(get("/test/ok").header("X-Correlation-ID", "corr-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-123"));
    }

    @Test
    void missingCorrelationIdGeneratesResponseHeader() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", notNullValue()));
    }

    @Test
    void validationErrorBodyContainsCorrelationIdAndFieldDetails() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .header("X-Correlation-ID", "corr-456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "creditorAccount": {
                                    "bankCode": "PAYBMYKL",
                                    "accountNumber": "9876543210",
                                    "accountName": "Supplier Sdn Bhd"
                                  },
                                  "amount": {
                                    "currency": "MYR",
                                    "value": "0.00"
                                  },
                                  "paymentReference": "INV-2026-0001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-456")))
                .andExpect(jsonPath("$.details[0].field", notNullValue()));
    }

    @RestController
    public static class TestController {
        @GetMapping("/test/ok")
        void ok() {}

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody CreateDomesticPaymentRequest request) {}
    }

    static class TestConfiguration {
        @Bean
        PaymentObservability paymentObservability() {
            return PaymentObservability.noop();
        }
    }
}
