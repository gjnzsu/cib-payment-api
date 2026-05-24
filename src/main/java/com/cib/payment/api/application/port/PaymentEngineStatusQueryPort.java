package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.PaymentId;
import java.util.Optional;

public interface PaymentEngineStatusQueryPort {
    Optional<EnginePaymentRecord> findByPaymentId(PaymentId paymentId, AuthorizationContext authorizationContext);
}
