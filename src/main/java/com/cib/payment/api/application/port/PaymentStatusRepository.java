package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.util.Optional;

public interface PaymentStatusRepository {
    PaymentRecord save(PaymentRecord record);

    Optional<PaymentRecord> findByPaymentIdAndClientId(PaymentId paymentId, String clientId);

    PaymentRecord updateStatus(PaymentId paymentId, PaymentStatus status, PaymentReason reason);
}
