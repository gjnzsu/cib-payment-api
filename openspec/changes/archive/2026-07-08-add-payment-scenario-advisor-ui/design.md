## Context

The service already exposes deterministic payment rail recommendation guidance through `POST /v1/payment-rail-recommendations` and simulator-only payment journeys for RTP, ACH Direct Credit, RTGS, and FI correspondent arrangements. These capabilities are technically complete but remain separated across API endpoints, Postman folders, and developer documentation.

The Payment Scenario Advisor MVP adds a business-facing layer that helps API users understand which existing banking API scenario best fits a payment use case, then validates that guidance through existing simulator metadata. It must preserve the current layered architecture and the simulator-only constraints: recommendation guidance is not autonomous payment routing, and mock scenarios are not production payment semantics.

## Goals / Non-Goals

**Goals:**

- Provide four curated MVP payment scenarios that cover RTP, ACH, RTGS, and FI correspondent arrangement paths.
- Expose a read-only advisor API that returns scenario metadata, recommendation intent, expected recommendation, simulator plan, and feedback report framing.
- Serve a minimal static UI for business-facing scenario selection, recommendation review, simulation-plan review, and result visualization.
- Keep existing secured payment APIs and payment rail recommendation API contracts unchanged.
- Keep account identifiers and sensitive payload details out of the advisor catalog and UI.

**Non-Goals:**

- No real AI, LLM integration, autonomous payment execution, or real payment routing.
- No recommendation persistence, advisor session persistence, or stored feedback reports.
- No cross-border, FX, pricing, liquidity, sanctions, fraud, compliance, or real rail availability decisions.
- No custom API gateway or new frontend build pipeline.
- No automatic conversion from recommendation response into a production-ready payment payload.

## Decisions

### Decision 1: Add a Thin Advisor API Instead of Changing Existing Payment APIs

The advisor will expose new read-only endpoints under `/v1/payment-scenario-advisor`:

- `GET /v1/payment-scenario-advisor/scenarios`
- `GET /v1/payment-scenario-advisor/scenarios/{scenarioId}`

Each scenario response will include the deterministic recommendation intent and simulator plan for that scenario. This avoids changing the existing recommendation endpoint or the payment execution simulators.

Alternative considered: add scenario guidance directly to `/v1/payment-rail-recommendations`. This would couple a generic recommendation contract to a curated demo catalog and make mock scenario leakage more likely.

### Decision 2: Use a Static In-Process UI

The UI will be a static page served by Spring Boot from `src/main/resources/static/payment-scenario-advisor/`. It will use browser `fetch` calls against the advisor API and render the advisor loop client-side.

Alternative considered: add a separate frontend application. That would be heavier than the MVP needs and would add build/deployment complexity unrelated to the banking API value.

### Decision 3: Keep Simulator Execution as Guidance in the MVP

The advisor API will provide simulator plan metadata such as endpoint, method, required scopes, idempotency requirement, payload format, mock scenario, expected result, and status lookup. It will not call the secured payment simulator APIs on behalf of the user.

Alternative considered: server-side orchestration that invokes simulator create and status APIs. That requires token management, idempotency keys, XML/JSON payload generation, error handling, and security semantics that are larger than the MVP. The UI can still present a complete validation loop by showing the simulator plan and expected validation result.

### Decision 4: Keep Advisor Catalog in Application Code

The curated scenarios will be defined by a focused application service using immutable DTO/domain records. This keeps the catalog deterministic, easy to test, and separate from payment domain lifecycle logic.

Alternative considered: external JSON catalog. That is useful later for non-developer editing, but Java records and tests are simpler for the first MVP and align with current code patterns.

## Risks / Trade-offs

- Advisor output could be mistaken for production rail routing -> Mitigate with explicit `simulatorOnly`, `requiresUserConfirmation`, and no real execution language in the response and UI.
- Static UI could expose too much technical detail -> Mitigate by using business scenario labels first, then API details only in the simulation plan section.
- Mock scenario metadata could leak into domain semantics -> Mitigate by keeping mock fields inside a `simulationPlan` object and not reusing payment domain models for advisor catalog entries.
- MVP does not execute real simulator calls from the UI -> Mitigate by presenting it as validation guidance and leaving room for a future explicit run endpoint after token/idempotency handling is specified.

## Migration Plan

This is an additive change. Deployment adds static UI assets and read-only advisor endpoints. Rollback removes the advisor endpoints and static UI without affecting existing payment APIs, recommendation API, Postman artifacts, or simulator behavior.

## Open Questions

- A future change can decide whether to add an authenticated `POST /v1/payment-scenario-advisor/simulations` endpoint that performs simulator calls after explicit user confirmation.
- A future change can decide whether advisor scenario catalogs should move to external configuration for non-developer editing.
