package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.api.dto.PaymentStatusResponse;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.port.PaymentStatusRepository;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentRecord;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetDomesticPaymentStatusService {
    private final PaymentStatusRepository paymentStatusRepository;
    private final PaymentObservability observability;

    public GetDomesticPaymentStatusService(
            PaymentStatusRepository paymentStatusRepository,
            PaymentObservability observability) {
        this.paymentStatusRepository = paymentStatusRepository;
        this.observability = observability;
    }

    public PaymentStatusResponse getStatus(String paymentIdValue, AuthorizationContext authorizationContext) {
        var paymentId = parsePaymentId(paymentIdValue);
        var record = paymentStatusRepository.findByPaymentIdAndClientId(paymentId, authorizationContext.clientId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment was not found"));
        observability.statusLookup(record, authorizationContext);
        return toResponse(record);
    }

    private PaymentId parsePaymentId(String paymentIdValue) {
        try {
            return new PaymentId(UUID.fromString(paymentIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("paymentId must be a UUID string");
        }
    }

    private PaymentStatusResponse toResponse(PaymentRecord record) {
        var paymentId = record.paymentId().value().toString();
        return new PaymentStatusResponse(
                paymentId,
                record.status().name(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReason).orElse(null),
                new PaymentLinksResponse("/v1/domestic-payments/" + paymentId, null));
    }

    private PaymentReasonResponse toReason(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }
}
