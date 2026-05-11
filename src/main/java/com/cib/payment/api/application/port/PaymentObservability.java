package com.cib.payment.api.application.port;

import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.time.Duration;

public interface PaymentObservability {
    void apiRequest(String operation, String result, Duration duration);

    void paymentCreationAccepted(CreateDomesticPaymentRequest request, AuthorizationContext authorizationContext, PaymentRecord record);

    void idempotencyReplay(IdempotencyRecord record);

    void idempotencyConflict(IdempotencyRecord record, CorrelationId correlationId);

    void statusLookup(PaymentRecord record, AuthorizationContext authorizationContext);

    void validationFailure(String errorCode, CorrelationId correlationId);

    void authFailure(String result, CorrelationId correlationId);

    void downstreamOutcome(String scenario, PaymentStatus status, CorrelationId correlationId);

    static PaymentObservability noop() {
        return new PaymentObservability() {
            @Override
            public void apiRequest(String operation, String result, Duration duration) {}

            @Override
            public void paymentCreationAccepted(
                    CreateDomesticPaymentRequest request,
                    AuthorizationContext authorizationContext,
                    PaymentRecord record) {}

            @Override
            public void idempotencyReplay(IdempotencyRecord record) {}

            @Override
            public void idempotencyConflict(IdempotencyRecord record, CorrelationId correlationId) {}

            @Override
            public void statusLookup(PaymentRecord record, AuthorizationContext authorizationContext) {}

            @Override
            public void validationFailure(String errorCode, CorrelationId correlationId) {}

            @Override
            public void authFailure(String result, CorrelationId correlationId) {}

            @Override
            public void downstreamOutcome(String scenario, PaymentStatus status, CorrelationId correlationId) {}
        };
    }
}
