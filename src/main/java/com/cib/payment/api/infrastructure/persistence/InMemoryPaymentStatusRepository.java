package com.cib.payment.api.infrastructure.persistence;

import com.cib.payment.api.application.port.PaymentStatusRepository;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentStatusRepository implements PaymentStatusRepository {
    private final ConcurrentHashMap<PaymentId, PaymentRecord> records = new ConcurrentHashMap<>();

    @Override
    public PaymentRecord save(PaymentRecord record) {
        records.put(record.paymentId(), record);
        return record;
    }

    @Override
    public Optional<PaymentRecord> findByPaymentIdAndClientId(PaymentId paymentId, String clientId) {
        return Optional.ofNullable(records.get(paymentId))
                .filter(record -> record.clientId().equals(clientId));
    }

    @Override
    public PaymentRecord updateStatus(PaymentId paymentId, PaymentStatus status, PaymentReason reason) {
        return records.compute(paymentId, (ignored, existing) -> {
            if (existing == null) {
                throw new IllegalArgumentException("Unknown payment id: " + paymentId.value());
            }
            return new PaymentRecord(
                    existing.paymentId(),
                    existing.clientId(),
                    existing.instruction(),
                    status,
                    existing.createdAt(),
                    Instant.now(),
                    existing.correlationId(),
                    Optional.ofNullable(reason));
        });
    }
}
