package com.cib.payment.api.domain.model;

public enum AchBatchStatus {
    ACCEPTED_FOR_CLEARING,
    SETTLEMENT_PENDING,
    SETTLED,
    PARTIALLY_RETURNED,
    REJECTED
}
