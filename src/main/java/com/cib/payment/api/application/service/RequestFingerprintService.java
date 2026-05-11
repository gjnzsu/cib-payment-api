package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class RequestFingerprintService {
    private final ObjectMapper objectMapper;

    public RequestFingerprintService() {
        this(new ObjectMapper().findAndRegisterModules()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true));
    }

    RequestFingerprintService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String fingerprint(
            String clientId,
            CreateDomesticPaymentRequest requestBody,
            Map<String, ?> behaviorallyRelevantContext) {
        var payload = new TreeMap<String, Object>();
        payload.put("clientId", clientId);
        payload.put("requestBody", requestBody);
        payload.put("context", new TreeMap<>(behaviorallyRelevantContext));
        return sha256Hex(canonicalJson(payload));
    }

    private byte[] canonicalJson(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize request fingerprint payload", exception);
        }
    }

    private String sha256Hex(byte[] input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(input);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
