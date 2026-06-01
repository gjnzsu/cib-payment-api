package com.cib.payment.api.api.dto;

public record RtgsRelatedCapabilityResponse(
        String rel,
        String href,
        String method,
        String requiredScope) {}
