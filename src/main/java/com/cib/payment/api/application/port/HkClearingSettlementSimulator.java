package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.InternalInterbankTransfer;

public interface HkClearingSettlementSimulator {
    HkClearingSettlementOutcome process(
            InternalInterbankTransfer transfer,
            AuthorizationContext authorizationContext,
            String scenarioContext);
}
