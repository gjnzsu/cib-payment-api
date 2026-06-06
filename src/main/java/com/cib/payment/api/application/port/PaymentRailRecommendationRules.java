package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.PaymentRailRecommendation;
import com.cib.payment.api.domain.model.RecommendationIntent;

public interface PaymentRailRecommendationRules {
    PaymentRailRecommendation recommend(RecommendationIntent intent);
}
