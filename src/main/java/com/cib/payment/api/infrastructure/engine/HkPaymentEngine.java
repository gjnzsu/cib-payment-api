package com.cib.payment.api.infrastructure.engine;

import com.cib.payment.api.application.port.HkClearingSettlementOutcome;
import com.cib.payment.api.application.port.HkClearingSettlementSimulator;
import com.cib.payment.api.application.port.PaymentEngineInitiationPort;
import com.cib.payment.api.application.port.PaymentEngineRecordRepository;
import com.cib.payment.api.application.port.PaymentEngineStatusQueryPort;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.InternalInterbankTransfer;
import com.cib.payment.api.domain.model.IsoPaymentCandidate;
import com.cib.payment.api.domain.model.IsoPaymentStatusReport;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.infrastructure.iso.Pain002Renderer;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HkPaymentEngine implements PaymentEngineInitiationPort, PaymentEngineStatusQueryPort {
    private final PaymentEngineRecordRepository recordRepository;
    private final HkClearingSettlementSimulator clearingSettlementSimulator;
    private final Pain002Renderer pain002Renderer;
    private final PaymentObservability observability;
    private final Clock clock;
    private final Supplier<UUID> paymentIdSupplier;

    @Autowired
    public HkPaymentEngine(
            PaymentEngineRecordRepository recordRepository,
            HkClearingSettlementSimulator clearingSettlementSimulator,
            PaymentObservability observability) {
        this(recordRepository, clearingSettlementSimulator, new Pain002Renderer(), observability, Clock.systemUTC(), UUID::randomUUID);
    }

    HkPaymentEngine(
            PaymentEngineRecordRepository recordRepository,
            HkClearingSettlementSimulator clearingSettlementSimulator,
            Clock clock,
            Supplier<UUID> paymentIdSupplier) {
        this(recordRepository, clearingSettlementSimulator, new Pain002Renderer(), PaymentObservability.noop(), clock, paymentIdSupplier);
    }

    HkPaymentEngine(
            PaymentEngineRecordRepository recordRepository,
            HkClearingSettlementSimulator clearingSettlementSimulator,
            Pain002Renderer pain002Renderer,
            PaymentObservability observability,
            Clock clock,
            Supplier<UUID> paymentIdSupplier) {
        this.recordRepository = Objects.requireNonNull(recordRepository, "recordRepository must not be null");
        this.clearingSettlementSimulator = Objects.requireNonNull(
                clearingSettlementSimulator, "clearingSettlementSimulator must not be null");
        this.pain002Renderer = Objects.requireNonNull(pain002Renderer, "pain002Renderer must not be null");
        this.observability = Objects.requireNonNull(observability, "observability must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.paymentIdSupplier = Objects.requireNonNull(paymentIdSupplier, "paymentIdSupplier must not be null");
    }

    HkPaymentEngine(
            PaymentEngineRecordRepository recordRepository,
            HkClearingSettlementSimulator clearingSettlementSimulator,
            Pain002Renderer pain002Renderer,
            Clock clock,
            Supplier<UUID> paymentIdSupplier) {
        this(recordRepository, clearingSettlementSimulator, pain002Renderer, PaymentObservability.noop(), clock, paymentIdSupplier);
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
        var paymentId = new PaymentId(paymentIdSupplier.get());
        var transfer = internalTransfer(paymentId, candidate, correlationId);
        observability.enginePaymentMapped(transfer, authorizationContext);
        var outcome = clearingSettlementSimulator.process(transfer, authorizationContext, scenarioContext);
        var status = toPaymentStatus(outcome.status());
        observability.hkSimulatorOutcome(normalizeScenario(scenarioContext), status, outcome.reason(), correlationId);
        var statusReportXml = pain002Renderer.render(new IsoPaymentStatusReport(
                paymentId,
                candidate,
                status,
                now,
                correlationId,
                outcome.reason()));
        observability.pain002Generated(paymentId.value().toString(), status, correlationId);
        var record = new EnginePaymentRecord(
                paymentId,
                authorizationContext.clientId(),
                candidate,
                status,
                now,
                now,
                correlationId,
                Optional.of(transfer),
                Optional.of(statusReportXml),
                outcome.reason(),
                idempotencyReference);
        return recordRepository.save(record);
    }

    @Override
    public Optional<EnginePaymentRecord> findByPaymentId(PaymentId paymentId, AuthorizationContext authorizationContext) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");

        return recordRepository.findByPaymentIdAndClientId(paymentId, authorizationContext.clientId());
    }

    private InternalInterbankTransfer internalTransfer(
            PaymentId paymentId,
            IsoPaymentCandidate candidate,
            CorrelationId correlationId) {
        return new InternalInterbankTransfer(
                "pacs008-" + paymentId.value(),
                paymentId,
                candidate.debtor(),
                candidate.beneficiary(),
                candidate.amount(),
                candidate.endToEndId(),
                candidate.instructionId(),
                candidate.paymentReference(),
                candidate.debtor().bankCode(),
                candidate.beneficiary().participantIdentifier(),
                correlationId);
    }

    private PaymentStatus toPaymentStatus(HkClearingSettlementOutcome.Status status) {
        return switch (status) {
            case SETTLED -> PaymentStatus.COMPLETED;
            case REJECTED -> PaymentStatus.REJECTED;
            case PENDING -> PaymentStatus.PROCESSING;
            case TIMEOUT -> PaymentStatus.TIMEOUT;
            case INTERNAL_FAILURE -> PaymentStatus.FAILED;
        };
    }

    private String normalizeScenario(String scenarioContext) {
        return scenarioContext == null || scenarioContext.isBlank() ? "success" : scenarioContext;
    }
}
