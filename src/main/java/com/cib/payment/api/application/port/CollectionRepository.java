package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.CollectionId;
import com.cib.payment.api.domain.model.CollectionRecord;
import java.util.Optional;

public interface CollectionRepository {
    CollectionRecord save(CollectionRecord record);

    Optional<CollectionRecord> find(CollectionId collectionId);
}
