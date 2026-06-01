package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.RtgsPaymentStatus;
import java.util.Objects;
import java.util.Optional;

public record RtgsPaymentOutcome(
        RtgsPaymentStatus status,
        boolean settlementFinality,
        Optional<PaymentReason> reason) {
    public RtgsPaymentOutcome {
        Objects.requireNonNull(status, "status must not be null");
        reason = reason == null ? Optional.empty() : reason;
        if (settlementFinality && status != RtgsPaymentStatus.SETTLED) {
            throw new IllegalArgumentException("settlementFinality can be true only when status is SETTLED");
        }
    }

    public RtgsPaymentOutcome(
            RtgsPaymentStatus status,
            boolean settlementFinality,
            PaymentReason reason) {
        this(status, settlementFinality, Optional.ofNullable(reason));
    }
}
