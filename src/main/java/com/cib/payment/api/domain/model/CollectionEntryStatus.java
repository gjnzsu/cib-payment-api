package com.cib.payment.api.domain.model;

public enum CollectionEntryStatus {
    ACCEPTED,
    PENDING_SETTLEMENT,
    COLLECTED,
    RETURNED,
    REJECTED,
    TIMEOUT,
    FAILED
}
