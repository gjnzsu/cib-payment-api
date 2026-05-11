package com.cib.payment.api.testsupport;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;

public final class JwtTestSupport {
    private static final String ISSUER = "bank-auth-server";
    private static final String AUDIENCE = "domestic-payment-api";
    private static final RSAKey RSA_KEY = createRsaKey();

    private JwtTestSupport() {}

    public static String tokenWithScopes(String subject, String... scopes) {
        return token(subject, AUDIENCE, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), scopes);
    }

    public static String expiredToken() {
        return token("client-a", AUDIENCE, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), "payments:create");
    }

    public static String tokenWithWrongAudience() {
        return token(RSA_KEY, "client-a", "other-api", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), "payments:create");
    }

    public static String tokenMissingClaim(String claimName) {
        if (!"sub".equals(claimName)) {
            throw new IllegalArgumentException("Unsupported missing-claim test token: " + claimName);
        }
        return token(RSA_KEY, null, AUDIENCE, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), "payments:create");
    }

    public static String tokenWithInvalidSignature() {
        return token(createRsaKey(), "client-a", AUDIENCE, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), "payments:create");
    }

    public static JwtDecoder jwtDecoder() {
        try {
            var decoder = NimbusJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    new JwtClaimValidator<List<String>>("aud", audiences -> audiences != null && audiences.contains(AUDIENCE)),
                    new JwtClaimValidator<String>("sub", subject -> subject != null && !subject.isBlank())));
            return decoder;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create JWT decoder", exception);
        }
    }

    private static String token(String subject, String audience, Instant issuedAt, Instant expiresAt, String... scopes) {
        return token(RSA_KEY, subject, audience, issuedAt, expiresAt, scopes);
    }

    private static String token(RSAKey key, String subject, String audience, Instant issuedAt, Instant expiresAt, String... scopes) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        var headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(key.getKeyID())
                .build();
        var claimsBuilder = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("scope", String.join(" ", scopes));
        if (subject != null) {
            claimsBuilder.subject(subject);
        }
        var claims = claimsBuilder.build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private static RSAKey createRsaKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate test RSA key", exception);
        }
    }
}
