package com.cib.payment.api.domain.model;

public enum CollectionStatus {
    ACCEPTED,
    SETTLEMENT_PENDING,
    PENDING_AUTHORIZATION,
    COLLECTED,
    COMPLETED,
    PARTIALLY_RETURNED,
    REJECTED,
    TIMEOUT,
    FAILED
}
