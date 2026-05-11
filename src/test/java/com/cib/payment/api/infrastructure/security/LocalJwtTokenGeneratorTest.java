package com.cib.payment.api.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalJwtTokenGeneratorTest {
    @Test
    void generatedLocalTokenCanBeDecodedByLocalJwtDecoder() {
        var token = LocalJwtTokenGenerator.generate(
                "client-a",
                "payments:create payments:read",
                Instant.now(),
                3600);

        var decoder = new LocalJwtDecoderConfig()
                .localJwtDecoder("bank-auth-server", "domestic-payment-api");
        var jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("client-a");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("payments:create payments:read");
        assertThat(jwt.getAudience()).contains("domestic-payment-api");
    }

    @Test
    void commandFriendlyCommaSeparatedScopesAreNormalizedForPostmanTokens() {
        var token = LocalJwtTokenGenerator.generateFromArgs(
                new String[] {"client-a", "payments:create,payments:read", "3600"},
                Instant.now());

        var decoder = new LocalJwtDecoderConfig()
                .localJwtDecoder("bank-auth-server", "domestic-payment-api");
        var jwt = decoder.decode(token);

        assertThat(jwt.getClaimAsString("scope")).isEqualTo("payments:create payments:read");
    }
}
