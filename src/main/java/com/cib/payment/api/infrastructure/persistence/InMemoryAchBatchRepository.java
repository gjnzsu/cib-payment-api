package com.cib.payment.api.infrastructure.persistence;

import com.cib.payment.api.application.port.AchBatchRepository;
import com.cib.payment.api.domain.model.AchBatchId;
import com.cib.payment.api.domain.model.AchBatchRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAchBatchRepository implements AchBatchRepository {
    private final ConcurrentHashMap<AchBatchId, AchBatchRecord> records = new ConcurrentHashMap<>();

    @Override
    public AchBatchRecord save(AchBatchRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        records.put(record.batchId(), record);
        return record;
    }

    @Override
    public Optional<AchBatchRecord> find(AchBatchId batchId) {
        Objects.requireNonNull(batchId, "batchId must not be null");
        return Optional.ofNullable(records.get(batchId));
    }
}
