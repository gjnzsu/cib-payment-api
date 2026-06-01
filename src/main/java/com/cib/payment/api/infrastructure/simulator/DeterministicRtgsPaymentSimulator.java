package com.cib.payment.api.infrastructure.simulator;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.RtgsPaymentOutcome;
import com.cib.payment.api.application.port.RtgsPaymentSimulator;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import com.cib.payment.api.domain.model.RtgsPaymentStatus;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DeterministicRtgsPaymentSimulator implements RtgsPaymentSimulator {
    @Override
    public RtgsPaymentOutcome process(
            RtgsPaymentRecord acceptedRecord,
            AuthorizationContext authorizationContext,
            String scenario) {
        Objects.requireNonNull(acceptedRecord, "acceptedRecord must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");

        if (scenario == null || scenario.isBlank()) {
            throw new ValidationFailureException("RTGS payment simulator scenario is required");
        }

        return switch (scenario) {
            case "rtgs_settled" -> new RtgsPaymentOutcome(
                    RtgsPaymentStatus.SETTLED,
                    true,
                    Optional.empty());
            case "rtgs_queued_for_liquidity" -> new RtgsPaymentOutcome(
                    RtgsPaymentStatus.QUEUED_FOR_LIQUIDITY,
                    false,
                    new PaymentReason(
                            "RTGS_LIQUIDITY_QUEUE",
                            "RTGS payment is queued for liquidity"));
            case "rtgs_rejected" -> new RtgsPaymentOutcome(
                    RtgsPaymentStatus.REJECTED,
                    false,
                    new PaymentReason(
                            "RTGS_PAYMENT_REJECTED",
                            "RTGS payment was rejected"));
            default -> throw new ValidationFailureException(
                    "Unsupported RTGS payment simulator scenario: " + scenario);
        };
    }
}
