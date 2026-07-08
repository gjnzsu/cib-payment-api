package com.cib.payment.api.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CollectionEntryId(UUID value) {
    public CollectionEntryId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
