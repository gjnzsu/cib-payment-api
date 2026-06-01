package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.AchBatchEntryStatusResponse;
import com.cib.payment.api.api.dto.AchBatchStatusResponse;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.AchBatchRepository;
import com.cib.payment.api.domain.model.AchBatchEntry;
import com.cib.payment.api.domain.model.AchBatchId;
import com.cib.payment.api.domain.model.AchBatchRecord;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetAchBatchStatusService {
    private static final String RAIL = "ACH";

    private final AchBatchRepository achBatchRepository;

    public GetAchBatchStatusService(AchBatchRepository achBatchRepository) {
        this.achBatchRepository = achBatchRepository;
    }

    public AchBatchStatusResponse getStatus(String batchIdValue, AuthorizationContext authorizationContext) {
        var batchId = parseBatchId(batchIdValue);
        var record = achBatchRepository.find(batchId)
                .filter(batch -> batch.clientId().equals(authorizationContext.clientId()))
                .orElseThrow(() -> new PaymentNotFoundException("Payment was not found"));
        return toResponse(record);
    }

    private AchBatchId parseBatchId(String batchIdValue) {
        try {
            return new AchBatchId(UUID.fromString(batchIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("batchId must be a UUID string");
        }
    }

    private AchBatchStatusResponse toResponse(AchBatchRecord record) {
        var batchId = record.batchId().value().toString();
        return new AchBatchStatusResponse(
                batchId,
                RAIL,
                record.batchReference(),
                record.originatorName(),
                record.effectiveEntryDate(),
                record.status().name(),
                record.entryCount(),
                toMoneyRequest(record.totalAmount()),
                record.entries().stream().map(this::toEntryStatus).toList(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                new PaymentLinksResponse("/v1/ach-batches/" + batchId, null));
    }

    private AchBatchEntryStatusResponse toEntryStatus(AchBatchEntry entry) {
        return new AchBatchEntryStatusResponse(
                entry.entryId().value().toString(),
                entry.entryReference(),
                entry.receiverName(),
                toMoneyRequest(entry.amount()),
                entry.status().name(),
                entry.reason().map(this::toReasonResponse).orElse(null));
    }

    private MoneyRequest toMoneyRequest(Money money) {
        return new MoneyRequest(money.currency(), money.value());
    }

    private PaymentReasonResponse toReasonResponse(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }
}
