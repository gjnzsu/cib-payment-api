package com.cib.payment.api.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MandateId(UUID value) {
    public MandateId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
