package com.cib.payment.api.application.port;

public interface RecallInvestigationSimulator {
    RecallInvestigationOutcome investigate(String scenarioContext);
}
