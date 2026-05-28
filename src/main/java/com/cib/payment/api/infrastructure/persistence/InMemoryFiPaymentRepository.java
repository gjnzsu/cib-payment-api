package com.cib.payment.api.infrastructure.persistence;

import com.cib.payment.api.application.port.FiPaymentRepository;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryFiPaymentRepository implements FiPaymentRepository {
    private final ConcurrentHashMap<FiPaymentId, FiPaymentRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<FiPaymentRecord> findById(FiPaymentId paymentId) {
        return Optional.ofNullable(records.get(paymentId));
    }

    @Override
    public Optional<FiPaymentRecord> findByIdAndOwnerClientId(FiPaymentId paymentId, String ownerClientId) {
        return findById(paymentId)
                .filter(record -> record.ownerClientId().equals(ownerClientId));
    }

    @Override
    public FiPaymentRecord save(FiPaymentRecord record) {
        records.put(record.paymentId(), record);
        return record;
    }
}
