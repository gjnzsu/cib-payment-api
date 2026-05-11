package com.cib.payment.api.application.service;

import com.cib.payment.api.api.CorrelationIdFilter;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationContextService {
    public AuthorizationContext from(Jwt jwt, HttpServletRequest request) {
        var subject = jwt.getSubject();
        return new AuthorizationContext(
                subject,
                subject,
                scopes(jwt),
                jwt.getClaimAsString("tenant_id"),
                actor(jwt),
                issuedAt(jwt),
                jwt.getId(),
                new CorrelationId(String.valueOf(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME))));
    }

    private Set<String> scopes(Jwt jwt) {
        var scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scope.split(" "))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Map<String, Object> actor(Jwt jwt) {
        var actor = jwt.getClaimAsMap("actor");
        return actor == null ? Map.of() : actor;
    }

    private Instant issuedAt(Jwt jwt) {
        return jwt.getIssuedAt();
    }
}
