package com.cib.payment.api.api.dto;

import java.util.List;

public record AdvisorRecommendationSummaryResponse(
        String rail,
        String arrangement,
        String clientSegment,
        String confidenceLevel,
        String reasonCode,
        String summary,
        List<String> matchedFactors,
        List<String> tradeoffs) {}
