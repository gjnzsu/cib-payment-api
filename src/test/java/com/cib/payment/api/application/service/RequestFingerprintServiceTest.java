package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestFingerprintServiceTest {
    private final RequestFingerprintService service = new RequestFingerprintService();

    @Test
    void sameClientAndSameBodyCreateSameFingerprint() {
        var first = service.fingerprint("client-a", validRequest("1250.50"), Map.of("mockScenario", "success"));
        var second = service.fingerprint("client-a", validRequest("1250.50"), Map.of("mockScenario", "success"));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void sameClientAndDifferentAmountCreateDifferentFingerprint() {
        var first = service.fingerprint("client-a", validRequest("1250.50"), Map.of("mockScenario", "success"));
        var second = service.fingerprint("client-a", validRequest("1251.50"), Map.of("mockScenario", "success"));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void differentClientAndSameBodyCreateDifferentFingerprint() {
        var first = service.fingerprint("client-a", validRequest("1250.50"), Map.of("mockScenario", "success"));
        var second = service.fingerprint("client-b", validRequest("1250.50"), Map.of("mockScenario", "success"));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void behaviorallyRelevantContextChangesFingerprint() {
        var first = service.fingerprint("client-a", validRequest("1250.50"), Map.of("mockScenario", "success"));
        var second = service.fingerprint("client-a", validRequest("1250.50"), Map.of("mockScenario", "rejection"));

        assertThat(second).isNotEqualTo(first);
    }

    private CreateDomesticPaymentRequest validRequest(String amount) {
        return new CreateDomesticPaymentRequest(
                new AccountReferenceRequest("CIBBMYKL", "1234567890", "Acme Treasury"),
                new AccountReferenceRequest("PAYBMYKL", "9876543210", "Supplier Sdn Bhd"),
                new MoneyRequest("MYR", amount),
                "INV-2026-0001",
                "Invoice payment",
                null);
    }
}
