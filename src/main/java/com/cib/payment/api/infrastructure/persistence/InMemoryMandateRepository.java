package com.cib.payment.api.infrastructure.persistence;

import com.cib.payment.api.application.port.MandateRepository;
import com.cib.payment.api.domain.model.MandateId;
import com.cib.payment.api.domain.model.MandateRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryMandateRepository implements MandateRepository {
    private final ConcurrentHashMap<MandateId, MandateRecord> records = new ConcurrentHashMap<>();

    @Override
    public MandateRecord save(MandateRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        records.put(record.mandateId(), record);
        return record;
    }

    @Override
    public Optional<MandateRecord> find(MandateId mandateId) {
        Objects.requireNonNull(mandateId, "mandateId must not be null");
        return Optional.ofNullable(records.get(mandateId));
    }

    @Override
    public Optional<MandateRecord> findByClientIdAndMandateReference(String clientId, String mandateReference) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(mandateReference, "mandateReference must not be null");
        return records.values().stream()
                .filter(record -> record.clientId().equals(clientId))
                .filter(record -> record.mandateReference().equals(mandateReference))
                .findFirst();
    }
}
