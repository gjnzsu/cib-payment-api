# Classic Payment Rail Simulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement ACH Direct Credit batch and RTGS payment simulation while preserving existing RTP and FI correspondent flows.

**Architecture:** Follow the existing layered Spring Boot structure: controllers map HTTP, application services orchestrate validation/idempotency/simulators/repositories, domain records hold rail-specific state, and infrastructure provides deterministic simulators plus in-memory repositories. ACH and RTGS records remain separate from existing RTP and FI stores.

**Tech Stack:** Java 21, Spring Boot, Bean Validation, Jackson, Maven, OpenAPI YAML, Postman JSON, OpenSpec.

---

## OpenSpec Traceability Matrix

| OpenSpec task | Plan task(s) |
| --- | --- |
| 1.1 ACH domain records | Task 1 |
| 1.2 RTGS domain records | Task 5 |
| 1.3 Ports | Task 1, Task 5 |
| 1.4 In-memory repositories | Task 2, Task 6 |
| 2.1 ACH DTOs | Task 3 |
| 2.2 ACH admission/validation | Task 3 |
| 2.3 ACH simulator | Task 2 |
| 2.4 ACH create service | Task 4 |
| 2.5 ACH status service | Task 4 |
| 2.6 ACH controller | Task 4 |
| 3.1 RTGS DTOs | Task 7 |
| 3.2 RTGS validation | Task 7 |
| 3.3 RTGS simulator | Task 6 |
| 3.4 RTGS create service | Task 8 |
| 3.5 RTGS status service | Task 8 |
| 3.6 RTGS controller | Task 8 |
| 4.1 scopes | Task 9 |
| 4.2 idempotency required | Task 4, Task 8, Task 9 |
| 4.3 replay/conflict | Task 4, Task 8 |
| 4.4 correlation | Task 4, Task 8, Task 9 |
| 4.5 errors | Task 3, Task 7, Task 9 |
| 4.6 masking/logging | Task 10 |
| 5.1-5.2 OpenAPI | Task 11 |
| 5.3-5.4 Postman | Task 12 |
| 5.5 README | Task 13 |
| 5.6 developer docs | Task 13 |
| 6.1-6.6 tests | Task 1 through Task 13 |
| 6.7-6.9 validation | Task 14 |

## File Structure

Create or modify these files.

Domain:
- `src/main/java/com/cib/payment/api/domain/model/AchBatchId.java`
- `src/main/java/com/cib/payment/api/domain/model/AchEntryId.java`
- `src/main/java/com/cib/payment/api/domain/model/AchBatchStatus.java`
- `src/main/java/com/cib/payment/api/domain/model/AchEntryStatus.java`
- `src/main/java/com/cib/payment/api/domain/model/AchBatchEntry.java`
- `src/main/java/com/cib/payment/api/domain/model/AchBatchRecord.java`
- `src/main/java/com/cib/payment/api/domain/model/RtgsPaymentId.java`
- `src/main/java/com/cib/payment/api/domain/model/RtgsPaymentStatus.java`
- `src/main/java/com/cib/payment/api/domain/model/RtgsClientSegment.java`
- `src/main/java/com/cib/payment/api/domain/model/RtgsPaymentRecord.java`

Application ports:
- `src/main/java/com/cib/payment/api/application/port/AchBatchRepository.java`
- `src/main/java/com/cib/payment/api/application/port/AchDirectCreditSimulator.java`
- `src/main/java/com/cib/payment/api/application/port/AchDirectCreditOutcome.java`
- `src/main/java/com/cib/payment/api/application/port/RtgsPaymentRepository.java`
- `src/main/java/com/cib/payment/api/application/port/RtgsPaymentSimulator.java`
- `src/main/java/com/cib/payment/api/application/port/RtgsPaymentOutcome.java`

Application services:
- `src/main/java/com/cib/payment/api/application/service/CreateAchBatchService.java`
- `src/main/java/com/cib/payment/api/application/service/GetAchBatchStatusService.java`
- `src/main/java/com/cib/payment/api/application/service/CreateRtgsPaymentService.java`
- `src/main/java/com/cib/payment/api/application/service/GetRtgsPaymentStatusService.java`
- Modify `src/main/java/com/cib/payment/api/application/service/RequestFingerprintService.java` only if existing generic fingerprinting cannot serialize the new request records deterministically.

Infrastructure:
- `src/main/java/com/cib/payment/api/infrastructure/persistence/InMemoryAchBatchRepository.java`
- `src/main/java/com/cib/payment/api/infrastructure/persistence/InMemoryRtgsPaymentRepository.java`
- `src/main/java/com/cib/payment/api/infrastructure/simulator/DeterministicAchDirectCreditSimulator.java`
- `src/main/java/com/cib/payment/api/infrastructure/simulator/DeterministicRtgsPaymentSimulator.java`

API:
- `src/main/java/com/cib/payment/api/api/controller/AchBatchController.java`
- `src/main/java/com/cib/payment/api/api/controller/RtgsPaymentController.java`
- DTOs under `src/main/java/com/cib/payment/api/api/dto/` for ACH and RTGS request/response shapes.
- Modify security/scope configuration in `src/main/java/com/cib/payment/api/infrastructure/security/SecurityConfig.java` if scopes are enforced there.
- Modify observability interface/implementation only where needed to record ACH/RTGS events without logging sensitive payloads.

Artifacts:
- `src/main/resources/openapi/domestic-payment-api.yaml`
- `postman/domestic-rtp-payment-api.postman_collection.json`
- `postman/domestic-rtp-payment-api.local.postman_environment.json`
- `README.md`
- `docs/developer-support/classic-payment-rail-simulation.md`

Tests:
- Add focused tests under existing package patterns in `src/test/java/com/cib/payment/api/...`.
- Extend `OpenApiContractTest`, `PostmanArtifactValidationTest`, and logging/security tests where appropriate.

---

### Task 1: ACH Domain And Port Contracts

**Files:**
- Create domain and port files listed above for ACH.
- Test: `src/test/java/com/cib/payment/api/domain/model/AchDomainModelTest.java`

- [x] **Step 1: Write ACH domain tests**

Add tests that assert:
- `AchBatchStatus` contains `ACCEPTED_FOR_CLEARING`, `SETTLEMENT_PENDING`, `SETTLED`, `PARTIALLY_RETURNED`, `REJECTED`.
- `AchEntryStatus` contains `ACCEPTED`, `SETTLED`, `RETURNED`, `REJECTED`.
- `AchBatchRecord` preserves client ID, correlation ID, derived entry count, and total amount.

Run:

```powershell
mvn -Dtest=AchDomainModelTest test
```

Expected: fail because types do not exist.

- [x] **Step 2: Add minimal ACH domain types and ports**

Implement records/enums with immutable fields. Use existing value objects where possible:
- `Money`
- `AccountReference`
- `PaymentReason`
- `CorrelationId`

Repository port must support:
- `save(AchBatchRecord record)`
- `find(AchBatchId batchId)`

Simulator port must support:
- `process(AchBatchRecord acceptedRecord, AuthorizationContext authorizationContext, String scenario)`

- [x] **Step 3: Run ACH domain tests**

```powershell
mvn -Dtest=AchDomainModelTest test
```

Expected: pass.

### Task 2: ACH Repository And Simulator

**Files:**
- Create `InMemoryAchBatchRepository.java`
- Create `DeterministicAchDirectCreditSimulator.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/simulator/DeterministicAchDirectCreditSimulatorTest.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/persistence/AchInMemoryRepositoryTest.java`

- [x] **Step 1: Write simulator and repository tests**

Cover:
- `ach_direct_credit_accepted` maps batch to `ACCEPTED_FOR_CLEARING`, entries to `ACCEPTED`.
- `ach_direct_credit_settled` maps batch and entries to settled.
- `ach_direct_credit_settlement_pending` maps batch to `SETTLEMENT_PENDING`.
- `ach_direct_credit_partially_returned` marks at least one entry `RETURNED`.
- `ach_direct_credit_rejected` maps batch to `REJECTED`.
- Unknown scenario throws `ValidationFailureException`.
- Repository owner lookup preserves records and returns empty for unknown IDs.

- [x] **Step 2: Implement repository and simulator**

Use `ConcurrentHashMap<AchBatchId, AchBatchRecord>` in repository. Keep scenario mapping deterministic and independent of real dates/calendars.

- [x] **Step 3: Run tests**

```powershell
mvn -Dtest=DeterministicAchDirectCreditSimulatorTest,AchInMemoryRepositoryTest test
```

Expected: pass.

### Task 3: ACH DTOs And Validation

**Files:**
- Create ACH DTOs in `src/main/java/com/cib/payment/api/api/dto/`
- Test: `src/test/java/com/cib/payment/api/api/dto/CreateAchBatchRequestValidationTest.java`

- [ ] **Step 1: Write request validation tests**

Cover:
- valid Direct Credit batch passes.
- empty entries fail.
- missing `batchReference`, `originatorName`, `effectiveEntryDate`, `settlementAccount`, or entry fields fail.
- duplicate `entryReference` fails in service-level validation.
- non-USD entry amount fails.
- debit/unsupported payment type fails if a type field is included.

- [ ] **Step 2: Implement DTO records**

Use `@JsonIgnoreProperties(ignoreUnknown = false)`, `@Valid`, `@NotNull`, and `@NotBlank` consistently with existing DTOs. Reuse `MoneyRequest` and `AccountReferenceRequest`.

Suggested DTO names:
- `CreateAchBatchRequest`
- `AchBatchEntryRequest`
- `AchBatchResponse`
- `AchBatchStatusResponse`
- `AchBatchEntryStatusResponse`
- `AchBatchLinksResponse` or reuse `PaymentLinksResponse`

- [ ] **Step 3: Run validation tests**

```powershell
mvn -Dtest=CreateAchBatchRequestValidationTest test
```

Expected: pass.

### Task 4: ACH Services And Controller

**Files:**
- Create `CreateAchBatchService.java`
- Create `GetAchBatchStatusService.java`
- Create `AchBatchController.java`
- Test: `src/test/java/com/cib/payment/api/api/AchBatchControllerIntegrationTest.java`
- Test: `src/test/java/com/cib/payment/api/application/service/CreateAchBatchServiceTest.java`

- [ ] **Step 1: Write ACH service/integration tests**

Cover:
- `POST /v1/ach-batches` returns `202 Accepted`.
- missing `Idempotency-Key` returns validation error.
- same key and same body replays original response.
- same key and different body returns `409 Conflict`.
- `GET /v1/ach-batches/{batchId}` returns owner status.
- unrelated client cannot query the batch.
- correlation ID appears in response body/header and stored record.
- `ach_direct_credit_partially_returned` returns batch and entry statuses.

- [ ] **Step 2: Implement create/status services**

Follow `CreateFiPaymentService` for idempotency replay using stored original response JSON. Include `mockScenario` in fingerprint context so scenario changes conflict for the same key. Store accepted response JSON in `IdempotencyRecord` for replay.

- [ ] **Step 3: Implement controller**

Controller maps:
- `POST /v1/ach-batches`
- `GET /v1/ach-batches/{batchId}`

Use `AuthorizationContextService.from(jwt, servletRequest)`, `X-Mock-Scenario`, and `Idempotency-Key`.

- [ ] **Step 4: Run ACH tests**

```powershell
mvn -Dtest=AchBatchControllerIntegrationTest,CreateAchBatchServiceTest test
```

Expected: pass.

### Task 5: RTGS Domain And Port Contracts

**Files:**
- Create RTGS domain and port files listed above.
- Test: `src/test/java/com/cib/payment/api/domain/model/RtgsDomainModelTest.java`

- [ ] **Step 1: Write RTGS domain tests**

Cover:
- statuses `ACCEPTED_FOR_SETTLEMENT`, `QUEUED_FOR_LIQUIDITY`, `SETTLED`, `REJECTED`.
- segments `CORPORATE`, `FI`.
- `settlementFinality` is true only for settled outcome records.

- [ ] **Step 2: Add RTGS domain types and ports**

Use `PaymentReason`, `Money`, `AccountReference`, `CorrelationId`. Keep FI BICs as strings or a small `FiParty` reuse where it fits without coupling RTGS to correspondent settlement context.

- [ ] **Step 3: Run RTGS domain tests**

```powershell
mvn -Dtest=RtgsDomainModelTest test
```

Expected: pass.

### Task 6: RTGS Repository And Simulator

**Files:**
- Create `InMemoryRtgsPaymentRepository.java`
- Create `DeterministicRtgsPaymentSimulator.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/simulator/DeterministicRtgsPaymentSimulatorTest.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/persistence/RtgsInMemoryRepositoryTest.java`

- [ ] **Step 1: Write simulator and repository tests**

Cover:
- `rtgs_settled` maps to `SETTLED` and `settlementFinality=true`.
- `rtgs_queued_for_liquidity` maps to `QUEUED_FOR_LIQUIDITY`, `settlementFinality=false`, reason code present.
- `rtgs_rejected` maps to `REJECTED`, `settlementFinality=false`, reason code present.
- Unknown scenario throws `ValidationFailureException`.
- Repository stores and finds by payment ID.

- [ ] **Step 2: Implement simulator and repository**

Keep outcomes deterministic. Do not calculate real liquidity or queue ordering.

- [ ] **Step 3: Run tests**

```powershell
mvn -Dtest=DeterministicRtgsPaymentSimulatorTest,RtgsInMemoryRepositoryTest test
```

Expected: pass.

### Task 7: RTGS DTOs And Validation

**Files:**
- Create RTGS DTOs under `src/main/java/com/cib/payment/api/api/dto/`
- Test: `src/test/java/com/cib/payment/api/api/dto/CreateRtgsPaymentRequestValidationTest.java`

- [ ] **Step 1: Write request validation tests**

Cover:
- corporate request requires debtor and creditor account references.
- FI request requires `instructingAgentBic` and `instructedAgentBic`.
- unsupported `clientSegment` fails.
- non-USD amount fails.
- valid positive USD amount is not rejected for being below a high-value threshold.

- [ ] **Step 2: Implement DTO records**

Suggested DTOs:
- `CreateRtgsPaymentRequest`
- `RtgsPaymentResponse`
- `RtgsPaymentStatusResponse`
- `RtgsRelatedCapabilityResponse`

Use a single request DTO with nullable segment-specific fields and service-level validation for segment rules.

- [ ] **Step 3: Run validation tests**

```powershell
mvn -Dtest=CreateRtgsPaymentRequestValidationTest test
```

Expected: pass.

### Task 8: RTGS Services And Controller

**Files:**
- Create `CreateRtgsPaymentService.java`
- Create `GetRtgsPaymentStatusService.java`
- Create `RtgsPaymentController.java`
- Test: `src/test/java/com/cib/payment/api/api/RtgsPaymentControllerIntegrationTest.java`
- Test: `src/test/java/com/cib/payment/api/application/service/CreateRtgsPaymentServiceTest.java`

- [ ] **Step 1: Write RTGS service/integration tests**

Cover:
- corporate settled request.
- FI settled request.
- FI queued-for-liquidity request.
- rejected request.
- missing idempotency key.
- idempotent replay/conflict.
- owner-only status query.
- response includes `settlementFinality`.
- FI RTGS does not create FI correspondent payment or invoke FI correspondent simulator.

- [ ] **Step 2: Implement services**

Follow ACH idempotency pattern. Include `mockScenario` in fingerprint context. Add related capability guidance for FI responses only if it stays static and does not imply runtime handoff.

- [ ] **Step 3: Implement controller**

Map:
- `POST /v1/rtgs-payments`
- `GET /v1/rtgs-payments/{paymentId}`

Return JSON. Creation should return `202 Accepted`.

- [ ] **Step 4: Run RTGS tests**

```powershell
mvn -Dtest=RtgsPaymentControllerIntegrationTest,CreateRtgsPaymentServiceTest test
```

Expected: pass.

### Task 9: Security, Error, Correlation, And Observability Integration

**Files:**
- Modify `SecurityConfig.java` if endpoint-scope mapping is centralized there.
- Modify `PaymentObservability.java` and `MicrometerPaymentObservability.java` only if new observability methods are needed.
- Tests: existing security/auth/error/correlation packages plus ACH/RTGS integration tests.

- [ ] **Step 1: Add scope tests**

Cover missing and valid scopes for all four new operations:
- `ach-batches:create`
- `ach-batches:read`
- `rtgs-payments:create`
- `rtgs-payments:read`

- [ ] **Step 2: Wire security and consistent errors**

Use existing exception types:
- `ValidationFailureException`
- `AuthorizationScopeException`
- `PaymentNotFoundException` or new rail-specific not-found exception only if needed.
- `IdempotencyConflictException`

- [ ] **Step 3: Run auth/error/correlation tests**

```powershell
mvn -Dtest=SecurityIntegrationTest,CorrelationAndErrorHandlingTest,AchBatchControllerIntegrationTest,RtgsPaymentControllerIntegrationTest test
```

Expected: pass.

### Task 10: Sensitive Logging

**Files:**
- Modify logging/observability code only where ACH/RTGS events are added.
- Tests: `src/test/java/com/cib/payment/api/api/AchSensitiveLoggingIntegrationTest.java`, `src/test/java/com/cib/payment/api/api/RtgsSensitiveLoggingIntegrationTest.java`

- [ ] **Step 1: Write sensitive logging tests**

Cover:
- ACH receiver and settlement account numbers do not appear in logs.
- RTGS debtor and creditor account numbers do not appear in logs.
- bearer token and raw JSON payload are not logged.

- [ ] **Step 2: Implement masking/omission**

Reuse `AccountNumberMasker` where possible. Prefer structured event fields over raw payload logging.

- [ ] **Step 3: Run logging tests**

```powershell
mvn -Dtest=AchSensitiveLoggingIntegrationTest,RtgsSensitiveLoggingIntegrationTest test
```

Expected: pass.

### Task 11: OpenAPI Contract

**Files:**
- Modify `src/main/resources/openapi/domestic-payment-api.yaml`
- Tests: `OpenApiContractTest`, `OpenApiResourceHttpTest`

- [ ] **Step 1: Add failing OpenAPI assertions**

Assert paths, scopes, schemas, examples, and error responses exist for ACH and RTGS.

- [ ] **Step 2: Update OpenAPI YAML**

Add:
- ACH create/status paths and schemas.
- RTGS create/status paths and schemas.
- security scope documentation.
- examples matching deterministic scenarios.
- simulator-only limitations.

- [ ] **Step 3: Run OpenAPI tests**

```powershell
mvn -Dtest=OpenApiContractTest,OpenApiResourceHttpTest test
```

Expected: pass.

### Task 12: Postman Artifacts

**Files:**
- Modify `postman/domestic-rtp-payment-api.postman_collection.json`
- Modify `postman/domestic-rtp-payment-api.local.postman_environment.json`
- Test: `src/test/java/com/cib/payment/api/developer/PostmanArtifactValidationTest.java`

- [ ] **Step 1: Add failing Postman validation assertions**

Assert folder/request coverage:
- RTP Baseline
- ACH Direct Credit settled/status/partially returned/rejected
- Corporate RTGS settled
- FI RTGS settled
- FI RTGS queued
- RTGS rejected/status
- FI Correspondent Comparison

- [ ] **Step 2: Update Postman collection and environment**

Add variables:
- `achBatchId`
- `achIdempotencyKey`
- `rtgsPaymentId`
- `rtgsIdempotencyKey`
- `railMockScenario`

Include expected-result documentation for each manual scenario.

- [ ] **Step 3: Run Postman artifact test**

```powershell
mvn -Dtest=PostmanArtifactValidationTest test
```

Expected: pass.

### Task 13: README And Developer Guide

**Files:**
- Modify `README.md`
- Create `docs/developer-support/classic-payment-rail-simulation.md`
- Test: extend developer artifact validation where practical.

- [ ] **Step 1: Add documentation validation assertions**

Assert README and developer guide mention:
- RTP baseline
- ACH Direct Credit batch simulation
- RTGS payment simulation
- FI correspondent as separate arrangement
- cross-border and AI copilot as future-facing

- [ ] **Step 2: Update README and guide**

Guide must include:
- rail comparison table
- scenario catalog
- expected statuses
- Postman guidance
- common setup mistakes
- future copilot hook without runtime claims.

- [ ] **Step 3: Run artifact tests**

```powershell
mvn -Dtest=PostmanArtifactValidationTest,OpenApiContractTest test
```

Expected: pass.

### Task 14: Final Verification

**Files:**
- All changed files.

- [ ] **Step 1: Validate OpenSpec**

```powershell
npx.cmd openspec validate add-classic-payment-rail-simulation
```

Expected:

```text
Change 'add-classic-payment-rail-simulation' is valid
```

- [ ] **Step 2: Run focused suite**

```powershell
mvn -Dtest=AchDomainModelTest,DeterministicAchDirectCreditSimulatorTest,AchInMemoryRepositoryTest,CreateAchBatchRequestValidationTest,CreateAchBatchServiceTest,AchBatchControllerIntegrationTest,RtgsDomainModelTest,DeterministicRtgsPaymentSimulatorTest,RtgsInMemoryRepositoryTest,CreateRtgsPaymentRequestValidationTest,CreateRtgsPaymentServiceTest,RtgsPaymentControllerIntegrationTest,OpenApiContractTest,PostmanArtifactValidationTest test
```

Expected: all tests pass.

- [ ] **Step 3: Run full suite**

```powershell
mvn test
```

Expected: all tests pass.

- [ ] **Step 4: Inspect git status**

```powershell
git status -sb
```

Expected: only intended OpenSpec, source, tests, OpenAPI, Postman, README, and developer doc changes are present.

## Self-Review

- Spec coverage: All OpenSpec task groups map to plan tasks above.
- Placeholder scan: No unresolved marker text or deferred implementation behavior is intentionally left in this plan.
- Type consistency: ACH and RTGS IDs/statuses/repositories/services/controllers use consistent names across tasks.
- Scope guard: No task implements NACHA, ACH debit, ISO runtime for ACH/RTGS, cross-border, AI recommendation, or RTGS-to-FI correspondent orchestration.
