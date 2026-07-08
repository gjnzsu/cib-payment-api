## Why

API users can call the payment rail recommendation endpoint and the individual payment simulators, but they do not yet have a business-facing loop that turns a payment use case into a recommended rail, validates it through the existing simulator suite, and presents the result in a customer-readable form. A minimal Payment Scenario Advisor UI will make the recommendation and simulator capabilities feel like one product experience while preserving the existing simulator-only boundaries.

## What Changes

- Add a Payment Scenario Advisor capability that lets a user select one of four MVP business payment scenarios.
- Add a thin advisor orchestration API that returns scenario metadata, deterministic recommendation intent, expected recommendation, simulator plan, and feedback report metadata.
- Add a minimal static UI served by the Spring Boot app for scenario selection, recommendation review, simulation-plan review, and visual feedback.
- Keep existing payment rail recommendation and payment simulator APIs unchanged.
- Keep simulator execution explicitly mock-only and separate from production payment semantics.
- Do not add real AI, LLM integration, autonomous payment routing, payment execution persistence, pricing, liquidity, sanctions, fraud, FX, cross-border support, or real rail availability decisions.

## Capabilities

### New Capabilities

- `payment-scenario-advisor`: Covers the business-facing advisor loop that maps supported payment scenarios to recommendation intent, simulator guidance, and visual feedback.

### Modified Capabilities

- None.

## Impact

- Adds advisor domain/application/API/UI code under the existing layered Spring Boot structure.
- Adds a public static advisor UI route that is separate from secured payment APIs.
- Adds focused tests for scenario catalog mapping, advisor response shape, UI availability, and OpenAPI contract alignment if an advisor API is exposed in OpenAPI.
- Updates README and developer support documentation to describe the Payment Scenario Advisor MVP and manual testing flow.
