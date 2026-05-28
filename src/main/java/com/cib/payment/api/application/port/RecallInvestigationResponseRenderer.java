package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.RecallInvestigationRecord;

public interface RecallInvestigationResponseRenderer {
    String render(RecallInvestigationRecord record);
}
