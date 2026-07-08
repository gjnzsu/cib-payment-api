package com.cib.payment.api.infrastructure.persistence;

import com.cib.payment.api.application.port.CollectionRepository;
import com.cib.payment.api.domain.model.CollectionId;
import com.cib.payment.api.domain.model.CollectionRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryCollectionRepository implements CollectionRepository {
    private final ConcurrentHashMap<CollectionId, CollectionRecord> records = new ConcurrentHashMap<>();

    @Override
    public CollectionRecord save(CollectionRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        records.put(record.collectionId(), record);
        return record;
    }

    @Override
    public Optional<CollectionRecord> find(CollectionId collectionId) {
        Objects.requireNonNull(collectionId, "collectionId must not be null");
        return Optional.ofNullable(records.get(collectionId));
    }
}
