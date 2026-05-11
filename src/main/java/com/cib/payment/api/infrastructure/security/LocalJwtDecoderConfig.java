package com.cib.payment.api.infrastructure.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class LocalJwtDecoderConfig {
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder localJwtDecoder(
            @Value("${payment-api.jwt.issuer}") String issuer,
            @Value("${payment-api.jwt.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withPublicKey(generatedLocalPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", audiences -> audiences != null && audiences.contains(audience)),
                new JwtClaimValidator<String>("sub", subject -> subject != null && !subject.isBlank())));
        return decoder;
    }

    private java.security.interfaces.RSAPublicKey generatedLocalPublicKey() {
        return LocalJwtKeyMaterial.publicKey();
    }
}
