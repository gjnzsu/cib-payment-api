package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.IdempotencyRecord;
import java.util.Optional;

public interface IdempotencyRepository {
    Optional<IdempotencyRecord> find(String clientId, String idempotencyKey);

    IdempotencyRecord saveIfAbsent(IdempotencyRecord record);
}
