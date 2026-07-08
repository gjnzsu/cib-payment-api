package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.MandateStatus;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.Optional;

public record MandateSimulatorOutcome(MandateStatus status, Optional<PaymentReason> reason) {
    public MandateSimulatorOutcome(MandateStatus status, PaymentReason reason) {
        this(status, Optional.ofNullable(reason));
    }
}
