package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.util.Optional;

public record DownstreamPaymentOutcome(PaymentStatus status, Optional<PaymentReason> reason) {
    public DownstreamPaymentOutcome(PaymentStatus status, PaymentReason reason) {
        this(status, Optional.ofNullable(reason));
    }
}
