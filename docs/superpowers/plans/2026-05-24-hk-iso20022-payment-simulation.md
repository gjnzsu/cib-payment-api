# HK ISO 20022 Payment Simulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace custom JSON payment initiation with an ISO-native HK domestic realtime payment simulation: `pain.001.001.09` in, internal `pacs.008`, HK FPS-style simulator, and `pain.002.001.10` out.

**Architecture:** Keep the Edge API as the external channel facade for auth, admission validation, idempotency links, correlation, and response mapping. Put payment record ownership, lifecycle state, internal `pacs.008` mapping, latest `pain.002`, and status truth behind in-process Payment Engine ports.

**Tech Stack:** Java 21, Spring Boot MVC/Security, Maven, MockMvc, JUnit 5, AssertJ, secure JDK XML parsers, OpenAPI YAML, Postman JSON artifacts, OpenSpec validation.

---

## File Structure

Create or modify these areas:

- `src/main/java/com/cib/payment/api/api/controller/DomesticPaymentController.java`: route ISO XML create/status requests and reject custom JSON initiation.
- `src/main/java/com/cib/payment/api/application/service/`: Edge admission/idempotency orchestration and existing auth/fingerprint services.
- `src/main/java/com/cib/payment/api/application/port/`: add `PaymentEngineInitiationPort`, `PaymentEngineStatusQueryPort`, `HkClearingSettlementSimulator`, and repository ports needed by the engine.
- `src/main/java/com/cib/payment/api/domain/model/`: add ISO candidate, beneficiary identifier, internal transfer, engine record, ISO report, and reason/status domain types.
- `src/main/java/com/cib/payment/api/infrastructure/iso/`: secure XML parsing and `pain.002` rendering adapters.
- `src/main/java/com/cib/payment/api/infrastructure/engine/`: in-memory engine repository and in-process engine implementation.
- `src/main/java/com/cib/payment/api/infrastructure/simulator/`: HK FPS-style deterministic simulator implementation.
- `src/test/resources/iso/`: synthetic `pain.001.001.09` and `pain.002.001.10` fixtures.
- `src/test/java/com/cib/payment/api/**`: unit, integration, contract, architecture, and artifact validation tests.
- `src/main/resources/openapi/domestic-payment-api.yaml`, `postman/*`, and developer docs: ISO-only examples and simulator scenarios.

---

### Task 1: Test Fixtures and ISO Contract Baseline

**Files:**
- Create: `src/test/resources/iso/pain001-success.xml`
- Create: `src/test/resources/iso/pain001-rejection.xml`
- Create: `src/test/resources/iso/pain001-suspicious.xml`
- Create: `src/test/resources/iso/pain001-pending.xml`
- Create: `src/test/resources/iso/pain001-timeout.xml`
- Create: `src/test/resources/iso/pain001-invalid-missing-creditor.xml`
- Create: `src/test/resources/iso/pain002-acsc.xml`
- Create: `src/test/resources/iso/pain002-rjct.xml`
- Create: `src/test/resources/iso/pain002-pdng.xml`
- Test: `src/test/java/com/cib/payment/api/infrastructure/iso/IsoFixtureValidationTest.java`

- [x] **Step 1: Add minimal synthetic XML fixtures**

  Use `pain.001.001.09` namespace for initiation fixtures and `pain.002.001.10` namespace for status report fixtures. Use synthetic identifiers only:
  - `MsgId`: `MSG-20260524-0001`
  - `EndToEndId`: `INV-2026-0001`
  - debtor/creditor names: `Acme Treasury HK`, `Supplier HK Limited`
  - account numbers: masked-looking synthetic values such as `000123456789`
  - currency: `HKD`

- [x] **Step 2: Write fixture smoke tests**

  Add tests that load every fixture from `src/test/resources/iso/`, assert it contains the expected namespace, and assert no fixture contains obvious real-looking HKID or phone values.

  Run: `mvn test -Dtest=IsoFixtureValidationTest`

  Expected: PASS after fixtures are added.

- [x] **Step 3: Commit fixture baseline**

  Commit message: `test: add iso20022 hk payment fixtures`

### Task 2: Secure XML Parsing and Admission Model

**Files:**
- Create: `src/main/java/com/cib/payment/api/domain/model/IsoPaymentCandidate.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/BeneficiaryIdentifier.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/iso/Pain001Parser.java`
- Create: `src/main/java/com/cib/payment/api/application/service/IsoPaymentAdmissionService.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/iso/Pain001ParserTest.java`
- Test: `src/test/java/com/cib/payment/api/application/service/IsoPaymentAdmissionServiceTest.java`

- [x] **Step 1: Write failing parser tests**

  Cover:
  - valid `pain001-success.xml` parses into debtor, creditor, HKD amount, `EndToEndId`, remittance, and optional purpose fields
  - malformed XML fails
  - XXE/document type declarations fail
  - wrong namespace/version fails

  Run: `mvn test -Dtest=Pain001ParserTest`

  Expected: FAIL because parser does not exist.

- [x] **Step 2: Implement secure parser**

  Use JDK XML parser settings that disallow DOCTYPE and external entities. Return a small parsed structure or throw existing validation/semantic exceptions; do not log raw XML.

- [x] **Step 3: Write failing admission tests**

  Cover:
  - missing creditor account/proxy returns validation/semantic failure
  - missing client-supplied `EndToEndId` or payment reference fails
  - non-HKD currency fails with semantic error
  - custom JSON content type is rejected before engine invocation

  Run: `mvn test -Dtest=IsoPaymentAdmissionServiceTest`

  Expected: FAIL until admission service exists.

- [x] **Step 4: Implement admission service**

  Keep this service before the Payment Engine boundary. It returns `IsoPaymentCandidate` only after all request admissibility checks pass.

- [x] **Step 5: Run focused tests**

  Run: `mvn test -Dtest=Pain001ParserTest,IsoPaymentAdmissionServiceTest`

  Expected: PASS.

- [x] **Step 6: Commit admission foundation**

  Commit message: `feat: add iso pain001 admission validation`

### Task 3: Payment Engine Ports and Record Ownership

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/port/PaymentEngineInitiationPort.java`
- Create: `src/main/java/com/cib/payment/api/application/port/PaymentEngineStatusQueryPort.java`
- Create: `src/main/java/com/cib/payment/api/application/port/PaymentEngineRecordRepository.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/EnginePaymentRecord.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/InternalInterbankTransfer.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/engine/InMemoryPaymentEngineRecordRepository.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/engine/HkPaymentEngine.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/engine/HkPaymentEngineTest.java`

- [x] **Step 1: Write failing engine ownership tests**

  Cover:
  - engine creates payment record after receiving admitted candidate
  - engine stores the latest `IsoPaymentStatusReport` for status query replay
  - status query port returns engine-owned record/report
  - Edge-facing service does not create engine records directly

  Run: `mvn test -Dtest=HkPaymentEngineTest`

  Expected: FAIL because engine ports do not exist.

- [x] **Step 2: Add engine ports and domain types**

  Define initiation input around `IsoPaymentCandidate`, `AuthorizationContext`, `CorrelationId`, idempotency context, and scenario. Define query by engine payment ID and client ownership.

- [x] **Step 3: Implement in-memory engine repository**

  Keep this repository under engine implementation. Do not reuse Edge `PaymentStatusRepository` for ISO-created payment truth.

- [x] **Step 4: Implement minimal engine record creation**

  Create record with internal status `PROCESSING` initially, store original ISO identifiers, and make it queryable.

- [x] **Step 5: Run focused tests**

  Run: `mvn test -Dtest=HkPaymentEngineTest`

  Expected: PASS.

- [x] **Step 6: Commit engine boundary**

  Commit message: `feat: add payment engine boundary`

### Task 4: Internal pacs.008 Mapping and HK Simulator

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/port/HkClearingSettlementSimulator.java`
- Create: `src/main/java/com/cib/payment/api/application/port/HkClearingSettlementOutcome.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/simulator/DeterministicHkClearingSettlementSimulator.java`
- Modify: `src/main/java/com/cib/payment/api/infrastructure/engine/HkPaymentEngine.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/simulator/DeterministicHkClearingSettlementSimulatorTest.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/engine/HkPaymentEngineMappingTest.java`

- [ ] **Step 1: Write failing simulator tests**

  Cover `success`, `rejection`, `suspicious_proxy_or_account`, `pending`, `timeout`, `internal_failure`, unknown participant, and non-HKD rejection.

  Run: `mvn test -Dtest=DeterministicHkClearingSettlementSimulatorTest`

  Expected: FAIL.

- [ ] **Step 2: Implement deterministic simulator**

  Return structured outcomes that the engine can map to internal status and ISO report status. Keep scenario controls local/test-only.

- [ ] **Step 3: Write failing mapping tests**

  Verify admitted `pain.001` candidate maps to an internal `pacs.008` representation and preserves `EndToEndId`, debtor, creditor, amount, participant routing, and correlation.

- [ ] **Step 4: Implement internal mapping in engine**

  Keep `pacs.008` internal. Do not expose it in controller responses or OpenAPI as external input.

- [ ] **Step 5: Run focused tests**

  Run: `mvn test -Dtest=DeterministicHkClearingSettlementSimulatorTest,HkPaymentEngineMappingTest`

  Expected: PASS.

- [ ] **Step 6: Commit simulator and mapping**

  Commit message: `feat: simulate hk clearing settlement`

### Task 5: pain.002 Generation and Status Mapping

**Files:**
- Create: `src/main/java/com/cib/payment/api/domain/model/IsoPaymentStatusReport.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/iso/Pain002Renderer.java`
- Modify: `src/main/java/com/cib/payment/api/infrastructure/engine/HkPaymentEngine.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/iso/Pain002RendererTest.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/engine/HkPaymentEngineStatusReportTest.java`

- [ ] **Step 1: Write failing pain.002 renderer tests**

  Cover:
  - `COMPLETED` -> `ACSC`
  - `REJECTED` -> `RJCT` with reason
  - `PROCESSING` -> `PDNG` with processing reason
  - `TIMEOUT` -> `PDNG` with timeout/operational reason
  - original `EndToEndId` appears in the report

  Run: `mvn test -Dtest=Pain002RendererTest`

  Expected: FAIL.

- [ ] **Step 2: Implement pain.002 renderer**

  Generate `pain.002.001.10` XML from an `IsoPaymentStatusReport`. Keep XML generation deterministic for tests.

- [ ] **Step 3: Connect engine outcome to latest report**

  After simulator processing, update engine record with internal status and latest `IsoPaymentStatusReport`.

- [ ] **Step 4: Run focused tests**

  Run: `mvn test -Dtest=Pain002RendererTest,HkPaymentEngineStatusReportTest`

  Expected: PASS.

- [ ] **Step 5: Commit status reporting**

  Commit message: `feat: generate pain002 status reports`

### Task 6: Edge API XML Integration and Idempotency Links

**Files:**
- Modify: `src/main/java/com/cib/payment/api/api/controller/DomesticPaymentController.java`
- Create: `src/main/java/com/cib/payment/api/application/service/CreateIsoDomesticPaymentService.java`
- Create or modify: Edge idempotency record/domain/repository classes as needed
- Test: `src/test/java/com/cib/payment/api/api/CreateIsoDomesticPaymentIntegrationTest.java`
- Test: `src/test/java/com/cib/payment/api/api/GetIsoPaymentStatusIntegrationTest.java`

- [ ] **Step 1: Write failing create integration tests**

  Cover:
  - `POST` `pain001-success.xml` returns `200` and `pain.002` containing `ACSC`
  - `rejection` returns `200` and `RJCT`
  - `pending`/`timeout` returns `200` and `PDNG`
  - malformed XML returns `400`
  - non-HKD/profile failure returns `422`
  - custom JSON initiation is rejected

  Run: `mvn test -Dtest=CreateIsoDomesticPaymentIntegrationTest`

  Expected: FAIL.

- [ ] **Step 2: Implement XML controller path**

  Keep controller limited to headers, content negotiation, request body forwarding, and response mapping. Delegate admission/idempotency/engine orchestration to application service.

- [ ] **Step 3: Implement Edge idempotency link**

  Store client ID, idempotency key, normalized fingerprint, original `pain.002` response, engine payment ID, correlation ID, and timestamps. Replay same semantics with original response; reject changed semantics with `409`.

- [ ] **Step 4: Write and implement status query tests**

  Verify `GET /v1/domestic-payments/{paymentId}` calls engine status query port and returns latest `pain.002`.

- [ ] **Step 5: Run focused tests**

  Run: `mvn test -Dtest=CreateIsoDomesticPaymentIntegrationTest,GetIsoPaymentStatusIntegrationTest`

  Expected: PASS.

- [ ] **Step 6: Commit API integration**

  Commit message: `feat: expose iso native payment api`

### Task 7: Security, Masking, and Architecture Tests

**Files:**
- Modify: `src/main/java/com/cib/payment/api/infrastructure/observability/AccountNumberMasker.java`
- Modify or create observability helpers as needed
- Modify: `pom.xml` if using ArchUnit
- Create: `src/test/java/com/cib/payment/api/architecture/EdgeEngineBoundaryTest.java`
- Test: `src/test/java/com/cib/payment/api/api/IsoSensitiveLoggingIntegrationTest.java`

- [ ] **Step 1: Decide architecture test mechanism**

  Prefer adding ArchUnit test dependency if acceptable. Otherwise implement focused package dependency tests using classpath scanning.

- [ ] **Step 2: Write failing architecture tests**

  Enforce:
  - controllers do not access repositories
  - Edge application services do not access engine repositories
  - Engine implementation does not depend on API DTO/controller classes
  - `pacs.008` internal representation is not returned by API controllers

- [ ] **Step 3: Extend masking**

  Mask account numbers, FPS proxy values, email/mobile proxy identifiers, HKID-like identifiers, and XML-derived identifiers at log/error boundaries.

- [ ] **Step 4: Run focused tests**

  Run: `mvn test -Dtest=EdgeEngineBoundaryTest,IsoSensitiveLoggingIntegrationTest`

  Expected: PASS.

- [ ] **Step 5: Commit boundary and masking tests**

  Commit message: `test: enforce edge engine boundary`

### Task 8: Developer Support and Documentation

**Files:**
- Modify: `src/main/resources/openapi/domestic-payment-api.yaml`
- Modify: `postman/domestic-rtp-payment-api.postman_collection.json`
- Modify: `postman/domestic-rtp-payment-api.local.postman_environment.json`
- Modify: `README.md`
- Modify: `docs/developer-support/postman-local-testing.md`
- Test: `src/test/java/com/cib/payment/api/api/OpenApiContractTest.java`
- Test: `src/test/java/com/cib/payment/api/developer/PostmanArtifactValidationTest.java`

- [ ] **Step 1: Update OpenAPI tests first**

  Expect OpenAPI to document:
  - `pain.001.001.09` request XML
  - `pain.002.001.10` response/status XML
  - no custom JSON payment initiation request schema
  - `ACSC`, `RJCT`, `PDNG` examples

- [ ] **Step 2: Update OpenAPI YAML**

  Replace JSON payment creation examples with XML examples and retain JSON error response schema for admission failures.

- [ ] **Step 3: Update Postman artifacts**

  Add saved requests for success, rejection, suspicious proxy/account, pending, timeout, malformed XML, non-HKD/profile failure, replay, conflict, and status query.

- [ ] **Step 4: Update README and local testing docs**

  Explain ISO-native request/response, simulator-only scope, no real HKICL/FPS connectivity, and internal-only `pacs.008`.

- [ ] **Step 5: Run artifact tests**

  Run: `mvn test -Dtest=OpenApiContractTest,PostmanArtifactValidationTest`

  Expected: PASS.

- [ ] **Step 6: Commit developer support**

  Commit message: `docs: update iso20022 developer artifacts`

### Task 9: Full Verification and OpenSpec Closure

**Files:**
- Verify all changed source, test, OpenAPI, Postman, docs, and OpenSpec artifacts.

- [ ] **Step 1: Run focused ISO suite**

  Run: `mvn test -Dtest=*Iso*,*HkPaymentEngine*,*HkClearing*,*Pain*`

  Expected: PASS.

- [ ] **Step 2: Run full test suite**

  Run: `mvn test`

  Expected: PASS.

- [ ] **Step 3: Run OpenSpec validation**

  Run: `npx.cmd openspec validate add-hk-iso20022-payment-simulation`

  Expected: `Change 'add-hk-iso20022-payment-simulation' is valid`

- [ ] **Step 4: Review sensitive data leakage**

  Search logs, tests, fixtures, Postman, and docs for raw real-looking account/proxy/HKID values. Replace with synthetic examples if needed.

- [ ] **Step 5: Review accidental external pacs.008 exposure**

  Search OpenAPI, controllers, DTOs, and Postman for externally exposed `pacs.008` input. It must remain internal-only.

- [ ] **Step 6: Final git review**

  Run: `git status --short` and `git diff --stat`

  Expected: only intended feature, tests, docs, and OpenSpec files are changed.
