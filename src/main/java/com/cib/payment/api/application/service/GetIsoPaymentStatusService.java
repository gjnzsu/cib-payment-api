package com.cib.payment.api.application.service;

import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.PaymentEngineStatusQueryPort;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.PaymentId;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetIsoPaymentStatusService {
    private final PaymentEngineStatusQueryPort paymentEngineStatusQueryPort;

    public GetIsoPaymentStatusService(PaymentEngineStatusQueryPort paymentEngineStatusQueryPort) {
        this.paymentEngineStatusQueryPort = paymentEngineStatusQueryPort;
    }

    public String getStatusReport(String paymentIdValue, AuthorizationContext authorizationContext) {
        var paymentId = parsePaymentId(paymentIdValue);
        var record = paymentEngineStatusQueryPort.findByPaymentId(paymentId, authorizationContext)
                .orElseThrow(() -> new PaymentNotFoundException("Payment was not found"));
        return record.latestStatusReportXml()
                .orElseThrow(() -> new PaymentNotFoundException("Payment status report was not found"));
    }

    private PaymentId parsePaymentId(String paymentIdValue) {
        try {
            return new PaymentId(UUID.fromString(paymentIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("paymentId must be a UUID string");
        }
    }
}
