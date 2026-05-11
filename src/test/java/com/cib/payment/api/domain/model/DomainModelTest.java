package com.cib.payment.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DomainModelTest {
    @Test
    void paymentStatusContainsMvpLifecycleStates() {
        assertThat(Arrays.stream(PaymentStatus.values()).map(Enum::name))
                .containsExactly("ACCEPTED", "PROCESSING", "COMPLETED", "REJECTED", "FAILED", "TIMEOUT");
    }
}
