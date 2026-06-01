package com.cib.payment.api.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record RtgsPaymentRecord(
        RtgsPaymentId paymentId,
        String ownerClientId,
        RtgsClientSegment clientSegment,
        String paymentReference,
        RtgsParties parties,
        Money settlementAmount,
        LocalDate requestedSettlementDate,
        String settlementPriority,
        String purpose,
        RtgsPaymentStatus status,
        boolean settlementFinality,
        CorrelationId correlationId,
        Instant createdAt,
        Instant updatedAt,
        Optional<PaymentReason> reason) {
    public RtgsPaymentRecord {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(ownerClientId, "ownerClientId must not be null");
        Objects.requireNonNull(clientSegment, "clientSegment must not be null");
        Objects.requireNonNull(paymentReference, "paymentReference must not be null");
        Objects.requireNonNull(parties, "parties must not be null");
        Objects.requireNonNull(settlementAmount, "settlementAmount must not be null");
        Objects.requireNonNull(requestedSettlementDate, "requestedSettlementDate must not be null");
        Objects.requireNonNull(settlementPriority, "settlementPriority must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        reason = reason == null ? Optional.empty() : reason;
        if (parties.clientSegment() != clientSegment) {
            throw new IllegalArgumentException("parties must match clientSegment");
        }
        if (settlementFinality && status != RtgsPaymentStatus.SETTLED) {
            throw new IllegalArgumentException("settlementFinality can be true only when status is SETTLED");
        }
    }

    public Optional<AccountReference> debtorAccount() {
        return parties instanceof CorporateParties corporateParties
                ? Optional.of(corporateParties.debtorAccount())
                : Optional.empty();
    }

    public Optional<AccountReference> creditorAccount() {
        return parties instanceof CorporateParties corporateParties
                ? Optional.of(corporateParties.creditorAccount())
                : Optional.empty();
    }

    public Optional<String> instructingAgentBic() {
        return parties instanceof FiParties fiParties
                ? Optional.of(fiParties.instructingAgentBic())
                : Optional.empty();
    }

    public Optional<String> instructedAgentBic() {
        return parties instanceof FiParties fiParties
                ? Optional.of(fiParties.instructedAgentBic())
                : Optional.empty();
    }

    public sealed interface RtgsParties permits CorporateParties, FiParties {
        RtgsClientSegment clientSegment();
    }

    public record CorporateParties(
            AccountReference debtorAccount,
            AccountReference creditorAccount) implements RtgsParties {
        public CorporateParties {
            Objects.requireNonNull(debtorAccount, "debtorAccount must not be null");
            Objects.requireNonNull(creditorAccount, "creditorAccount must not be null");
        }

        @Override
        public RtgsClientSegment clientSegment() {
            return RtgsClientSegment.CORPORATE;
        }
    }

    public record FiParties(
            String instructingAgentBic,
            String instructedAgentBic) implements RtgsParties {
        public FiParties {
            requireText(instructingAgentBic, "instructingAgentBic must not be blank");
            requireText(instructedAgentBic, "instructedAgentBic must not be blank");
        }

        @Override
        public RtgsClientSegment clientSegment() {
            return RtgsClientSegment.FI;
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
