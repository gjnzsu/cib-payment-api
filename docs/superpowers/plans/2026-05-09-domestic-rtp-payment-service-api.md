# Domestic RTP Payment Service API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java Spring Boot MVP for the Domestic RTP Payment Service API that satisfies the OpenSpec change `add-domestic-rtp-payment-service-api`.

**Architecture:** Use a layered Payment Service API. Controllers handle HTTP, headers, request parsing, and response mapping. Application services orchestrate authentication context, validation, idempotency, payment status, and downstream mock calls. Domain objects stay free of Spring, HTTP, JWT, OpenAPI, Postman, Kubernetes, and mock implementation details.

**Tech Stack:** Java 21, Maven, Spring Boot 3.x, Spring Web MVC, Spring Validation, Spring Security OAuth2 Resource Server, Spring Actuator, springdoc-openapi, Nimbus JOSE JWT, JUnit 5, MockMvc.

---

## Source Artifacts

Use these artifacts as the requirements source:

- `openspec/changes/add-domestic-rtp-payment-service-api/proposal.md`
- `openspec/changes/add-domestic-rtp-payment-service-api/design.md`
- `openspec/changes/add-domestic-rtp-payment-service-api/specs/domestic-rtp-payments/spec.md`
- `openspec/changes/add-domestic-rtp-payment-service-api/specs/payment-authentication-and-idempotency/spec.md`
- `openspec/changes/add-domestic-rtp-payment-service-api/specs/payment-developer-support/spec.md`
- `openspec/changes/add-domestic-rtp-payment-service-api/tasks.md`
- `AGENTS.md`

Do not add cross-border, batch, recurring, cancellation, amendment, webhook, callback, real core banking, real payment scheme connectivity, balance checks, fraud screening, sanctions screening, FX conversion, durable database storage, or a custom lightweight API Gateway service.

## OpenSpec Traceability And Drift Control

OpenSpec remains the source of truth for scope. This Superpowers plan translates the OpenSpec tasks into implementation slices and must not introduce product behavior that is not captured in OpenSpec.

### Traceability Matrix

| Superpowers Plan Task | OpenSpec Task Coverage | Alignment Notes |
| --- | --- | --- |
| Task 1: Spring Boot Project Scaffold | 1.1, 1.2, 1.3, 1.4 | Direct match for project foundation, dependencies, local config, and health checks. |
| Task 2: OpenAPI Contract | 2.1, 2.2, 2.3, 2.4, 2.5 | Direct match for OpenAPI, headers, schemas, errors, and rendering. |
| Task 3: Domain Model | 2.2, 4.4, 6.1, 6.2 | Supports schema/model behavior required by OpenSpec; no independent product scope. |
| Task 4: API DTOs And Validation | 4.1, 4.2, 4.3, 11.1 | Implements request contract and validation behavior from specs. |
| Task 5: Repositories And Fingerprinting | 5.1, 5.2, 5.4, 5.5, 5.6, 6.1 | Direct match for idempotency and status storage behavior. |
| Task 6: Correlation ID And Error Mapping | 8.1, 8.2, 8.3 | Direct match for correlation propagation and consistent errors. |
| Task 7: JWT Security And Authorization Context | 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 11.2 | Direct match for JWT, scopes, authorization context, and security tests. |
| Task 8: Downstream Mock | 7.1, 7.2, 7.3, 7.4, 7.5, 11.5 | Direct match for deterministic mock scenarios and fixtures. |
| Task 9: Payment Creation Use Case | 4.1, 4.2, 4.3, 4.4, 4.5, 5.3, 5.4, 5.5 | Direct match for payment creation, acceptance response, and idempotency behavior. |
| Task 10: Payment Status Query Use Case | 6.3, 6.4, 6.5, 11.4 | Direct match for status query, lifecycle visibility, and client isolation. |
| Task 11: Observability And Sensitive Data Protection | 8.2, 8.4, 8.5, 8.6, 11.6 | Plan started smaller than final OpenSpec observability slice; implementation stayed aligned by expanding tests/logging/metrics under OpenSpec task 8. |
| Task 12: Postman And Fixtures | 10.1, 10.2, 10.3, 10.4, 11.7 | Direct match for developer support artifacts. File names were adjusted to the final repo convention. |
| Task 13: Kubernetes And GKE Exposure | 9.1, 9.2, 9.3, 9.4 | Direct match for MVP GKE exposure without a custom gateway service. |
| Task 14: End-To-End Verification And OpenSpec Closure | 11.8, 11.9 | Direct match for final verification and OpenSpec validation. |
| Implementation hardening after manual Postman testing | 12.1, 12.2 | Added during execution because manual testing found local JWT/Postman usability gaps; captured back into OpenSpec tasks before completion. |

### Drift Control Rules For Future Runs

- Every Superpowers plan task must map to one or more OpenSpec tasks before implementation begins.
- If implementation discovers missing behavior, update OpenSpec `proposal.md`, `design.md`, `spec.md`, or `tasks.md` first, then revise this plan.
- If the Superpowers plan contains behavior that cannot be traced to OpenSpec, treat it as scope drift and pause before coding.
- If OpenSpec tasks are too vague for safe coding, refine OpenSpec before generating or continuing the Superpowers plan.
- Manual testing discoveries may add tasks, but they must be captured in OpenSpec before being marked complete.

### Current Journey Drift Assessment

- No product scope drift was identified: implemented endpoints, auth, idempotency, lifecycle, mock scenarios, observability, Postman, and GKE artifacts all trace back to OpenSpec.
- Two execution refinements occurred and were properly captured: local JWT/Postman usability hardening and Postman collection auth inheritance fixes.
- A small artifact naming difference occurred between this initial plan and final files: final Postman files use `domestic-rtp-payment-api.*` instead of `domestic-rtp-payment-service-api.*`. This is a naming convention adjustment, not behavior drift.

## File Map

Create these top-level files and directories:

- `pom.xml`: Maven build, dependencies, Java 21 configuration.
- `README.md`: local run, test, OpenAPI, JWT, Postman, and deployment notes.
- `src/main/java/com/cib/payment/api/PaymentApiApplication.java`: Spring Boot entry point.
- `src/main/resources/application.yml`: port, domestic currency, JWT, mock, and management settings.
- `src/main/resources/openapi/domestic-payment-api.yaml`: OpenAPI 3.0.3 contract.
- `src/main/java/com/cib/payment/api/api`: controllers, DTOs, exception mapper, correlation filter, security adapter.
- `src/main/java/com/cib/payment/api/application`: use cases, ports, idempotency service, authorization context service.
- `src/main/java/com/cib/payment/api/domain`: payment, idempotency, correlation, and authorization value objects.
- `src/main/java/com/cib/payment/api/infrastructure`: in-memory repositories, downstream mock, configuration properties.
- `src/test/java/com/cib/payment/api`: MockMvc, unit, and integration tests.
- `src/test/resources/application-test.yml`: deterministic test configuration.
- `postman/domestic-rtp-payment-service-api.postman_collection.json`: local debugging collection.
- `postman/domestic-rtp-payment-service-api.local.postman_environment.json`: local variables.
- `k8s/deployment.yaml`, `k8s/service.yaml`, `k8s/gateway.yaml`: GKE MVP exposure artifacts.

## Domain Contract

Use these public JSON fields:

```json
{
  "debtorAccount": {
    "bankCode": "CIBMYKL",
    "accountNumber": "1234567890",
    "accountName": "ACME Operating Account"
  },
  "creditorAccount": {
    "bankCode": "CIBMYKL",
    "accountNumber": "9876543210",
    "accountName": "Supplier Sdn Bhd"
  },
  "amount": {
    "currency": "MYR",
    "value": "1250.00"
  },
  "paymentReference": "INV-2026-0001",
  "remittanceInformation": "Invoice payment",
  "requestedExecutionDate": "2026-05-09"
}
```

Accepted creation response:

```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACCEPTED",
  "createdAt": "2026-05-09T12:00:00Z",
  "updatedAt": "2026-05-09T12:00:00Z",
  "correlationId": "corr-123",
  "links": {
    "status": "/v1/domestic-payments/550e8400-e29b-41d4-a716-446655440000"
  }
}
```

Status response:

```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "REJECTED",
  "createdAt": "2026-05-09T12:00:00Z",
  "updatedAt": "2026-05-09T12:00:01Z",
  "correlationId": "corr-123",
  "reason": {
    "code": "MOCK_REJECTION",
    "message": "Payment rejected by downstream mock"
  },
  "links": {
    "self": "/v1/domestic-payments/550e8400-e29b-41d4-a716-446655440000"
  }
}
```

Error response:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "status": 400,
  "correlationId": "corr-123",
  "details": [
    {
      "field": "amount.value",
      "message": "must be a decimal string"
    }
  ]
}
```

## Implementation Tasks

### Task 1: Spring Boot Project Scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/cib/payment/api/PaymentApiApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/resources/application-test.yml`
- Create: `README.md`

- [ ] **Step 1: Create Maven build**

Create `pom.xml` with Java 21 and these dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
  </dependency>
  <dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>10.0.2</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

- [ ] **Step 2: Create application entry point**

Create `PaymentApiApplication.java`:

```java
package com.cib.payment.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApiApplication.class, args);
    }
}
```

- [ ] **Step 3: Create local configuration**

Create `application.yml`:

```yaml
server:
  port: 8080

payment-api:
  domestic-currency: MYR
  jwt:
    issuer: bank-auth-server
    audience: domestic-payment-api
  idempotency:
    retention-hours: 24

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true

springdoc:
  swagger-ui:
    url: /openapi/domestic-payment-api.yaml
```

- [ ] **Step 4: Create test configuration**

Create `application-test.yml` with the same domestic currency and JWT values. Keep all storage in-memory.

- [ ] **Step 5: Verify scaffold**

Run: `mvn test`

Expected: build succeeds with zero tests or only generated context checks.

- [ ] **Step 6: Update OpenSpec task status**

After this task passes, mark tasks `1.1`, `1.2`, `1.3`, and `1.4` complete in `openspec/changes/add-domestic-rtp-payment-service-api/tasks.md`.

### Task 2: OpenAPI Contract

**Files:**
- Create: `src/main/resources/openapi/domestic-payment-api.yaml`
- Test: `src/test/java/com/cib/payment/api/api/OpenApiContractTest.java`

- [ ] **Step 1: Create OpenAPI contract file**

Define:

- `openapi: 3.0.3`
- `info.title: Domestic RTP Payment Service API`
- `paths./v1/domestic-payments.post`
- `paths./v1/domestic-payments/{paymentId}.get`
- `components.securitySchemes.bearerAuth`
- schemas listed in the Domain Contract section.

Use these required headers:

- `Authorization` via bearer security scheme.
- `Idempotency-Key` on `POST`.
- Optional inbound `X-Correlation-ID`.
- Local/test-only `X-Mock-Scenario` on `POST`, enum: `success`, `rejection`, `timeout`, `internal_failure`.

- [ ] **Step 2: Make OpenAPI static file available**

Configure Spring so `src/main/resources/openapi/domestic-payment-api.yaml` is served at `/openapi/domestic-payment-api.yaml`. Static resource defaults can serve this path under `classpath:/static`; if direct resource serving is not available, create `src/main/resources/static/openapi/domestic-payment-api.yaml` instead and keep the same content there.

- [ ] **Step 3: Add contract smoke test**

Create `OpenApiContractTest`:

```java
package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OpenApiContractTest {
    @Test
    void openApiContractDefinesPaymentEndpointsAndSchemas() throws Exception {
        var resource = new ClassPathResource("openapi/domestic-payment-api.yaml");
        var yaml = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml).contains("openapi: 3.0.3");
        assertThat(yaml).contains("/v1/domestic-payments:");
        assertThat(yaml).contains("/v1/domestic-payments/{paymentId}:");
        assertThat(yaml).contains("CreateDomesticPaymentRequest");
        assertThat(yaml).contains("PaymentResponse");
        assertThat(yaml).contains("PaymentStatusResponse");
        assertThat(yaml).contains("ErrorResponse");
        assertThat(yaml).contains("X-Correlation-ID");
        assertThat(yaml).contains("X-Mock-Scenario");
    }
}
```

- [ ] **Step 4: Verify OpenAPI task**

Run: `mvn test -Dtest=OpenApiContractTest`

Expected: test passes.

- [ ] **Step 5: Update OpenSpec task status**

Mark tasks `2.1`, `2.2`, `2.3`, `2.4`, and `2.5` complete after Swagger UI renders the static contract locally at `/swagger-ui.html`.

### Task 3: Domain Model

**Files:**
- Create: `src/main/java/com/cib/payment/api/domain/model/*.java`
- Test: `src/test/java/com/cib/payment/api/domain/model/DomainModelTest.java`

- [ ] **Step 1: Create domain records and enum**

Create:

```java
public record AccountReference(String bankCode, String accountNumber, String accountName) {}
public record Money(String currency, String value) {}
public record PaymentInstruction(AccountReference debtorAccount, AccountReference creditorAccount, Money amount, String paymentReference, String remittanceInformation, LocalDate requestedExecutionDate) {}
public record PaymentId(UUID value) {}
public enum PaymentStatus { ACCEPTED, PROCESSING, COMPLETED, REJECTED, FAILED, TIMEOUT }
public record PaymentReason(String code, String message) {}
public record CorrelationId(String value) {}
public record AuthorizationContext(String clientId, String subject, Set<String> scopes, String tenantId, Map<String, Object> actor, Instant issuedAt, String tokenId, CorrelationId correlationId) {}
```

Create `PaymentRecord` with `paymentId`, `clientId`, `instruction`, `status`, `createdAt`, `updatedAt`, `correlationId`, and optional `reason`.

Create `IdempotencyRecord` with `clientId`, `idempotencyKey`, `requestFingerprint`, accepted response fields, payment status reference, `createdAt`, and `updatedAt`.

- [ ] **Step 2: Add model smoke tests**

Create `DomainModelTest` that verifies `PaymentStatus` contains exactly `ACCEPTED`, `PROCESSING`, `COMPLETED`, `REJECTED`, `FAILED`, and `TIMEOUT`.

- [ ] **Step 3: Verify domain task**

Run: `mvn test -Dtest=DomainModelTest`

Expected: test passes.

### Task 4: API DTOs And Validation

**Files:**
- Create: `src/main/java/com/cib/payment/api/api/dto/*.java`
- Create: `src/main/java/com/cib/payment/api/api/validation/DecimalString.java`
- Create: `src/main/java/com/cib/payment/api/api/validation/DecimalStringValidator.java`
- Test: `src/test/java/com/cib/payment/api/api/dto/CreateDomesticPaymentRequestValidationTest.java`

- [ ] **Step 1: Create DTOs**

Create request DTOs:

```java
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateDomesticPaymentRequest(
    @Valid @NotNull AccountReferenceRequest debtorAccount,
    @Valid @NotNull AccountReferenceRequest creditorAccount,
    @Valid @NotNull MoneyRequest amount,
    @NotBlank String paymentReference,
    String remittanceInformation,
    LocalDate requestedExecutionDate
) {}

public record AccountReferenceRequest(
    @NotBlank String bankCode,
    @NotBlank String accountNumber,
    @NotBlank String accountName
) {}

public record MoneyRequest(
    @NotBlank String currency,
    @NotBlank @DecimalString String value
) {}
```

Create response DTOs: `PaymentResponse`, `PaymentStatusResponse`, `PaymentLinksResponse`, `PaymentReasonResponse`, `ErrorResponse`, and `ValidationErrorDetailResponse`.

- [ ] **Step 2: Create decimal string validator**

Use regex `^[0-9]+(\\.[0-9]{1,2})?$`. Reject `0`, `0.00`, negative values, and values with more than two decimals.

- [ ] **Step 3: Add validation tests**

Verify:

- valid request passes Bean Validation.
- missing debtor account fails.
- missing account number fails.
- `amount.value = "12.345"` fails.
- `amount.value = "-1.00"` fails.
- unknown top-level JSON field fails Jackson deserialization.

- [ ] **Step 4: Verify DTO task**

Run: `mvn test -Dtest=CreateDomesticPaymentRequestValidationTest`

Expected: all validation tests pass.

### Task 5: Repositories And Fingerprinting

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/port/PaymentStatusRepository.java`
- Create: `src/main/java/com/cib/payment/api/application/port/IdempotencyRepository.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/persistence/InMemoryPaymentStatusRepository.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/persistence/InMemoryIdempotencyRepository.java`
- Create: `src/main/java/com/cib/payment/api/application/service/RequestFingerprintService.java`
- Test: `src/test/java/com/cib/payment/api/application/service/RequestFingerprintServiceTest.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/persistence/InMemoryRepositoryTest.java`

- [ ] **Step 1: Define repository ports**

`PaymentStatusRepository` methods:

```java
PaymentRecord save(PaymentRecord record);
Optional<PaymentRecord> findByPaymentIdAndClientId(PaymentId paymentId, String clientId);
PaymentRecord updateStatus(PaymentId paymentId, PaymentStatus status, PaymentReason reason);
```

`IdempotencyRepository` methods:

```java
Optional<IdempotencyRecord> find(String clientId, String idempotencyKey);
IdempotencyRecord saveIfAbsent(IdempotencyRecord record);
```

- [ ] **Step 2: Implement in-memory adapters**

Use `ConcurrentHashMap`. Use key format `clientId + ":" + idempotencyKey` for idempotency. Ensure `saveIfAbsent` uses `putIfAbsent`.

- [ ] **Step 3: Implement request fingerprinting**

Use Jackson `ObjectMapper` to serialize a stable map containing:

- `clientId`
- `requestBody`
- behaviorally relevant context fields used by the MVP

Hash canonical JSON with SHA-256 and encode hex lowercase.

- [ ] **Step 4: Add tests**

Verify:

- same client and same body create same fingerprint.
- same client and different amount create different fingerprint.
- different client and same body create different fingerprint.
- idempotency `saveIfAbsent` returns the original record for duplicates.

- [ ] **Step 5: Verify repository task**

Run: `mvn test -Dtest=RequestFingerprintServiceTest,InMemoryRepositoryTest`

Expected: tests pass.

### Task 6: Correlation ID And Error Mapping

**Files:**
- Create: `src/main/java/com/cib/payment/api/api/CorrelationIdFilter.java`
- Create: `src/main/java/com/cib/payment/api/api/GlobalExceptionHandler.java`
- Create: `src/main/java/com/cib/payment/api/application/exception/*.java`
- Test: `src/test/java/com/cib/payment/api/api/CorrelationAndErrorHandlingTest.java`

- [ ] **Step 1: Create correlation filter**

Read `X-Correlation-ID`. If blank, generate UUID string. Store it as request attribute `correlationId`. Always set response header `X-Correlation-ID`.

- [ ] **Step 2: Create application exceptions**

Create:

- `ValidationFailureException`
- `AuthenticationContextException`
- `AuthorizationScopeException`
- `IdempotencyConflictException`
- `PaymentNotFoundException`
- `SemanticPaymentException`
- `DownstreamProcessingException`

- [ ] **Step 3: Create global exception handler**

Map:

- malformed JSON, validation failures, missing `Idempotency-Key` -> `400`
- authentication failures -> `401`
- scope failures -> `403`
- not found or unauthorized payment lookup -> `404`
- idempotency conflict -> `409`
- semantic payment invalidity -> `422`
- unexpected exception -> `500`

Every error body must include `code`, `message`, `status`, `correlationId`, and field details when available.

- [ ] **Step 4: Add tests**

Verify:

- inbound `X-Correlation-ID` is returned.
- missing `X-Correlation-ID` generates a response header.
- validation error body contains same correlation ID.

- [ ] **Step 5: Verify correlation and errors**

Run: `mvn test -Dtest=CorrelationAndErrorHandlingTest`

Expected: tests pass.

### Task 7: JWT Security And Authorization Context

**Files:**
- Create: `src/main/java/com/cib/payment/api/infrastructure/security/SecurityConfig.java`
- Create: `src/main/java/com/cib/payment/api/application/service/AuthorizationContextService.java`
- Create: `src/test/java/com/cib/payment/api/testsupport/JwtTestSupport.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/security/SecurityIntegrationTest.java`

- [ ] **Step 1: Configure resource server**

Configure Spring Security:

- all `/v1/domestic-payments/**` endpoints require authentication.
- `/actuator/health/**`, `/swagger-ui/**`, `/v3/api-docs/**`, and `/openapi/**` are permitted.
- use RSA public key for local JWT validation.
- validate issuer `bank-auth-server`.
- validate audience `domestic-payment-api`.

- [ ] **Step 2: Derive authorization context**

Extract:

- `sub` as client identity.
- `scope` as space-delimited scopes.
- optional `tenant_id`.
- optional `actor`.
- optional `iat`.
- optional `jti`.
- request correlation ID.

- [ ] **Step 3: Add JWT test support**

Create RSA keypair in test support and helper methods:

- `tokenWithScopes(String subject, String... scopes)`
- `expiredToken()`
- `tokenMissingClaim(String claimName)`
- `tokenWithWrongAudience()`

- [ ] **Step 4: Add security tests**

Verify:

- missing token returns `401`.
- expired token returns `401`.
- wrong audience returns `401`.
- missing `payments:create` returns `403` on `POST`.
- missing `payments:read` returns `403` on `GET`.

- [ ] **Step 5: Verify security**

Run: `mvn test -Dtest=SecurityIntegrationTest`

Expected: tests pass.

### Task 8: Downstream Mock

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/port/DownstreamPaymentProcessor.java`
- Create: `src/main/java/com/cib/payment/api/application/port/DownstreamPaymentOutcome.java`
- Create: `src/main/java/com/cib/payment/api/infrastructure/downstream/MockDownstreamPaymentProcessor.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/downstream/MockDownstreamPaymentProcessorTest.java`

- [ ] **Step 1: Define downstream port and outcome**

`DownstreamPaymentProcessor` method:

```java
DownstreamPaymentOutcome process(PaymentInstruction instruction, AuthorizationContext authorizationContext, CorrelationId correlationId, String mockScenario);
```

`DownstreamPaymentOutcome` fields:

- `PaymentStatus status`
- optional `PaymentReason reason`

- [ ] **Step 2: Implement mock scenarios**

Map:

- null or `success` -> `COMPLETED`
- `rejection` -> `REJECTED` with code `MOCK_REJECTION`
- `timeout` -> `TIMEOUT` with code `MOCK_TIMEOUT`
- `internal_failure` -> `FAILED` with code `MOCK_INTERNAL_FAILURE`
- other values -> validation error `UNSUPPORTED_MOCK_SCENARIO`

- [ ] **Step 3: Add mock tests**

Verify all four supported values and unsupported value.

- [ ] **Step 4: Verify downstream mock**

Run: `mvn test -Dtest=MockDownstreamPaymentProcessorTest`

Expected: tests pass.

### Task 9: Payment Creation Use Case

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/service/CreateDomesticPaymentService.java`
- Modify: `src/main/java/com/cib/payment/api/api/controller/DomesticPaymentController.java`
- Test: `src/test/java/com/cib/payment/api/api/CreateDomesticPaymentIntegrationTest.java`

- [ ] **Step 1: Create service flow**

Implement this order:

1. Validate domestic currency equals configured value.
2. Validate `Idempotency-Key` is present for `POST`.
3. Compute fingerprint after DTO validation succeeds.
4. Check idempotency store by client and key.
5. If same fingerprint exists, return original accepted response.
6. If different fingerprint exists, throw `IdempotencyConflictException`.
7. Generate UUID `paymentId`.
8. Save payment record with `ACCEPTED`.
9. Save idempotency record with accepted response.
10. Invoke downstream mock with instruction, authorization context, correlation ID, and `X-Mock-Scenario`.
11. Update stored payment status to downstream outcome.
12. Return original `202 Accepted` response.

- [ ] **Step 2: Create controller**

`POST /v1/domestic-payments` accepts:

- `Authorization`
- `Idempotency-Key`
- optional `X-Correlation-ID`
- optional local/test-only `X-Mock-Scenario`
- JSON body

Return `202`.

- [ ] **Step 3: Add integration tests**

Verify:

- valid request returns `202`.
- response contains UUID `paymentId`.
- response contains `links.status`.
- replay with same key/body returns same response.
- same key/different body returns `409`.
- missing key returns `400`.
- unsupported currency returns validation error.
- unknown top-level field returns validation error.

- [ ] **Step 4: Verify creation use case**

Run: `mvn test -Dtest=CreateDomesticPaymentIntegrationTest`

Expected: tests pass.

### Task 10: Payment Status Query Use Case

**Files:**
- Create: `src/main/java/com/cib/payment/api/application/service/GetDomesticPaymentStatusService.java`
- Modify: `src/main/java/com/cib/payment/api/api/controller/DomesticPaymentController.java`
- Test: `src/test/java/com/cib/payment/api/api/GetDomesticPaymentStatusIntegrationTest.java`

- [ ] **Step 1: Create status service**

Look up payment by `paymentId` UUID and authenticated client ID. Return not found for unknown IDs and cross-client access.

- [ ] **Step 2: Add status controller method**

`GET /v1/domestic-payments/{paymentId}`:

- requires `payments:read`
- does not require `Idempotency-Key`
- returns current stored status
- returns reason details for `REJECTED`, `FAILED`, and `TIMEOUT`
- returns `links.self`

- [ ] **Step 3: Add integration tests**

Verify:

- create then get returns current status.
- unknown UUID returns `404`.
- malformed UUID returns `400`.
- same payment queried by different client returns `404`.
- missing `Idempotency-Key` on `GET` still succeeds.

- [ ] **Step 4: Verify status use case**

Run: `mvn test -Dtest=GetDomesticPaymentStatusIntegrationTest`

Expected: tests pass.

### Task 11: Observability And Sensitive Data Protection

**Files:**
- Create: `src/main/java/com/cib/payment/api/infrastructure/logging/SensitiveValueMasker.java`
- Test: `src/test/java/com/cib/payment/api/infrastructure/logging/SensitiveValueMaskerTest.java`
- Modify: services/controllers that log payment events.

- [ ] **Step 1: Create masking helper**

Mask account numbers by keeping only the last four characters. Example: `1234567890` -> `******7890`.

- [ ] **Step 2: Use structured logs**

Log these events without raw request payloads:

- payment accepted
- idempotency replay
- idempotency conflict
- status query
- downstream mock outcome
- validation/authentication/authorization failure

Fields allowed in logs:

- `paymentId`
- `clientId`
- `tenantId`
- `correlationId`
- `status`
- masked account number

- [ ] **Step 3: Add tests**

Verify masking helper output and ensure account number is not returned unmasked by the helper.

- [ ] **Step 4: Verify observability**

Run: `mvn test -Dtest=SensitiveValueMaskerTest`

Expected: tests pass.

### Task 12: Postman And Fixtures

**Files:**
- Create: `postman/domestic-rtp-payment-service-api.postman_collection.json`
- Create: `postman/domestic-rtp-payment-service-api.local.postman_environment.json`
- Create: `src/test/resources/fixtures/payment-success.json`
- Create: `src/test/resources/fixtures/payment-rejection.json`
- Create: `src/test/resources/fixtures/payment-timeout.json`
- Create: `src/test/resources/fixtures/payment-internal-failure.json`
- Create: `src/test/resources/fixtures/payment-invalid.json`
- Test: `src/test/java/com/cib/payment/api/developer/DeveloperArtifactAlignmentTest.java`

- [ ] **Step 1: Create fixtures**

Use the request JSON from the Domain Contract section. Vary only payment reference or mock scenario intent in fixture file names. Keep `X-Mock-Scenario` outside the JSON body.

- [ ] **Step 2: Create Postman environment**

Variables:

- `baseUrl`
- `jwtToken`
- `correlationId`
- `idempotencyKey`
- `paymentId`
- `mockScenario`

- [ ] **Step 3: Create Postman collection**

Requests:

- create payment success
- create payment rejection
- create payment timeout
- create payment internal failure
- query payment status
- missing idempotency key
- idempotency conflict
- missing token
- missing scope
- invalid request

- [ ] **Step 4: Add alignment test**

Verify Postman files contain:

- `/v1/domestic-payments`
- `Idempotency-Key`
- `X-Correlation-ID`
- `X-Mock-Scenario`
- `paymentId`

- [ ] **Step 5: Verify developer artifacts**

Run: `mvn test -Dtest=DeveloperArtifactAlignmentTest`

Expected: tests pass.

### Task 13: Kubernetes And GKE Exposure

**Files:**
- Create: `k8s/deployment.yaml`
- Create: `k8s/service.yaml`
- Create: `k8s/gateway.yaml`
- Modify: `README.md`

- [ ] **Step 1: Create deployment**

Use app name `domestic-rtp-payment-api`. Configure container port `8080`. Configure readiness and liveness probes using `/actuator/health/readiness` and `/actuator/health/liveness`.

- [ ] **Step 2: Create service**

Expose port `80` to target port `8080`.

- [ ] **Step 3: Create GKE Gateway API manifest**

Use Gateway API route to send `/v1/domestic-payments` and documentation paths to the service. Do not create a custom API gateway service.

- [ ] **Step 4: Document local and GKE assumptions**

README must mention:

- manifests are MVP examples
- JWT key material must be supplied securely outside local development
- Apigee or Kong can be introduced later without changing domain/application code

- [ ] **Step 5: Verify manifests are parseable**

Run: `kubectl apply --dry-run=client -f k8s`

Expected: manifests parse successfully in an environment with `kubectl` configured.

### Task 14: End-To-End Verification And OpenSpec Closure

**Files:**
- Modify: `openspec/changes/add-domestic-rtp-payment-service-api/tasks.md`
- Modify: `README.md`

- [ ] **Step 1: Run full test suite**

Run: `mvn test`

Expected: all tests pass.

- [ ] **Step 2: Run OpenSpec validation**

Run: `npx.cmd openspec validate add-domestic-rtp-payment-service-api`

Expected: `Change 'add-domestic-rtp-payment-service-api' is valid`.

- [ ] **Step 3: Update OpenSpec tasks**

Mark every completed checklist item in `openspec/changes/add-domestic-rtp-payment-service-api/tasks.md`. Do not mark an item complete unless the related code, artifacts, and tests exist.

- [ ] **Step 4: Review scope guardrails**

Search for prohibited scope creep:

```powershell
rg -n "cross-border|batch|recurring|standing|cancellation|amendment|webhook|callback|fraud|sanctions|FX|ISO 20022|pacs\\.008|core banking|settlement" .
```

Expected: only OpenSpec guardrails or explicit out-of-scope documentation references appear.

- [ ] **Step 5: Final implementation summary**

Prepare a concise summary listing:

- implemented endpoints
- auth/idempotency behavior
- mock scenarios
- OpenAPI/Postman locations
- verification commands and results

## Commit Plan

Use small commits after passing each task group:

```bash
git add pom.xml src/main resources README.md
git commit -m "feat: scaffold domestic rtp payment api"

git add src/main/resources/openapi src/test/java/com/cib/payment/api/api/OpenApiContractTest.java
git commit -m "docs: add domestic rtp openapi contract"

git add src/main/java/com/cib/payment/api/domain src/test/java/com/cib/payment/api/domain
git commit -m "feat: add payment domain model"

git add src/main/java/com/cib/payment/api src/test/java/com/cib/payment/api
git commit -m "feat: implement domestic rtp payment flows"

git add postman k8s README.md openspec/changes/add-domestic-rtp-payment-service-api/tasks.md
git commit -m "chore: add developer and deployment artifacts"
```

Only commit if the user asks for commits or if the execution workflow explicitly includes commits.

## Self-Review Checklist

- Spec coverage: Tasks 1-14 cover scaffold, OpenAPI, validation, auth, idempotency, status lifecycle, downstream mock, error handling, correlation ID, logging, Postman, Kubernetes, tests, and OpenSpec validation.
- Placeholder scan: This plan intentionally avoids placeholder tokens and open-ended implementation language.
- Type consistency: Public names match the OpenSpec contract: `paymentId`, `Idempotency-Key`, `X-Correlation-ID`, `X-Mock-Scenario`, `payments:create`, `payments:read`, `ACCEPTED`, `PROCESSING`, `COMPLETED`, `REJECTED`, `FAILED`, `TIMEOUT`.
