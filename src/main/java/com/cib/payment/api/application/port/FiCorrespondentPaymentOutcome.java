package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.FiPaymentStatus;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.Objects;
import java.util.Optional;

public record FiCorrespondentPaymentOutcome(FiPaymentStatus status, Optional<PaymentReason> reason) {
    public FiCorrespondentPaymentOutcome {
        Objects.requireNonNull(status, "status must not be null");
        reason = reason == null ? Optional.empty() : reason;
    }

    public FiCorrespondentPaymentOutcome(FiPaymentStatus status, PaymentReason reason) {
        this(status, Optional.ofNullable(reason));
    }
}
