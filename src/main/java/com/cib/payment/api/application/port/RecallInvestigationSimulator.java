package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AuthorizationContext;

public interface RecallInvestigationSimulator {
    RecallInvestigationOutcome investigate(
            AuthorizationContext authorizationContext,
            String scenarioContext);
}
