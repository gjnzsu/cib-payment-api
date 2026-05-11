package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.PaymentInstruction;

public interface DownstreamPaymentProcessor {
    DownstreamPaymentOutcome process(
            PaymentInstruction instruction,
            AuthorizationContext authorizationContext,
            CorrelationId correlationId,
            String mockScenario);
}
