package com.cib.payment.api.domain.model;

public enum RecommendationMatchedFactor {
    DOMESTIC_USD,
    IMMEDIATE_URGENCY,
    LOW_VALUE,
    HIGH_VALUE,
    MULTIPLE_PAYMENTS,
    BATCH_PREFERRED,
    COST_SENSITIVE,
    REQUIRES_FINALITY,
    FI_CLIENT,
    FI_TO_FI,
    CORRESPONDENT_ACCOUNT_PATH
}
