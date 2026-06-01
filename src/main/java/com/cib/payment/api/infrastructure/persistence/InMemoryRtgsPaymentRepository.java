package com.cib.payment.api.infrastructure.persistence;

import com.cib.payment.api.application.port.RtgsPaymentRepository;
import com.cib.payment.api.domain.model.RtgsPaymentId;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryRtgsPaymentRepository implements RtgsPaymentRepository {
    private final ConcurrentHashMap<RtgsPaymentId, RtgsPaymentRecord> records = new ConcurrentHashMap<>();

    @Override
    public RtgsPaymentRecord save(RtgsPaymentRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        records.put(record.paymentId(), record);
        return record;
    }

    @Override
    public Optional<RtgsPaymentRecord> find(RtgsPaymentId paymentId) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        return Optional.ofNullable(records.get(paymentId));
    }
}
