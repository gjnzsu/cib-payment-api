package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CollectionRecord;

public interface CollectionSimulator {
    CollectionSimulatorOutcome process(
            CollectionRecord acceptedRecord,
            AuthorizationContext authorizationContext,
            String scenario);
}
