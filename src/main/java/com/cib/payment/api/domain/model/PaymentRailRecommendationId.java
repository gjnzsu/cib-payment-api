package com.cib.payment.api.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PaymentRailRecommendationId(UUID value) {
    public PaymentRailRecommendationId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
