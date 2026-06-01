package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.api.dto.RtgsPaymentStatusResponse;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.RtgsPaymentRepository;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.RtgsPaymentId;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetRtgsPaymentStatusService {
    private static final String RAIL = "RTGS";

    private final RtgsPaymentRepository rtgsPaymentRepository;

    public GetRtgsPaymentStatusService(RtgsPaymentRepository rtgsPaymentRepository) {
        this.rtgsPaymentRepository = rtgsPaymentRepository;
    }

    public RtgsPaymentStatusResponse getStatus(String paymentIdValue, AuthorizationContext authorizationContext) {
        var paymentId = parsePaymentId(paymentIdValue);
        var record = rtgsPaymentRepository.find(paymentId)
                .filter(payment -> payment.ownerClientId().equals(authorizationContext.clientId()))
                .orElseThrow(() -> new PaymentNotFoundException("Payment was not found"));
        return toResponse(record);
    }

    private RtgsPaymentId parsePaymentId(String paymentIdValue) {
        try {
            return new RtgsPaymentId(UUID.fromString(paymentIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("paymentId must be a UUID string");
        }
    }

    private RtgsPaymentStatusResponse toResponse(RtgsPaymentRecord record) {
        var paymentId = record.paymentId().value().toString();
        return new RtgsPaymentStatusResponse(
                paymentId,
                RAIL,
                record.paymentReference(),
                record.clientSegment().name(),
                record.debtorAccount().map(this::toAccountRequest).orElse(null),
                record.creditorAccount().map(this::toAccountRequest).orElse(null),
                record.instructingAgentBic().orElse(null),
                record.instructedAgentBic().orElse(null),
                toMoneyRequest(record.settlementAmount()),
                record.requestedSettlementDate(),
                record.settlementPriority(),
                record.purpose(),
                record.status().name(),
                record.settlementFinality(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                new PaymentLinksResponse("/v1/rtgs-payments/" + paymentId, null));
    }

    private AccountReferenceRequest toAccountRequest(AccountReference account) {
        return new AccountReferenceRequest(account.bankCode(), account.accountNumber(), account.accountName());
    }

    private MoneyRequest toMoneyRequest(Money money) {
        return new MoneyRequest(money.currency(), money.value());
    }

    private PaymentReasonResponse toReasonResponse(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }
}
