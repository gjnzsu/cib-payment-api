package com.cib.payment.api.infrastructure.observability;

import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MicrometerPaymentObservability implements PaymentObservability {
    private static final Logger log = LoggerFactory.getLogger(MicrometerPaymentObservability.class);

    private final MeterRegistry meterRegistry;

    public MicrometerPaymentObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void apiRequest(String operation, String result, Duration duration) {
        meterRegistry.counter("payment.api.requests", "operation", operation, "result", result).increment();
        meterRegistry.timer("payment.api.latency", "operation", operation, "result", result).record(duration);
    }

    @Override
    public void paymentCreationAccepted(
            CreateDomesticPaymentRequest request,
            AuthorizationContext authorizationContext,
            PaymentRecord record) {
        meterRegistry.counter("payment.accepted").increment();
        meterRegistry.counter("payment.status.distribution", "status", record.status().name()).increment();
        log.info(
                "payment_creation_accepted correlationId={} clientId={} paymentId={} status={} debtorAccount={} creditorAccount={} currency={}",
                record.correlationId().value(),
                authorizationContext.clientId(),
                record.paymentId().value(),
                record.status().name(),
                AccountNumberMasker.mask(request.debtorAccount().accountNumber()),
                AccountNumberMasker.mask(request.creditorAccount().accountNumber()),
                request.amount().currency());
    }

    @Override
    public void idempotencyReplay(IdempotencyRecord record) {
        meterRegistry.counter("payment.idempotency.replays").increment();
        log.info(
                "idempotency_replay correlationId={} clientId={} paymentId={}",
                record.correlationId().value(),
                record.clientId(),
                record.paymentId().value());
    }

    @Override
    public void idempotencyConflict(IdempotencyRecord record, CorrelationId correlationId) {
        meterRegistry.counter("payment.idempotency.conflicts").increment();
        log.info(
                "idempotency_conflict correlationId={} originalCorrelationId={} clientId={} paymentId={}",
                correlationId.value(),
                record.correlationId().value(),
                record.clientId(),
                record.paymentId().value());
    }

    @Override
    public void statusLookup(PaymentRecord record, AuthorizationContext authorizationContext) {
        meterRegistry.counter("payment.status.distribution", "status", record.status().name()).increment();
        log.info(
                "payment_status_lookup correlationId={} clientId={} paymentId={} status={}",
                authorizationContext.correlationId().value(),
                authorizationContext.clientId(),
                record.paymentId().value(),
                record.status().name());
    }

    @Override
    public void validationFailure(String errorCode, CorrelationId correlationId) {
        meterRegistry.counter("payment.validation.failures", "code", errorCode).increment();
        log.info("validation_failure correlationId={} code={}", correlationId.value(), errorCode);
    }

    @Override
    public void authFailure(String result, CorrelationId correlationId) {
        meterRegistry.counter("payment.auth.failures", "result", result).increment();
        log.info("auth_failure correlationId={} result={}", correlationId.value(), result);
    }

    @Override
    public void downstreamOutcome(String scenario, PaymentStatus status, CorrelationId correlationId) {
        meterRegistry.counter("payment.downstream.outcomes", "scenario", scenario, "status", status.name()).increment();
        meterRegistry.counter("payment.status.distribution", "status", status.name()).increment();
        log.info(
                "downstream_mock_outcome correlationId={} scenario={} status={}",
                correlationId.value(),
                scenario,
                status.name());
    }
}
