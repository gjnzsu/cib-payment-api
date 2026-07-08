package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.CollectionEntryStatusResponse;
import com.cib.payment.api.api.dto.CollectionStatusResponse;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.CollectionRepository;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CollectionEntry;
import com.cib.payment.api.domain.model.CollectionId;
import com.cib.payment.api.domain.model.CollectionRecord;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetCollectionStatusService {
    private final CollectionRepository collectionRepository;

    public GetCollectionStatusService(CollectionRepository collectionRepository) {
        this.collectionRepository = collectionRepository;
    }

    public CollectionStatusResponse getStatus(
            String collectionIdValue,
            AuthorizationContext authorizationContext) {
        var collectionId = parseCollectionId(collectionIdValue);
        var record = collectionRepository.find(collectionId)
                .filter(collection -> collection.clientId().equals(authorizationContext.clientId()))
                .orElseThrow(() -> new PaymentNotFoundException("Payment was not found"));
        return toResponse(record);
    }

    private CollectionId parseCollectionId(String collectionIdValue) {
        try {
            return new CollectionId(UUID.fromString(collectionIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("collectionId must be a UUID string");
        }
    }

    private CollectionStatusResponse toResponse(CollectionRecord record) {
        var collectionId = record.collectionId().value().toString();
        return new CollectionStatusResponse(
                collectionId,
                record.collectionProfile().name(),
                record.collectionReference(),
                record.mandateReference(),
                record.creditorName(),
                record.debtorName(),
                record.status().name(),
                record.entryCount(),
                toMoneyRequest(record.totalAmount()),
                record.entries().stream().map(this::toEntryStatus).toList(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                new PaymentLinksResponse("/v1/collections/" + collectionId, null));
    }

    private CollectionEntryStatusResponse toEntryStatus(CollectionEntry entry) {
        return new CollectionEntryStatusResponse(
                entry.entryId().value().toString(),
                entry.entryReference(),
                entry.payerName(),
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
