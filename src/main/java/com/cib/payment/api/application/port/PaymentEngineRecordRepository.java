package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.PaymentId;
import java.util.Optional;

public interface PaymentEngineRecordRepository {
    EnginePaymentRecord save(EnginePaymentRecord record);

    Optional<EnginePaymentRecord> findByPaymentIdAndClientId(PaymentId paymentId, String clientId);
}
