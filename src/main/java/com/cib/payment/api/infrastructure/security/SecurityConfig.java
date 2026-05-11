package com.cib.payment.api.infrastructure.security;

import com.cib.payment.api.api.CorrelationIdFilter;
import com.cib.payment.api.api.dto.ErrorResponse;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.CorrelationId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PaymentObservability observability,
            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/openapi/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/domestic-payments")
                        .hasAuthority("SCOPE_payments:create")
                        .requestMatchers(HttpMethod.GET, "/v1/domestic-payments/*")
                        .hasAuthority("SCOPE_payments:read")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, exception) -> {
                            observability.authFailure("unauthorized", correlation(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)));
                            writeError(
                                    response,
                                    objectMapper,
                                    HttpStatus.UNAUTHORIZED,
                                    "UNAUTHORIZED",
                                    "Authentication is required or invalid",
                                    request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            observability.authFailure("forbidden", correlation(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)));
                            writeError(
                                    response,
                                    objectMapper,
                                    HttpStatus.FORBIDDEN,
                                    "FORBIDDEN",
                                    "Authenticated client lacks the required scope",
                                    request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME));
                        }))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    private CorrelationId correlation(Object value) {
        return new CorrelationId(value == null ? "unknown" : value.toString());
    }

    private void writeError(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message,
            Object correlationId) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse(code, message, status.value(), correlation(correlationId).value(), List.of()));
    }
}
