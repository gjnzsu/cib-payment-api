package com.cib.payment.api.infrastructure.persistence;

import com.cib.payment.api.application.port.RecallInvestigationRepository;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryRecallInvestigationRepository implements RecallInvestigationRepository {
    private final ConcurrentHashMap<FiPaymentId, RecallInvestigationRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<RecallInvestigationRecord> findByPaymentId(FiPaymentId paymentId) {
        return Optional.ofNullable(records.get(paymentId));
    }

    @Override
    public Optional<RecallInvestigationRecord> findByPaymentIdAndOwnerClientId(
            FiPaymentId paymentId,
            String ownerClientId) {
        return findByPaymentId(paymentId)
                .filter(record -> record.ownerClientId().equals(ownerClientId));
    }

    @Override
    public RecallInvestigationRecord saveIfAbsent(RecallInvestigationRecord record) {
        var existing = records.putIfAbsent(record.fiPaymentId(), record);
        return existing == null ? record : existing;
    }
}
