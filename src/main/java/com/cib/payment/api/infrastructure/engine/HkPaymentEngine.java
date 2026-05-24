package com.cib.payment.api.infrastructure.engine;

import com.cib.payment.api.application.port.PaymentEngineInitiationPort;
import com.cib.payment.api.application.port.PaymentEngineRecordRepository;
import com.cib.payment.api.application.port.PaymentEngineStatusQueryPort;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.IsoPaymentCandidate;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.UUID;

public class HkPaymentEngine implements PaymentEngineInitiationPort, PaymentEngineStatusQueryPort {
    private final PaymentEngineRecordRepository recordRepository;
    private final Clock clock;
    private final Supplier<UUID> paymentIdSupplier;

    public HkPaymentEngine(PaymentEngineRecordRepository recordRepository) {
        this(recordRepository, Clock.systemUTC(), UUID::randomUUID);
    }

    HkPaymentEngine(PaymentEngineRecordRepository recordRepository, Clock clock, Supplier<UUID> paymentIdSupplier) {
        this.recordRepository = Objects.requireNonNull(recordRepository, "recordRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.paymentIdSupplier = Objects.requireNonNull(paymentIdSupplier, "paymentIdSupplier must not be null");
    }

    @Override
    public EnginePaymentRecord initiate(
            IsoPaymentCandidate candidate,
            AuthorizationContext authorizationContext,
            CorrelationId correlationId,
            String idempotencyReference,
            String scenarioContext) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");

        var now = clock.instant();
        var record = new EnginePaymentRecord(
                new PaymentId(paymentIdSupplier.get()),
                authorizationContext.clientId(),
                candidate,
                PaymentStatus.PROCESSING,
                now,
                now,
                correlationId,
                Optional.empty(),
                Optional.empty(),
                idempotencyReference,
                scenarioContext);
        return recordRepository.save(record);
    }

    @Override
    public Optional<EnginePaymentRecord> findByPaymentId(PaymentId paymentId, AuthorizationContext authorizationContext) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");

        return recordRepository.findByPaymentIdAndClientId(paymentId, authorizationContext.clientId());
    }
}
