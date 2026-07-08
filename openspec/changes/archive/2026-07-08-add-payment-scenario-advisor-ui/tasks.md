## 1. Advisor Catalog And API

- [x] 1.1 Add advisor catalog domain/DTO records for scenario summary, scenario detail, recommendation summary, simulation plan, and feedback report framing.
- [x] 1.2 Implement a deterministic advisor catalog service with four MVP scenarios mapped to RTP, ACH, RTGS, and FI correspondent arrangement guidance.
- [x] 1.3 Add advisor controller endpoints for listing scenarios and retrieving one scenario by ID, including consistent 404 error handling.
- [x] 1.4 Add unit and integration tests for catalog mapping, endpoint responses, correlation IDs, no idempotency requirement, and unknown scenario errors.

## 2. Minimal Advisor UI

- [x] 2.1 Add a static Payment Scenario Advisor UI under `src/main/resources/static/payment-scenario-advisor/`.
- [x] 2.2 Implement client-side scenario loading, scenario selection, recommendation review, simulation plan display, and feedback result visualization.
- [x] 2.3 Update security configuration and tests so the advisor UI is publicly reachable while payment execution APIs remain secured.

## 3. Contract And Developer Support

- [x] 3.1 Document the advisor API and UI in OpenAPI, README, and developer support docs.
- [x] 3.2 Add contract/developer artifact tests for advisor paths, schemas, and documentation alignment.

## 4. Verification And Governance

- [x] 4.1 Run focused advisor tests while applying TDD red-green cycles.
- [x] 4.2 Run full `mvn test`.
- [x] 4.3 Run `openspec validate add-payment-scenario-advisor-ui --type change --strict`.
- [x] 4.4 Perform code review and address critical or important findings before handing off for manual testing.
