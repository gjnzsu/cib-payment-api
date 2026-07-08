# Payment Scenario Advisor UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Payment Scenario Advisor UI and thin advisor API that map four business payment scenarios to rail recommendation guidance, simulator plans, and visual feedback.

**Architecture:** Follow the existing layered Spring Boot structure. A focused application catalog service returns immutable advisor scenario records; an API controller maps them to JSON; static UI assets render the business-facing feedback loop by calling the advisor API. Existing payment rail recommendation and payment simulator APIs remain unchanged.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring MVC, Spring Security, JUnit 5, MockMvc, static HTML/CSS/JavaScript.

---

## File Structure

- Create `src/main/java/com/cib/payment/api/domain/model/AdvisorScenarioId.java`: small value object for stable advisor scenario IDs.
- Create `src/main/java/com/cib/payment/api/domain/model/AdvisorScenario.java`: scenario aggregate-like record for advisor catalog metadata.
- Create `src/main/java/com/cib/payment/api/domain/model/AdvisorRecommendationSummary.java`: recommendation summary record.
- Create `src/main/java/com/cib/payment/api/domain/model/AdvisorSimulationPlan.java`: simulator plan record scoped to mock-only validation guidance.
- Create `src/main/java/com/cib/payment/api/domain/model/AdvisorFeedbackReport.java`: feedback result framing record.
- Create `src/main/java/com/cib/payment/api/application/service/PaymentScenarioAdvisorCatalogService.java`: deterministic catalog service.
- Create DTOs under `src/main/java/com/cib/payment/api/api/dto/` for advisor scenario JSON responses.
- Create `src/main/java/com/cib/payment/api/api/controller/PaymentScenarioAdvisorController.java`: read-only advisor endpoints.
- Modify `src/main/java/com/cib/payment/api/infrastructure/security/SecurityConfig.java`: permit static advisor UI and read-only advisor endpoints.
- Create static files under `src/main/resources/static/payment-scenario-advisor/`: `index.html`, `styles.css`, `app.js`.
- Modify `src/main/resources/openapi/domestic-payment-api.yaml`: add advisor paths and schemas.
- Modify `README.md` and create `docs/developer-support/payment-scenario-advisor.md`.
- Create tests:
  - `src/test/java/com/cib/payment/api/application/service/PaymentScenarioAdvisorCatalogServiceTest.java`
  - `src/test/java/com/cib/payment/api/api/PaymentScenarioAdvisorControllerIntegrationTest.java`
  - update `src/test/java/com/cib/payment/api/api/OpenApiContractTest.java`
  - update `src/test/java/com/cib/payment/api/infrastructure/security/SecurityIntegrationTest.java`

## Task 1: Advisor Catalog Domain And Service

- [ ] **Step 1: Write failing catalog service tests**

Create `PaymentScenarioAdvisorCatalogServiceTest` with assertions for four scenarios, stable IDs, recommended rails, simulator endpoints, explicit confirmation, and no sensitive account data.

Run:

```powershell
mvn -q -Dtest=PaymentScenarioAdvisorCatalogServiceTest test
```

Expected: FAIL because the service and records do not exist.

- [ ] **Step 2: Add minimal domain records and catalog service**

Implement only the fields required by the tests: scenario identity, business labels, intent summary, recommendation summary, simulation plan, and feedback report.

- [ ] **Step 3: Run focused catalog tests**

Run:

```powershell
mvn -q -Dtest=PaymentScenarioAdvisorCatalogServiceTest test
```

Expected: PASS.

## Task 2: Advisor API

- [ ] **Step 1: Write failing controller integration tests**

Create tests for:

- `GET /v1/payment-scenario-advisor/scenarios`
- `GET /v1/payment-scenario-advisor/scenarios/{scenarioId}`
- unknown scenario `404`
- no `Idempotency-Key` required
- correlation ID present in response

Run:

```powershell
mvn -q -Dtest=PaymentScenarioAdvisorControllerIntegrationTest test
```

Expected: FAIL because controller and DTOs do not exist.

- [ ] **Step 2: Add DTOs and controller**

Map domain records to JSON DTOs without exposing account identifiers or raw payment payloads.

- [ ] **Step 3: Permit advisor API in security config**

Add `/v1/payment-scenario-advisor/**` to public read-only matchers. Existing payment execution APIs remain authenticated.

- [ ] **Step 4: Run focused API tests**

Run:

```powershell
mvn -q -Dtest=PaymentScenarioAdvisorControllerIntegrationTest test
```

Expected: PASS.

## Task 3: Minimal Static UI

- [ ] **Step 1: Write failing UI availability/security test**

Add assertions that `/payment-scenario-advisor/` returns HTML without a bearer token and secured payment APIs remain protected.

Run:

```powershell
mvn -q -Dtest=SecurityIntegrationTest test
```

Expected: FAIL until the static UI exists or route is permitted.

- [ ] **Step 2: Add static UI assets**

Create `index.html`, `styles.css`, and `app.js`. The UI loads scenarios, lets the user select one, and renders the four-stage loop: scenario, recommendation, simulator plan, validation feedback.

- [ ] **Step 3: Run UI/security tests**

Run:

```powershell
mvn -q -Dtest=SecurityIntegrationTest test
```

Expected: PASS.

## Task 4: Contract And Documentation

- [ ] **Step 1: Write/update failing OpenAPI contract tests**

Update `OpenApiContractTest` to assert advisor paths, schemas, no idempotency parameter, and simulator-only description.

Run:

```powershell
mvn -q -Dtest=OpenApiContractTest test
```

Expected: FAIL until OpenAPI is updated.

- [ ] **Step 2: Update OpenAPI and docs**

Add advisor paths/schemas to `domestic-payment-api.yaml`, update `README.md`, and add developer support guide.

- [ ] **Step 3: Run contract tests**

Run:

```powershell
mvn -q -Dtest=OpenApiContractTest test
```

Expected: PASS.

## Task 5: Final Verification

- [ ] **Step 1: Run focused advisor tests**

```powershell
mvn -q -Dtest=PaymentScenarioAdvisorCatalogServiceTest,PaymentScenarioAdvisorControllerIntegrationTest,OpenApiContractTest,SecurityIntegrationTest test
```

- [ ] **Step 2: Run full test suite**

```powershell
mvn test
```

- [ ] **Step 3: Validate OpenSpec**

```powershell
openspec validate add-payment-scenario-advisor-ui --type change --strict
```

- [ ] **Step 4: Review changes**

Inspect `git diff --stat` and `git diff` for boundary leaks, sensitive data exposure, and unrelated changes before handoff.
