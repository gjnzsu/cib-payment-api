package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AchBatchId;
import com.cib.payment.api.domain.model.AchBatchRecord;
import java.util.Optional;

public interface AchBatchRepository {
    AchBatchRecord save(AchBatchRecord record);

    Optional<AchBatchRecord> find(AchBatchId batchId);
}
