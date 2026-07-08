package com.cib.payment.api.api.dto;

import java.util.List;

public record AdvisorScenarioCatalogResponse(
        List<AdvisorScenarioSummaryResponse> scenarios,
        String correlationId) {}
