package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AchBatchRecord;
import com.cib.payment.api.domain.model.AuthorizationContext;

public interface AchDirectCreditSimulator {
    AchDirectCreditOutcome process(
            AchBatchRecord acceptedRecord,
            AuthorizationContext authorizationContext,
            String scenario);
}
