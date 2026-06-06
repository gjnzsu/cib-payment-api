## Why

The payment simulator now demonstrates RTP, ACH, RTGS, and FI correspondent flows, but API users still need a structured way to understand which rail or arrangement fits a payment intent. A deterministic recommendation API creates a safe bridge toward a future payment rail copilot without introducing real AI runtime, payment orchestration, or production decisioning claims.

## What Changes

- Add `POST /v1/payment-rail-recommendations` to return a rule-based recommendation for a domestic USD payment intent.
- Return recommended rail and arrangement, confidence level, matched factors, warnings, alternatives, structured tradeoffs, and next API guidance.
- Support RTP, ACH, RTGS, and FI correspondent recommendation options using the existing simulator product language.
- Model FI correspondent as an arrangement-aware option so FI RTGS and correspondent account path recommendations remain conceptually distinct.
- Treat valid but unsupported intents, such as cross-border or non-USD, as explanatory recommendation responses rather than validation failures.
- Add JWT scope enforcement for `payment-rail-recommendations:create`.
- Keep the recommendation API stateless: no payment creation, no recommendation query endpoint, no idempotency requirement, no durable recommendation storage, and no real AI integration.
- Update OpenAPI, Postman, README/developer guidance, and tests for the recommendation scenarios.

## Capabilities

### New Capabilities

- `payment-rail-recommendations`: Deterministic payment rail and arrangement recommendation API for domestic USD payment intents.

### Modified Capabilities

- `payment-authentication-and-idempotency`: Add recommendation API authentication, scope, correlation, and no-idempotency behavior.
- `payment-developer-support`: Add OpenAPI, Postman, README, and developer guidance expectations for recommendation copilot scenarios.

## Impact

- New API controller, DTOs, domain/application model, rule service, and tests for rail recommendation.
- OpenAPI 3.0.3 contract updates for the recommendation endpoint, schemas, examples, errors, and security scope.
- Postman collection and local environment updates for recommendation scenarios and token guidance.
- Documentation updates clarifying simulator-only thresholds, no real AI runtime, no production suitability scoring, and unsupported intent behavior.
- No database, real payment rail, real AI provider, payment creation orchestration, webhook, callback, FX, sanctions, fraud, pricing, or liquidity integration.
