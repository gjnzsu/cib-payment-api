package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CancelMandateRequest(String cancellationReason) {}
