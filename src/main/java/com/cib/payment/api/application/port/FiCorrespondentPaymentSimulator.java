package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.FiPaymentCandidate;

public interface FiCorrespondentPaymentSimulator {
    FiCorrespondentPaymentOutcome process(
            FiPaymentCandidate candidate,
            CorrespondentSettlementContext settlementContext,
            AuthorizationContext authorizationContext,
            String scenarioContext);
}
