package com.cib.payment.api.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CollectionId(UUID value) {
    public CollectionId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
