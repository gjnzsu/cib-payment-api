## Context

The simulator already exposes payment creation and status APIs for domestic RTP baseline, ACH Direct Credit batches, RTGS payments, and FI correspondent payment flows. These APIs demonstrate rail behavior, but they do not help an API user decide which rail or arrangement is appropriate for a neutral payment intent.

This change adds a deterministic recommendation API as a product-facing interface for a future payment rail copilot. The API must stay inside the simulator boundary: it recommends and explains, but it does not create payments, invoke real AI, store recommendation resources, or make production rail suitability decisions.

## Goals / Non-Goals

**Goals:**

- Add a stateless `POST /v1/payment-rail-recommendations` endpoint.
- Accept rail-neutral domestic USD payment intent input.
- Recommend RTP, ACH, RTGS, or FI correspondent with explicit rail and arrangement fields.
- Return explainable rule output: confidence level, matched factors, warnings, tradeoffs, alternatives, and next API guidance.
- Return explanatory unsupported results for valid but unsupported intents such as cross-border or non-USD.
- Align auth, correlation, OpenAPI, Postman, and developer documentation with existing API controls.

**Non-Goals:**

- No real AI, LLM, model provider, or agent runtime.
- No payment creation, payment orchestration, or automatic handoff to rail creation endpoints.
- No recommendation persistence, query endpoint, replay, idempotency, audit evidence store, or durable history.
- No real pricing, liquidity, sanctions, fraud, compliance, FX, or rail availability decision.
- No cross-border recommendation support beyond an unsupported result.
- No real scheme limits; thresholds are deterministic simulator values only.

## Decisions

### Deterministic Rules Over AI Runtime

Use an application service with deterministic rules for the MVP. This keeps the endpoint testable and makes every recommendation explainable through matched factors and reason codes. A future AI copilot can be placed behind the same application port or service boundary after the API contract proves useful.

Alternatives considered:
- Real AI runtime: rejected for this slice because it introduces provider configuration, non-determinism, prompt safety, and evaluation scope.
- Static examples only: rejected because it would not provide a reusable API surface.

### Rail Plus Arrangement Output

Return a `recommendedOption` containing both `rail` and `arrangement`. This preserves the distinction between FI RTGS and FI correspondent payment:

- FI RTGS: `rail=RTGS`, `arrangement=DOMESTIC_INTERBANK_GROSS_SETTLEMENT`
- FI correspondent: `rail=FI_CORRESPONDENT`, `arrangement=CORRESPONDENT_ACCOUNT_PATH`

This is more precise than treating FI correspondent as the same dimension as RTP, ACH, and RTGS.

### Intent Summary Input Instead Of Rail-Specific Payloads

Use rail-neutral input fields such as `paymentCount`, `amountSummary`, `urgency`, `requiresFinality`, `batchPreferred`, `costSensitivity`, `clientSegment`, and optional `arrangementPreference`. The recommendation API must not accept full payment creation payloads or account identifiers.

For multiple-payment intents, `amountSummary.totalAmount` is required and `amountSummary.maxSingleAmount` is optional. The request does not include `averageAmount`; any average can be derived internally if needed, but MVP rules must not depend on precise average amount.

### Unsupported Intent As Recommendation Result

Valid but unsupported business intent returns `200 OK` with `recommendationStatus=UNSUPPORTED`, null recommendation/guidance fields, and warnings. Invalid request shape or missing required fields still returns a validation error. This preserves copilot-style explanatory behavior without weakening input validation.

### Stateless Recommendation

Do not require `Idempotency-Key` and do not add `GET /v1/payment-rail-recommendations/{recommendationId}`. The endpoint returns a generated `recommendationId` for response-local diagnostics and future extension, but the ID is not queryable in the MVP.

### Recommendation Scope

Require JWT Bearer authentication and `payment-rail-recommendations:create`. The scope follows the resource-based style used by existing payment creation APIs while keeping it separate from payment execution scopes.

### Demo Thresholds

Use `100000 USD` as the deterministic simulator threshold:

- Single immediate payment at or below the threshold can recommend RTP.
- High-value or finality-driven single payment can recommend RTGS.
- Batch signal recommends ACH, with warnings or alternatives when high-value single entries or finality signals conflict.

Documentation and OpenAPI descriptions must state that this is not a real scheme limit or production decision threshold.

## Risks / Trade-offs

- **Risk: Recommendation appears like production advice** -> Mitigate with explicit simulator-only descriptions, warning fields, and documentation that excludes pricing, compliance, sanctions, liquidity, FX, and real rail availability decisions.
- **Risk: FI correspondent is confused with RTP/ACH/RTGS rails** -> Mitigate by using rail plus arrangement and by documenting correspondent account path as a separate FI arrangement.
- **Risk: Rule precedence hides conflicting signals** -> Mitigate by returning matched factors, warnings, alternatives, and structured tradeoffs.
- **Risk: Request grows into payment creation payload** -> Mitigate by excluding account identifiers and full payment payload details from the recommendation request.
- **Risk: No persistence limits auditability** -> Accept for MVP; use correlation ID and response-local recommendation ID for diagnostics. Persistence can be introduced later through a separate change if evidence capture becomes a product requirement.

## Migration Plan

This is an additive API change. Existing payment creation, status query, ACH, RTGS, FI correspondent, and recall/investigation endpoints remain unchanged.

Implementation should proceed behind new endpoint, service, DTO, and documentation changes. Rollback is removal of the new endpoint and artifacts with no data migration.

## Open Questions

- None for MVP. Future changes may decide whether recommendation evidence should be persisted, whether sandbox UI should call this API, or whether a real AI copilot should sit behind the deterministic recommendation contract.
