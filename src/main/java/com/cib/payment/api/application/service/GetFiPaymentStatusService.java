package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.CorrespondentSettlementContextResponse;
import com.cib.payment.api.api.dto.FiPaymentStatusResponse;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.api.dto.RecallInvestigationSummaryResponse;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.FiPaymentRepository;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.port.RecallInvestigationRepository;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GetFiPaymentStatusService {
    private final FiPaymentRepository fiPaymentRepository;
    private final RecallInvestigationRepository recallInvestigationRepository;
    private final PaymentObservability observability;

    GetFiPaymentStatusService(
            FiPaymentRepository fiPaymentRepository,
            RecallInvestigationRepository recallInvestigationRepository) {
        this(fiPaymentRepository, recallInvestigationRepository, PaymentObservability.noop());
    }

    @Autowired
    public GetFiPaymentStatusService(
            FiPaymentRepository fiPaymentRepository,
            RecallInvestigationRepository recallInvestigationRepository,
            PaymentObservability observability) {
        this.fiPaymentRepository = fiPaymentRepository;
        this.recallInvestigationRepository = recallInvestigationRepository;
        this.observability = observability;
    }

    public FiPaymentStatusResponse getStatus(String paymentIdValue, AuthorizationContext authorizationContext) {
        var paymentId = parsePaymentId(paymentIdValue);
        var record = fiPaymentRepository.findByIdAndOwnerClientId(paymentId, authorizationContext.clientId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment was not found"));
        observability.fiPaymentStatusLookup(record, authorizationContext);
        var recallSummary = recallInvestigationRepository
                .findByPaymentIdAndOwnerClientId(paymentId, authorizationContext.clientId())
                .map(this::toRecallSummary)
                .orElse(null);
        return toResponse(record, recallSummary);
    }

    private FiPaymentId parsePaymentId(String paymentIdValue) {
        try {
            return new FiPaymentId(UUID.fromString(paymentIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("paymentId must be a UUID string");
        }
    }

    private FiPaymentStatusResponse toResponse(
            FiPaymentRecord record,
            RecallInvestigationSummaryResponse recallSummary) {
        var paymentId = record.paymentId().value().toString();
        return new FiPaymentStatusResponse(
                paymentId,
                record.status().name(),
                record.identifiers().messageId(),
                record.identifiers().instructionId(),
                record.identifiers().originalPaymentReference(),
                record.instructingParty().bic(),
                record.instructedParty().bic(),
                record.settlementCurrency(),
                toContextResponse(record.correspondentSettlementContext()),
                recallSummary,
                record.reason().map(this::toReasonResponse).orElse(null),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                new PaymentLinksResponse("/v1/fi-payments/" + paymentId, null));
    }

    private RecallInvestigationSummaryResponse toRecallSummary(RecallInvestigationRecord record) {
        return new RecallInvestigationSummaryResponse(
                record.investigationId().value().toString(),
                record.recallMessageId(),
                record.caseId(),
                record.originalPaymentReference(),
                record.status().name(),
                record.reasonCode().orElse(null),
                record.reasonMessage().orElse(null),
                record.createdAt(),
                record.updatedAt());
    }

    private CorrespondentSettlementContextResponse toContextResponse(CorrespondentSettlementContext context) {
        return new CorrespondentSettlementContextResponse(
                context.instructingAgent().bic(),
                context.instructedAgent().bic(),
                context.correspondentOrIntermediaryBank().map(party -> party.bic()).orElse(null),
                context.settlementCurrency(),
                context.accountRelationshipRole().name(),
                context.maskedSimulatedAccountReference());
    }

    private PaymentReasonResponse toReasonResponse(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }
}
