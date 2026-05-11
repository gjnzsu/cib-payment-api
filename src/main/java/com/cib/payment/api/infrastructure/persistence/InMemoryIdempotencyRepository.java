package com.cib.payment.api.infrastructure.persistence;

import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryIdempotencyRepository implements IdempotencyRepository {
    private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String clientId, String idempotencyKey) {
        return Optional.ofNullable(records.get(key(clientId, idempotencyKey)));
    }

    @Override
    public IdempotencyRecord saveIfAbsent(IdempotencyRecord record) {
        var key = key(record.clientId(), record.idempotencyKey());
        var existing = records.putIfAbsent(key, record);
        return existing == null ? record : existing;
    }

    private String key(String clientId, String idempotencyKey) {
        return clientId + ":" + idempotencyKey;
    }
}
