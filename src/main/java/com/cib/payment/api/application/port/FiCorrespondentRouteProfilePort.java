package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.CorrespondentSettlementContext;

public interface FiCorrespondentRouteProfilePort {
    CorrespondentSettlementContext derive(String instructingAgent, String instructedAgent, String currency);
}
