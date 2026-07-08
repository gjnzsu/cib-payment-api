package com.cib.payment.api.domain.model;

public enum MandateStatus {
    PENDING_AUTHORIZATION,
    ACTIVE,
    REJECTED,
    CANCELLED,
    EXPIRED,
    TIMEOUT,
    FAILED
}
