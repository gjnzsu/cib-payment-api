package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;

public interface RtgsPaymentSimulator {
    RtgsPaymentOutcome process(
            RtgsPaymentRecord acceptedRecord,
            AuthorizationContext authorizationContext,
            String scenario);
}
