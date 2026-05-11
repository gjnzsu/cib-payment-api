package com.cib.payment.api.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record AuthorizationContext(
        String clientId,
        String subject,
        Set<String> scopes,
        String tenantId,
        Map<String, Object> actor,
        Instant issuedAt,
        String tokenId,
        CorrelationId correlationId
) {
}
