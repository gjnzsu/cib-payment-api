package com.cib.payment.api.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class LocalJwtTokenGenerator {
    private static final String ISSUER = "bank-auth-server";
    private static final String AUDIENCE = "domestic-payment-api";

    private LocalJwtTokenGenerator() {}

    public static void main(String[] args) {
        System.out.println(generateFromArgs(args, Instant.now()));
    }

    public static String generateFromArgs(String[] args, Instant issuedAt) {
        var subject = value(args, 0, "client-a");
        var scope = normalizeScope(value(args, 1, "payments:create,payments:read"));
        var ttlSeconds = Long.parseLong(value(args, 2, "3600"));
        return generate(subject, scope, issuedAt, ttlSeconds);
    }

    public static String generate(String subject, String scope, Instant issuedAt, long ttlSeconds) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(List.of(AUDIENCE))
                    .subject(subject)
                    .claim("scope", scope)
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(issuedAt.plusSeconds(ttlSeconds)))
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID("local-dev-payment-api")
                    .build(), claims);
            jwt.sign(new RSASSASigner(LocalJwtKeyMaterial.privateKey()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate local JWT", exception);
        }
    }

    private static String value(String[] args, int index, String fallback) {
        return args.length > index && !args[index].isBlank() ? args[index] : fallback;
    }

    private static String normalizeScope(String scope) {
        return scope.replace(',', ' ').trim().replaceAll("\\s+", " ");
    }
}
