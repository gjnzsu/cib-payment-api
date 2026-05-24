package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.IsoPaymentCandidate;

public interface PaymentEngineInitiationPort {
    EnginePaymentRecord initiate(
            IsoPaymentCandidate candidate,
            AuthorizationContext authorizationContext,
            CorrelationId correlationId,
            String idempotencyReference,
            String scenarioContext);
}
