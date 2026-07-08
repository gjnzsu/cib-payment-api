package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.MandateRecord;

public interface MandateSimulator {
    MandateSimulatorOutcome process(
            MandateRecord acceptedRecord,
            AuthorizationContext authorizationContext,
            String scenario);
}
