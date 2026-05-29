package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.RecallInvestigationStatus;
import java.util.Optional;

public record RecallInvestigationOutcome(
        RecallInvestigationStatus status,
        Optional<String> reasonCode,
        Optional<String> reasonMessage) {
    public RecallInvestigationOutcome {
        reasonCode = reasonCode == null ? Optional.empty() : reasonCode;
        reasonMessage = reasonMessage == null ? Optional.empty() : reasonMessage;
    }

    public RecallInvestigationOutcome(
            RecallInvestigationStatus status,
            String reasonCode,
            String reasonMessage) {
        this(status, Optional.ofNullable(reasonCode), Optional.ofNullable(reasonMessage));
    }
}
