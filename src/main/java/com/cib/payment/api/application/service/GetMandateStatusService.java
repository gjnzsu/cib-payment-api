package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.MandateStatusResponse;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.MandateRepository;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.MandateId;
import com.cib.payment.api.domain.model.MandateRecord;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetMandateStatusService {
    private final MandateRepository mandateRepository;

    public GetMandateStatusService(MandateRepository mandateRepository) {
        this.mandateRepository = mandateRepository;
    }

    public MandateStatusResponse getStatus(String mandateIdValue, AuthorizationContext authorizationContext) {
        var mandateId = parseMandateId(mandateIdValue);
        var record = mandateRepository.find(mandateId)
                .filter(mandate -> mandate.clientId().equals(authorizationContext.clientId()))
                .orElseThrow(() -> new PaymentNotFoundException("Mandate was not found"));
        return toResponse(record);
    }

    private MandateId parseMandateId(String mandateIdValue) {
        try {
            return new MandateId(UUID.fromString(mandateIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("mandateId must be a UUID string");
        }
    }

    private MandateStatusResponse toResponse(MandateRecord record) {
        var mandateId = record.mandateId().value().toString();
        return new MandateStatusResponse(
                mandateId,
                record.mandateReference(),
                record.mandateProfile().name(),
                record.creditorName(),
                record.debtorName(),
                record.status().name(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                new PaymentLinksResponse("/v1/mandates/" + mandateId, null));
    }

    private PaymentReasonResponse toReasonResponse(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }
}
