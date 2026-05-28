# FI Correspondent RFI Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a USD-only FI correspondent payment simulation with `pacs.009` initiation, simulator-derived nostro/vostro/loro settlement context, FI-specific scopes, JSON FI status responses, and a deterministic `camt.056` to `camt.029` recall/cancellation investigation workflow.

**Architecture:** Add a distinct FI payment slice beside the existing domestic ISO payment flow. Keep controllers thin, put orchestration in application services, keep FI domain records in domain models, implement XML parsing/rendering and deterministic simulator behavior in infrastructure adapters, and reuse existing auth, idempotency, correlation, error, observability, OpenAPI, and Postman patterns.

**Tech Stack:** Java 21, Spring Boot MVC/Security, Maven, JUnit 5, AssertJ, MockMvc, secure JDK XML parsers, in-memory repositories, OpenAPI YAML, Postman JSON artifacts, OpenSpec validation.

---

## OpenSpec Traceability Matrix

| OpenSpec task | Plan task(s) |
| --- | --- |
| 1.1-1.5 FI fixtures and route matrix | Task 1 |
| 2.1-2.5 FI domain and repository boundaries | Task 2 |
| 3.1-3.6 `pacs.009` admission and FI payment simulation | Tasks 3, 4 |
| 4.1-4.3 FI payment status query | Task 5 |
| 5.1-5.6 `camt.056` recall and `camt.029` resolution | Tasks 6, 7 |
| 6.1-6.8 Security, idempotency, correlation, masking | Task 8 |
| 7.1-7.7 FI API integration | Task 9 |
| 8.1-8.5 Developer support and product docs | Task 10 |
| 9.1-9.5 Verification and alignment | Task 11 |

## File Structure

Create or modify these areas:

- `src/test/resources/fi/`: synthetic `pacs.009`, `camt.056`, and `camt.029` fixtures.
- `src/main/java/com/cib/payment/api/domain/model/`: FI payment, correspondent context, account role, recall/investigation, and status domain records.
- `src/main/java/com/cib/payment/api/application/port/`: FI parser, renderer, simulator, repository, and query/initiation ports.
- `src/main/java/com/cib/payment/api/application/service/`: FI admission, create, status, recall, and fingerprint orchestration services.
- `src/main/java/com/cib/payment/api/infrastructure/iso/`: secure `pacs.009` and `camt.056` parsers plus `camt.029` renderer.
- `src/main/java/com/cib/payment/api/infrastructure/simulator/`: deterministic FI correspondent payment and recall simulator.
- `src/main/java/com/cib/payment/api/infrastructure/persistence/`: in-memory FI payment and recall repositories.
- `src/main/java/com/cib/payment/api/api/controller/FiPaymentController.java`: new FI payment API surface.
- `src/main/java/com/cib/payment/api/api/dto/`: JSON acknowledgement/status DTOs for FI payments.
- `src/main/java/com/cib/payment/api/infrastructure/security/`: FI scope authorization and local token generation support.
- `src/main/java/com/cib/payment/api/infrastructure/observability/`: FI masking and observability additions.
- `src/main/resources/openapi/domestic-payment-api.yaml`, `postman/*`, `README.md`, and `docs/developer-support/*`: developer artifacts.
- `src/test/java/com/cib/payment/api/**`: parser, renderer, service, simulator, integration, architecture, security, logging, OpenAPI, and Postman tests.

## Task 1: FI ISO Fixtures and Route Profile Matrix

**Files:**
- Create: `src/test/resources/fi/pacs009-accepted-nostro.xml`
- Create: `src/test/resources/fi/pacs009-pending-vostro.xml`
- Create: `src/test/resources/fi/pacs009-rejected-loro.xml`
- Create: `src/test/resources/fi/pacs009-non-usd.xml`
- Create: `src/test/resources/fi/pacs009-missing-instructed-agent.xml`
- Create: `src/test/resources/fi/pacs009-unsafe.xml`
- Create: `src/test/resources/fi/camt056-recall-accepted.xml`
- Create: `src/test/resources/fi/camt056-recall-rejected.xml`
- Create: `src/test/resources/fi/camt056-investigation-pending.xml`
- Create: `src/test/resources/fi/camt056-wrong-original-reference.xml`
- Create: `src/test/resources/fi/camt056-malformed.xml`
- Create: `src/test/resources/fi/camt056-unsafe.xml`
- Create: `src/test/resources/fi/camt029-accepted.xml`
- Create: `src/test/resources/fi/camt029-rejected.xml`
- Create: `src/test/resources/fi/camt029-pending.xml`
- Create: `src/test/java/com/cib/payment/api/infrastructure/iso/FiIsoFixtureValidationTest.java`

- [ ] **Step 1: Add fixture validation tests first**

Create `FiIsoFixtureValidationTest` with tests that load all `src/test/resources/fi/*.xml` resources, assert expected namespaces, assert valid `pacs.009` fixtures use `USD`, and assert fixtures avoid real-looking sensitive values.

Use this test shape:

```java
@Test
void fiFixturesUseExpectedNamespacesAndSyntheticData() {
    var fixtures = List.of(
            "fi/pacs009-accepted-nostro.xml",
            "fi/pacs009-pending-vostro.xml",
            "fi/pacs009-rejected-loro.xml",
            "fi/camt056-recall-accepted.xml",
            "fi/camt029-accepted.xml");

    for (var fixture : fixtures) {
        var xml = readFixture(fixture);
        assertThat(xml).contains("urn:iso:std:iso:20022:tech:xsd:");
        assertThat(xml).doesNotContain("John Doe", "Jane Doe", "1234567890123456");
    }
}
```

- [ ] **Step 2: Run fixture validation test to verify it fails**

Run: `mvn test -Dtest=FiIsoFixtureValidationTest`

Expected: FAIL because fixtures and test class do not exist yet.

- [ ] **Step 3: Add minimal synthetic fixtures**

Use fixed synthetic identifiers:

- FI client: `FICLIENT01`
- instructing agent BIC: `CIBBHKHH`
- instructed/correspondent BICs: `CORRUS33`, `VOSTUS33`, `LOROUS33`
- original payment reference: `FI-E2E-20260528-0001`
- settlement amount: `100000.00`
- settlement currency: `USD`

Use message namespaces:

- `pacs.009`: `urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08`
- `camt.056`: `urn:iso:std:iso:20022:tech:xsd:camt.056.001.08`
- `camt.029`: `urn:iso:std:iso:20022:tech:xsd:camt.029.001.09`

- [ ] **Step 4: Run fixture validation test**

Run: `mvn test -Dtest=FiIsoFixtureValidationTest`

Expected: PASS.

- [ ] **Step 5: Commit fixture baseline**

```powershell
git add src/test/resources/fi src/test/java/com/cib/payment/api/infrastructure/iso/FiIsoFixtureValidationTest.java
git commit -m "test: add fi iso fixtures"
```

## Task 2: FI Domain Models and Repository Ports

**Files:**
- Create: `src/main/java/com/cib/payment/api/domain/model/FiPaymentId.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/FiPaymentStatus.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/FiParty.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/FiPaymentIdentifiers.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/AccountRelationshipRole.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/CorrespondentSettlementContext.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/FiPaymentCandidate.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/FiPaymentRecord.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/RecallInvestigationId.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/RecallInvestigationStatus.java`
- Create: `src/main/java/com/cib/payment/api/domain/model/RecallInvestigationRecord.java`
- Create: `src/main/java/com/cib/payment/api/application/port/FiPaymentRepository.java`
- Create: `src/main/java/com/cib/payment/api/application/port/RecallInvestigationRepository.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/persistence/InMemoryFiPaymentRepository.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/persistence/InMemoryRecallInvestigationRepository.java`
- Create: `src/test/java/com/cib/payment/api/domain/model/FiDomainModelTest.java`
- Create: `src/test/java/com/cib/payment/api/infrastructure/persistence/FiInMemoryRepositoryTest.java`

- [ ] **Step 1: Write FI domain model tests**

Add tests for immutable value objects and repository ownership lookup:

```java
@Test
void fiPaymentRecordKeepsOwnerAndCorrespondentContext() {
    var paymentId = new FiPaymentId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    var context = new CorrespondentSettlementContext(
            "CIBBHKHH", "CORRUS33", null, "USD", AccountRelationshipRole.NOSTRO, "nostro-usd-corrus33-****1234");

    var record = FiPaymentRecord.create(
            paymentId,
            "client-fi-a",
            new FiPaymentIdentifiers("MSG-FI-0001", "INSTR-FI-0001", "FI-E2E-20260528-0001"),
            new FiParty("CIBBHKHH"),
            new FiParty("CORRUS33"),
            "100000.00",
            "USD",
            FiPaymentStatus.SETTLED,
            context,
            new CorrelationId("corr-fi-001"),
            Instant.parse("2026-05-28T10:00:00Z"));

    assertThat(record.ownerClientId()).isEqualTo("client-fi-a");
    assertThat(record.settlementContext().accountRole()).isEqualTo(AccountRelationshipRole.NOSTRO);
}
```

- [ ] **Step 2: Run FI domain tests to verify they fail**

Run: `mvn test -Dtest=FiDomainModelTest,FiInMemoryRepositoryTest`

Expected: FAIL because FI domain and repository classes do not exist.

- [ ] **Step 3: Implement minimal FI domain records**

Use Java records for simple value objects and keep them in `domain.model`. Include static constructors only where they improve readability. Keep these classes free of Spring, HTTP, XML, and persistence details.

- [ ] **Step 4: Implement repository ports and in-memory adapters**

Define repository methods:

```java
Optional<FiPaymentRecord> findById(FiPaymentId paymentId);
FiPaymentRecord save(FiPaymentRecord record);

Optional<RecallInvestigationRecord> findByPaymentId(FiPaymentId paymentId);
RecallInvestigationRecord saveIfAbsent(RecallInvestigationRecord record);
```

Use `ConcurrentHashMap` in adapters.

- [ ] **Step 5: Run domain and repository tests**

Run: `mvn test -Dtest=FiDomainModelTest,FiInMemoryRepositoryTest`

Expected: PASS.

- [ ] **Step 6: Commit domain foundation**

```powershell
git add src/main/java/com/cib/payment/api/domain/model src/main/java/com/cib/payment/api/application/port src/main/java/com/cib/payment/api/infrastructure/persistence src/test/java/com/cib/payment/api/domain/model/FiDomainModelTest.java src/test/java/com/cib/payment/api/infrastructure/persistence/FiInMemoryRepositoryTest.java
git commit -m "feat: add fi payment domain model"
```

## Task 3: pacs.009 Parser and Admission Service

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/port/Pacs009FiPaymentParser.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/iso/Pacs009Parser.java`
- Create: `src/main/java/com/cib/payment/api/application/service/FiPaymentAdmissionService.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/iso/Pacs009ParserTest.java`
- Test: `src/test/java/com/cib/payment/api/application/service/FiPaymentAdmissionServiceTest.java`

- [ ] **Step 1: Write failing parser tests**

Cover:

- valid `pacs009-accepted-nostro.xml` extracts message ID, instruction ID, end-to-end reference, amount, USD, settlement date, instructing agent, and instructed agent
- non-USD fixture parses but admission rejects
- malformed XML fails
- unsafe XML fails
- wrong namespace fails
- multiple payment instructions fail

Run: `mvn test -Dtest=Pacs009ParserTest`

Expected: FAIL because parser does not exist.

- [ ] **Step 2: Implement `Pacs009FiPaymentParser` and `Pacs009Parser`**

Follow `Pain001Parser` patterns:

- namespace-aware JDK parser
- disallow DOCTYPE and external entities
- use XPath with one namespace context
- return `ParsedFiPayment`

Define `ParsedFiPayment` inside the port:

```java
record ParsedFiPayment(
        String messageId,
        String instructionId,
        String endToEndId,
        String amount,
        String currency,
        String settlementDate,
        String instructingAgentBic,
        String instructedAgentBic,
        String intermediaryAgentBic,
        String sourceMessageType) {}
```

- [ ] **Step 3: Write failing admission tests**

Cover:

- valid USD `pacs.009` becomes `FiPaymentCandidate`
- missing required field returns `ValidationFailureException`
- non-USD returns `ValidationFailureException`
- unsupported content type returns `ValidationFailureException`

Run: `mvn test -Dtest=FiPaymentAdmissionServiceTest`

Expected: FAIL because admission service does not exist.

- [ ] **Step 4: Implement `FiPaymentAdmissionService`**

Validate:

- body present
- content type compatible with `application/pacs.009+xml` or XML for tests
- required identifiers and FI parties present
- currency is `USD`

Return `FiPaymentCandidate` with extracted business fields and no route role yet; route role is derived in Task 4.

- [ ] **Step 5: Run parser and admission tests**

Run: `mvn test -Dtest=Pacs009ParserTest,FiPaymentAdmissionServiceTest`

Expected: PASS.

- [ ] **Step 6: Commit pacs.009 admission**

```powershell
git add src/main/java/com/cib/payment/api/application/port/Pacs009FiPaymentParser.java src/main/java/com/cib/payment/api/infrastructure/iso/Pacs009Parser.java src/main/java/com/cib/payment/api/application/service/FiPaymentAdmissionService.java src/test/java/com/cib/payment/api/infrastructure/iso/Pacs009ParserTest.java src/test/java/com/cib/payment/api/application/service/FiPaymentAdmissionServiceTest.java
git commit -m "feat: add pacs009 fi payment admission"
```

## Task 4: Correspondent Route Profile and FI Payment Simulator

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/port/FiCorrespondentPaymentSimulator.java`
- Create: `src/main/java/com/cib/payment/api/application/port/FiCorrespondentPaymentOutcome.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/simulator/FiCorrespondentRouteProfile.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/simulator/DeterministicFiCorrespondentPaymentSimulator.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/simulator/FiCorrespondentRouteProfileTest.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/simulator/DeterministicFiCorrespondentPaymentSimulatorTest.java`

- [ ] **Step 1: Write route profile matrix tests**

Route matrix:

| Instructing | Instructed | Currency | Role | Masked account |
| --- | --- | --- | --- | --- |
| `CIBBHKHH` | `CORRUS33` | `USD` | `NOSTRO` | `nostro-usd-corrus33-****1234` |
| `VOSTUS33` | `CIBBHKHH` | `USD` | `VOSTRO` | `vostro-usd-vostus33-****5678` |
| `CIBBHKHH` | `LOROUS33` | `USD` | `LORO` | `loro-usd-lorous33-****9012` |

Create `FiCorrespondentRouteProfileTest` expecting:

```java
assertThat(profile.derive("CIBBHKHH", "CORRUS33", "USD").accountRole()).isEqualTo(AccountRelationshipRole.NOSTRO);
assertThat(profile.derive("VOSTUS33", "CIBBHKHH", "USD").accountRole()).isEqualTo(AccountRelationshipRole.VOSTRO);
assertThat(profile.derive("CIBBHKHH", "LOROUS33", "USD").accountRole()).isEqualTo(AccountRelationshipRole.LORO);
assertThatThrownBy(() -> profile.derive("CIBBHKHH", "UNKNOWN33", "USD"))
        .isInstanceOf(ValidationFailureException.class);
```

- [ ] **Step 2: Implement `FiCorrespondentRouteProfile`**

Provide:

```java
CorrespondentSettlementContext derive(String instructingAgent, String instructedAgent, String currency)
```

Throw `ValidationFailureException` when no route exists.

- [ ] **Step 3: Write simulator tests**

Cover:

- `fi_payment_accepted` returns `SETTLED`
- `fi_payment_pending_correspondent_review` returns `PROCESSING`
- `fi_payment_rejected_unsupported_correspondent` returns `REJECTED`

- [ ] **Step 4: Implement simulator**

`DeterministicFiCorrespondentPaymentSimulator` should accept `FiPaymentCandidate`, derived `CorrespondentSettlementContext`, and scenario string, then return:

```java
record FiCorrespondentPaymentOutcome(
        FiPaymentStatus status,
        String reasonCode,
        String reasonMessage,
        CorrespondentSettlementContext settlementContext) {}
```

- [ ] **Step 5: Run simulator tests**

Run: `mvn test -Dtest=FiCorrespondentRouteProfileTest,DeterministicFiCorrespondentPaymentSimulatorTest`

Expected: PASS.

- [ ] **Step 6: Commit simulator**

```powershell
git add src/main/java/com/cib/payment/api/application/port/FiCorrespondentPaymentSimulator.java src/main/java/com/cib/payment/api/application/port/FiCorrespondentPaymentOutcome.java src/main/java/com/cib/payment/api/infrastructure/simulator/FiCorrespondentRouteProfile.java src/main/java/com/cib/payment/api/infrastructure/simulator/DeterministicFiCorrespondentPaymentSimulator.java src/test/java/com/cib/payment/api/infrastructure/simulator
git commit -m "feat: simulate fi correspondent routes"
```

## Task 5: FI Payment Creation and Status Services

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/service/CreateFiPaymentService.java`
- Create: `src/main/java/com/cib/payment/api/application/service/GetFiPaymentStatusService.java`
- Create: `src/main/java/com/cib/payment/api/api/dto/FiPaymentAcknowledgementResponse.java`
- Create: `src/main/java/com/cib/payment/api/api/dto/FiPaymentStatusResponse.java`
- Create: `src/main/java/com/cib/payment/api/api/dto/CorrespondentSettlementContextResponse.java`
- Create: `src/main/java/com/cib/payment/api/api/dto/RecallInvestigationSummaryResponse.java`
- Test: `src/test/java/com/cib/payment/api/application/service/CreateFiPaymentServiceTest.java`
- Test: `src/test/java/com/cib/payment/api/application/service/GetFiPaymentStatusServiceTest.java`

- [ ] **Step 1: Write failing create service tests**

Cover:

- accepted scenario stores `SETTLED`
- pending scenario stores `PROCESSING`
- rejected scenario stores `REJECTED`
- derived account context appears in acknowledgement
- missing idempotency key fails
- duplicate same request replays original acknowledgement
- same key different semantics returns `IdempotencyConflictException`

- [ ] **Step 2: Implement `CreateFiPaymentService`**

Follow `CreateIsoDomesticPaymentService` locking/idempotency pattern.

Fingerprint inputs:

- client ID
- normalized `FiPaymentCandidate`
- normalized mock scenario

Store idempotency original response as JSON string for FI acknowledgements. If the current `IdempotencyRecord` XML field is too ISO-specific, add a neutral response body field without breaking existing usage, or add FI-specific idempotency record handling behind a new application service helper.

- [ ] **Step 3: Write failing status service tests**

Cover:

- owner can query own FI payment
- unrelated client cannot query
- unknown payment returns `PaymentNotFoundException`
- latest recall summary appears when repository contains a recall record

- [ ] **Step 4: Implement `GetFiPaymentStatusService`**

Return `FiPaymentStatusResponse` DTO. Keep controller mapping thin by letting the service build the response DTO.

- [ ] **Step 5: Run service tests**

Run: `mvn test -Dtest=CreateFiPaymentServiceTest,GetFiPaymentStatusServiceTest`

Expected: PASS.

- [ ] **Step 6: Commit FI payment services**

```powershell
git add src/main/java/com/cib/payment/api/application/service/CreateFiPaymentService.java src/main/java/com/cib/payment/api/application/service/GetFiPaymentStatusService.java src/main/java/com/cib/payment/api/api/dto src/test/java/com/cib/payment/api/application/service/CreateFiPaymentServiceTest.java src/test/java/com/cib/payment/api/application/service/GetFiPaymentStatusServiceTest.java
git commit -m "feat: add fi payment services"
```

## Task 6: camt.056 Parser and camt.029 Renderer

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/port/Camt056RecallRequestParser.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/iso/Camt056Parser.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/iso/Camt029Renderer.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/iso/Camt056ParserTest.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/iso/Camt029RendererTest.java`

- [ ] **Step 1: Write failing `camt.056` parser tests**

Cover:

- valid recall accepted fixture extracts message ID, case ID, original payment reference, and reason
- wrong namespace fails
- malformed XML fails
- unsafe XML fails

- [ ] **Step 2: Implement `Camt056RecallRequestParser` and `Camt056Parser`**

Define parsed record:

```java
record ParsedRecallRequest(
        String messageId,
        String caseId,
        String originalPaymentReference,
        String reasonCode,
        String sourceMessageType) {}
```

Use same secure XML settings as `Pain001Parser`.

- [ ] **Step 3: Write failing `camt.029` renderer tests**

Cover:

- accepted resolution contains `camt.029.001.09`
- rejected resolution includes reason details
- pending resolution includes pending reason details
- original recall identifiers and original FI payment reference are preserved

- [ ] **Step 4: Implement `Camt029Renderer`**

Render deterministic XML from `RecallInvestigationRecord`. Use secure `TransformerFactory` settings from `Pain002Renderer`.

- [ ] **Step 5: Run parser and renderer tests**

Run: `mvn test -Dtest=Camt056ParserTest,Camt029RendererTest`

Expected: PASS.

- [ ] **Step 6: Commit investigation XML adapters**

```powershell
git add src/main/java/com/cib/payment/api/application/port/Camt056RecallRequestParser.java src/main/java/com/cib/payment/api/infrastructure/iso/Camt056Parser.java src/main/java/com/cib/payment/api/infrastructure/iso/Camt029Renderer.java src/test/java/com/cib/payment/api/infrastructure/iso/Camt056ParserTest.java src/test/java/com/cib/payment/api/infrastructure/iso/Camt029RendererTest.java
git commit -m "feat: add camt investigation xml adapters"
```

## Task 7: Recall Investigation Service and Simulator

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/port/RecallInvestigationSimulator.java`
- Create: `src/main/java/com/cib/payment/api/application/port/RecallInvestigationOutcome.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/simulator/DeterministicRecallInvestigationSimulator.java`
- Create: `src/main/java/com/cib/payment/api/application/service/CreateRecallInvestigationService.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/simulator/DeterministicRecallInvestigationSimulatorTest.java`
- Test: `src/test/java/com/cib/payment/api/application/service/CreateRecallInvestigationServiceTest.java`

- [ ] **Step 1: Write simulator tests**

Cover:

- `recall_accepted` -> `ACCEPTED`
- `recall_rejected` -> `REJECTED`
- `investigation_pending` -> `PENDING`

- [ ] **Step 2: Implement recall simulator**

Return `RecallInvestigationOutcome(status, reasonCode, reasonMessage)`.

- [ ] **Step 3: Write recall service tests**

Cover:

- `SETTLED` payment allows recall
- `PROCESSING` payment allows recall
- `REJECTED` payment rejects recall
- wrong original reference rejects recall
- unrelated client cannot recall
- first recall stores record
- duplicate same semantics replays original `camt.029`
- second different recall for same FI payment returns conflict or validation error

- [ ] **Step 4: Implement `CreateRecallInvestigationService`**

Use the same idempotency locking approach as FI payment creation. Validate:

- `Idempotency-Key`
- request body present
- payment exists and owner matches
- payment status is `SETTLED` or `PROCESSING`
- no existing recall record with different semantics
- `camt.056` original payment reference matches the FI payment end-to-end/reference

- [ ] **Step 5: Run recall tests**

Run: `mvn test -Dtest=DeterministicRecallInvestigationSimulatorTest,CreateRecallInvestigationServiceTest`

Expected: PASS.

- [ ] **Step 6: Commit recall service**

```powershell
git add src/main/java/com/cib/payment/api/application/port/RecallInvestigationSimulator.java src/main/java/com/cib/payment/api/application/port/RecallInvestigationOutcome.java src/main/java/com/cib/payment/api/infrastructure/simulator/DeterministicRecallInvestigationSimulator.java src/main/java/com/cib/payment/api/application/service/CreateRecallInvestigationService.java src/test/java/com/cib/payment/api/infrastructure/simulator/DeterministicRecallInvestigationSimulatorTest.java src/test/java/com/cib/payment/api/application/service/CreateRecallInvestigationServiceTest.java
git commit -m "feat: add fi recall investigation workflow"
```

## Task 8: Security, Correlation, Masking, and Architecture Guardrails

**Files:**
- Modify: `src/main/java/com/cib/payment/api/infrastructure/security/SecurityConfig.java`
- Modify: `src/main/java/com/cib/payment/api/infrastructure/security/LocalJwtTokenGenerator.java`
- Modify: `src/test/java/com/cib/payment/api/testsupport/JwtTestSupport.java`
- Modify: `src/main/java/com/cib/payment/api/infrastructure/observability/AccountNumberMasker.java`
- Modify: `src/main/java/com/cib/payment/api/application/port/PaymentObservability.java`
- Modify: `src/main/java/com/cib/payment/api/infrastructure/observability/MicrometerPaymentObservability.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/security/SecurityIntegrationTest.java`
- Test: `src/test/java/com/cib/payment/api/api/FiSensitiveLoggingIntegrationTest.java`
- Test: `src/test/java/com/cib/payment/api/architecture/EdgeEngineBoundaryTest.java`

- [ ] **Step 1: Write failing security tests**

Add coverage:

- `POST /v1/fi-payments` requires `fi-payments:create`
- `GET /v1/fi-payments/{paymentId}` requires `fi-payments:read`
- `POST /v1/fi-payments/{paymentId}/recall-requests` requires `fi-payments:investigate`

- [ ] **Step 2: Update `SecurityConfig`**

Add matchers before `anyRequest()`:

```java
.requestMatchers(HttpMethod.POST, "/v1/fi-payments")
.hasAuthority("SCOPE_fi-payments:create")
.requestMatchers(HttpMethod.GET, "/v1/fi-payments/*")
.hasAuthority("SCOPE_fi-payments:read")
.requestMatchers(HttpMethod.POST, "/v1/fi-payments/*/recall-requests")
.hasAuthority("SCOPE_fi-payments:investigate")
```

- [ ] **Step 3: Update token helpers**

Ensure docs/tests can generate tokens with FI scopes:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-fi-a fi-payments:create,fi-payments:read,fi-payments:investigate 3600"
```

- [ ] **Step 4: Add masking tests**

Create `FiSensitiveLoggingIntegrationTest` asserting logs do not include raw `pacs.009`, raw `camt.056`, full simulated account references, or bearer tokens.

- [ ] **Step 5: Extend observability/masking**

Add FI-safe log methods or reuse existing methods with masked fields. Do not log raw XML payloads.

- [ ] **Step 6: Update architecture tests**

Extend `EdgeEngineBoundaryTest` to prevent:

- FI controllers accessing FI repositories directly
- FI domain models depending on API, HTTP, Spring, or XML classes

- [ ] **Step 7: Run security and architecture tests**

Run: `mvn test -Dtest=SecurityIntegrationTest,FiSensitiveLoggingIntegrationTest,EdgeEngineBoundaryTest`

Expected: PASS.

- [ ] **Step 8: Commit security and guardrails**

```powershell
git add src/main/java/com/cib/payment/api/infrastructure/security src/test/java/com/cib/payment/api/testsupport/JwtTestSupport.java src/main/java/com/cib/payment/api/infrastructure/observability src/main/java/com/cib/payment/api/application/port/PaymentObservability.java src/test/java/com/cib/payment/api/infrastructure/security/SecurityIntegrationTest.java src/test/java/com/cib/payment/api/api/FiSensitiveLoggingIntegrationTest.java src/test/java/com/cib/payment/api/architecture/EdgeEngineBoundaryTest.java
git commit -m "feat: secure fi payment endpoints"
```

## Task 9: FI API Controller and Integration Tests

**Files:**
- Create: `src/main/java/com/cib/payment/api/api/controller/FiPaymentController.java`
- Test: `src/test/java/com/cib/payment/api/api/CreateFiPaymentIntegrationTest.java`
- Test: `src/test/java/com/cib/payment/api/api/GetFiPaymentStatusIntegrationTest.java`
- Test: `src/test/java/com/cib/payment/api/api/CreateRecallInvestigationIntegrationTest.java`

- [ ] **Step 1: Write failing create/status integration tests**

Cover:

- accepted FI payment returns JSON with `SETTLED`
- pending FI payment returns JSON with `PROCESSING`
- unsupported correspondent returns JSON with `REJECTED`
- status query returns derived correspondent context
- invalid XML returns JSON error
- missing idempotency key returns JSON error

- [ ] **Step 2: Implement `FiPaymentController` create and status paths**

Use controller shape:

```java
@RestController
@RequestMapping("/v1/fi-payments")
public class FiPaymentController {
    @PostMapping
    ResponseEntity<FiPaymentAcknowledgementResponse> create(
            Jwt jwt,
            HttpServletRequest servletRequest,
            String idempotencyKey,
            String mockScenario,
            String contentType,
            String requestBody);

    @GetMapping("/{paymentId}")
    ResponseEntity<FiPaymentStatusResponse> getStatus(
            Jwt jwt,
            HttpServletRequest servletRequest,
            String paymentId);
}
```

Keep controller limited to headers, auth context, body forwarding, response content type, and observability timing.

- [ ] **Step 3: Write failing recall integration tests**

Cover:

- recall accepted returns `application/camt.029+xml`
- recall rejected returns `application/camt.029+xml`
- investigation pending returns `application/camt.029+xml`
- wrong original reference returns JSON error
- `REJECTED` FI payment recall returns JSON error
- duplicate recall same idempotency key replays original `camt.029`
- second different recall for same payment returns conflict/validation error

- [ ] **Step 4: Implement recall controller path**

Add:

```java
@PostMapping("/{paymentId}/recall-requests")
ResponseEntity<String> createRecall(
        Jwt jwt,
        HttpServletRequest servletRequest,
        String paymentId,
        String idempotencyKey,
        String mockScenario,
        String contentType,
        String requestBody);
```

Return `MediaType.valueOf("application/camt.029+xml")` for successful recall responses.

- [ ] **Step 5: Run FI integration tests**

Run: `mvn test -Dtest=CreateFiPaymentIntegrationTest,GetFiPaymentStatusIntegrationTest,CreateRecallInvestigationIntegrationTest`

Expected: PASS.

- [ ] **Step 6: Commit API integration**

```powershell
git add src/main/java/com/cib/payment/api/api/controller/FiPaymentController.java src/test/java/com/cib/payment/api/api/CreateFiPaymentIntegrationTest.java src/test/java/com/cib/payment/api/api/GetFiPaymentStatusIntegrationTest.java src/test/java/com/cib/payment/api/api/CreateRecallInvestigationIntegrationTest.java
git commit -m "feat: expose fi payment api"
```

## Task 10: Developer Artifacts and Product Documentation

**Files:**
- Modify: `src/main/resources/openapi/domestic-payment-api.yaml`
- Modify: `postman/domestic-rtp-payment-api.postman_collection.json`
- Modify: `postman/domestic-rtp-payment-api.local.postman_environment.json`
- Modify: `README.md`
- Modify: `docs/developer-support/postman-local-testing.md`
- Modify: `docs/product-strategy/payment-simulation-suite-vision.md`
- Test: `src/test/java/com/cib/payment/api/api/OpenApiContractTest.java`
- Test: `src/test/java/com/cib/payment/api/developer/PostmanArtifactValidationTest.java`

- [ ] **Step 1: Write failing artifact tests**

Extend `OpenApiContractTest` to assert:

- `/v1/fi-payments`
- `/v1/fi-payments/{paymentId}`
- `/v1/fi-payments/{paymentId}/recall-requests`
- `application/pacs.009+xml`
- `application/camt.056+xml`
- `application/camt.029+xml`
- FI scopes
- simulator-only limitations

Extend `PostmanArtifactValidationTest` to assert FI requests and scenarios exist.

- [ ] **Step 2: Update OpenAPI YAML**

Document:

- FI create JSON acknowledgement
- FI status JSON response
- recall `camt.029` XML response
- JSON error envelope
- FI scopes
- local/test-only mock scenario values
- no real SWIFT/CBPR+/ledger limitations

- [ ] **Step 3: Update Postman artifacts**

Add requests for:

- FI payment accepted
- FI payment pending correspondent review
- FI payment unsupported correspondent rejection
- FI payment status query
- recall accepted
- recall rejected
- investigation pending
- idempotency replay
- idempotency conflict
- auth/scope failure
- validation failure

- [ ] **Step 4: Update README and developer docs**

Add FI token generation example, USD-only profile, route profile explanation, and future `baas-api-sandbox` scenario-pack note.

- [ ] **Step 5: Run artifact tests**

Run: `mvn test -Dtest=OpenApiContractTest,PostmanArtifactValidationTest`

Expected: PASS.

- [ ] **Step 6: Commit developer artifacts**

```powershell
git add src/main/resources/openapi/domestic-payment-api.yaml postman README.md docs/developer-support docs/product-strategy/payment-simulation-suite-vision.md src/test/java/com/cib/payment/api/api/OpenApiContractTest.java src/test/java/com/cib/payment/api/developer/PostmanArtifactValidationTest.java
git commit -m "docs: add fi payment developer artifacts"
```

## Task 11: Final Verification and OpenSpec Alignment

**Files:**
- Verify all changed source, tests, OpenAPI, Postman, docs, and OpenSpec artifacts.

- [ ] **Step 1: Run focused FI suite**

Run:

```powershell
mvn test -Dtest=*Fi*,*Pacs009*,*Camt056*,*Camt029*,*Recall*
```

Expected: PASS.

- [ ] **Step 2: Run security, architecture, and artifact tests**

Run:

```powershell
mvn test -Dtest=SecurityIntegrationTest,EdgeEngineBoundaryTest,OpenApiContractTest,PostmanArtifactValidationTest
```

Expected: PASS.

- [ ] **Step 3: Run full Maven test suite**

Run:

```powershell
mvn test
```

Expected: PASS.

- [ ] **Step 4: Run OpenSpec validation**

Run:

```powershell
npx.cmd openspec validate add-fi-correspondent-rfi-workflow
```

Expected: `Change 'add-fi-correspondent-rfi-workflow' is valid`

- [ ] **Step 5: Run OpenSpec-to-Superpowers alignment review**

Check:

- every OpenSpec task has implementation coverage
- every plan task maps to an OpenSpec task
- no implementation behavior exceeds OpenSpec
- any file/path deviations are recorded in final notes
- `.claude/` and `.superpowers/` remain uncommitted unless explicitly requested

- [ ] **Step 6: Final git review**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only intended FI payment simulator, tests, docs, OpenAPI, Postman, and OpenSpec files are changed.

- [ ] **Step 7: Commit final verification adjustments**

If verification required small fixes:

```powershell
git add path\to\the\actual\fixed\file.java path\to\the\actual\fixed\test.java
git commit -m "test: verify fi correspondent workflow"
```

Replace the example paths with the concrete files fixed during verification. If no files changed, do not create an empty commit.
