package com.cib.payment.api.infrastructure.observability;

import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.AchBatchRecord;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.InternalInterbankTransfer;
import com.cib.payment.api.domain.model.IsoPaymentCandidate;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
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

    @Override
    public void isoPaymentInitiationAdmitted(
            IsoPaymentCandidate candidate,
            AuthorizationContext authorizationContext) {
        meterRegistry.counter("payment.iso.initiation.admitted").increment();
        log.info(
                "iso_payment_initiation_admitted correlationId={} clientId={} debtorAccount={} creditorAccount={} proxy={} currency={}",
                authorizationContext.correlationId().value(),
                authorizationContext.clientId(),
                AccountNumberMasker.maskSensitive(candidate.debtor().accountNumber()),
                candidate.beneficiary().accountNumber().map(AccountNumberMasker::maskSensitive).orElse(""),
                candidate.beneficiary().fpsProxyValue().map(AccountNumberMasker::maskSensitive).orElse(""),
                candidate.amount().currency());
    }

    @Override
    public void enginePaymentMapped(
            InternalInterbankTransfer transfer,
            AuthorizationContext authorizationContext) {
        meterRegistry.counter("payment.engine.mapped").increment();
        log.info(
                "engine_payment_mapped correlationId={} clientId={} paymentId={} debtorAccount={} creditorAccount={} proxy={} currency={}",
                transfer.correlationId().value(),
                authorizationContext.clientId(),
                transfer.paymentId().value(),
                AccountNumberMasker.maskSensitive(transfer.debtor().accountNumber()),
                transfer.beneficiary().accountNumber().map(AccountNumberMasker::maskSensitive).orElse(""),
                transfer.beneficiary().fpsProxyValue().map(AccountNumberMasker::maskSensitive).orElse(""),
                transfer.amount().currency());
    }

    @Override
    public void hkSimulatorOutcome(
            String scenario,
            PaymentStatus status,
            Optional<PaymentReason> reason,
            CorrelationId correlationId) {
        meterRegistry.counter("payment.hk.simulator.outcomes", "scenario", scenario, "status", status.name()).increment();
        log.info(
                "hk_simulator_outcome correlationId={} scenario={} status={} reasonCode={}",
                correlationId.value(),
                scenario,
                status.name(),
                reason.map(PaymentReason::code).orElse(""));
    }

    @Override
    public void pain002Generated(String paymentId, PaymentStatus status, CorrelationId correlationId) {
        meterRegistry.counter("payment.pain002.generated", "status", status.name()).increment();
        log.info(
                "pain002_generated correlationId={} paymentId={} status={}",
                correlationId.value(),
                paymentId,
                status.name());
    }

    @Override
    public void fiXmlPayloadHandled(String messageType, String rawXml, CorrelationId correlationId) {
        meterRegistry.counter("payment.fi.xml.payloads", "messageType", messageType).increment();
        log.info(
                "fi_xml_payload_handled correlationId={} messageType={} xmlPayload={}",
                correlationId.value(),
                messageType,
                AccountNumberMasker.maskSensitive(rawXml));
    }

    @Override
    public void fiPaymentAccepted(FiPaymentRecord record, AuthorizationContext authorizationContext) {
        meterRegistry.counter("payment.fi.accepted", "status", record.status().name()).increment();
        log.info(
                "fi_payment_accepted correlationId={} clientId={} paymentId={} status={} instructingAgent={} instructedAgent={} currency={} accountRole={} simulatedAccount={}",
                record.correlationId().value(),
                authorizationContext.clientId(),
                record.paymentId().value(),
                record.status().name(),
                record.instructingParty().bic(),
                record.instructedParty().bic(),
                record.settlementCurrency(),
                record.correspondentSettlementContext().accountRelationshipRole().name(),
                AccountNumberMasker.maskSensitive(record.correspondentSettlementContext().maskedSimulatedAccountReference()));
    }

    @Override
    public void fiPaymentStatusLookup(FiPaymentRecord record, AuthorizationContext authorizationContext) {
        meterRegistry.counter("payment.fi.status.distribution", "status", record.status().name()).increment();
        log.info(
                "fi_payment_status_lookup correlationId={} clientId={} paymentId={} status={} simulatedAccount={}",
                authorizationContext.correlationId().value(),
                authorizationContext.clientId(),
                record.paymentId().value(),
                record.status().name(),
                AccountNumberMasker.maskSensitive(record.correspondentSettlementContext().maskedSimulatedAccountReference()));
    }

    @Override
    public void recallInvestigationCreated(
            RecallInvestigationRecord record,
            AuthorizationContext authorizationContext) {
        meterRegistry.counter("payment.fi.recall.created", "status", record.status().name()).increment();
        log.info(
                "fi_recall_investigation_created correlationId={} clientId={} paymentId={} investigationId={} status={} originalReference={} simulatedAccount={}",
                record.correlationId().value(),
                authorizationContext.clientId(),
                record.fiPaymentId().value(),
                record.investigationId().value(),
                record.status().name(),
                AccountNumberMasker.maskSensitive(record.originalPaymentReference()),
                AccountNumberMasker.maskSensitive(record.settlementContext().maskedSimulatedAccountReference()));
    }

    @Override
    public void achBatchAccepted(AchBatchRecord record, AuthorizationContext authorizationContext) {
        meterRegistry.counter("payment.ach.accepted", "status", record.status().name()).increment();
        log.info(
                "ach_batch_accepted correlationId={} clientId={} batchId={} status={} settlementAccount={} receiverAccounts={} currency={}",
                record.correlationId().value(),
                authorizationContext.clientId(),
                record.batchId().value(),
                record.status().name(),
                AccountNumberMasker.maskSensitive(record.settlementAccount().accountNumber()),
                record.entries().stream()
                        .map(entry -> AccountNumberMasker.maskSensitive(entry.receiverAccount().accountNumber()))
                        .toList(),
                record.totalAmount().currency());
    }

    @Override
    public void rtgsPaymentAccepted(RtgsPaymentRecord record, AuthorizationContext authorizationContext) {
        meterRegistry.counter("payment.rtgs.accepted", "status", record.status().name()).increment();
        log.info(
                "rtgs_payment_accepted correlationId={} clientId={} paymentId={} clientSegment={} status={} settlementFinality={} debtorAccount={} creditorAccount={} currency={}",
                record.correlationId().value(),
                authorizationContext.clientId(),
                record.paymentId().value(),
                record.clientSegment().name(),
                record.status().name(),
                record.settlementFinality(),
                record.debtorAccount()
                        .map(account -> AccountNumberMasker.maskSensitive(account.accountNumber()))
                        .orElse(""),
                record.creditorAccount()
                        .map(account -> AccountNumberMasker.maskSensitive(account.accountNumber()))
                        .orElse(""),
                record.settlementAmount().currency());
    }
}
