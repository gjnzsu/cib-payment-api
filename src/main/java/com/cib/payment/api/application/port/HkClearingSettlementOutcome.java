package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.PaymentReason;
import java.util.Objects;
import java.util.Optional;

public record HkClearingSettlementOutcome(Status status, Optional<PaymentReason> reason) {
    public HkClearingSettlementOutcome {
        Objects.requireNonNull(status, "status must not be null");
        reason = reason == null ? Optional.empty() : reason;
    }

    public HkClearingSettlementOutcome(Status status, PaymentReason reason) {
        this(status, Optional.ofNullable(reason));
    }

    public enum Status {
        SETTLED,
        REJECTED,
        PENDING,
        TIMEOUT,
        INTERNAL_FAILURE
    }
}
