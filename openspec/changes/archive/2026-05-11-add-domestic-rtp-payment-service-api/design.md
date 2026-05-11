## Context

The Domestic RTP Payment Service API will expose domestic real-time payment initiation and status query as an external-facing Banking-as-a-Service capability. The first version is an MVP for corporate clients, fintech partners, ERP platforms, treasury systems, and embedded banking applications that need to create a single domestic real-time payment instruction and track its processing status through a developer-friendly JSON API.

The current change is proposal-stage plus design-stage only. Application code, API implementation, tests, OpenAPI files, and Postman files will be created in later artifacts or implementation steps.

The design must preserve the proposal scope:

- Domestic real-time payments only.
- JSON business payload only.
- Single payment instruction per request.
- JWT Bearer authentication with scope validation.
- Required `Idempotency-Key` for payment creation.
- Asynchronous payment processing and status query.
- Downstream payment processor mock instead of real payment rails or core banking integration.
- OpenAPI documentation and Postman collection/environment support as developer deliverables.
- MVP-level in-memory or lightweight persistence for payment status and idempotency records.

Primary stakeholders are external API consumers, partner onboarding teams, platform/API engineering teams, payment product owners, security reviewers, QA/contract-test owners, and future payment-platform maintainers.

## Goals / Non-Goals

**Goals:**

- Define a clean external API architecture for domestic RTP payment creation and status query.
- Define the MVP API gateway exposure strategy for GKE without introducing a custom gateway service.
- Support `POST /v1/domestic-payments` with `202 Accepted` for accepted asynchronous processing.
- Support `GET /v1/domestic-payments/{paymentId}` for polling current payment status.
- Define payment creation flow from request authentication through validation, idempotency handling, payment record creation, downstream mock invocation, and status persistence.
- Define an asynchronous status model that can represent accepted, processing, completed, rejected, failed, timeout, and unknown outcomes.
- Define idempotency behavior for duplicate payment creation requests, including safe replay and conflict detection.
- Define JWT authentication and scope-based authorization at the API layer.
- Define downstream payment processor mock behavior for success, rejection, timeout, and internal failure scenarios.
- Define consistent JSON error handling for validation, authentication, authorization, idempotency conflict, missing payment, and downstream failure cases.
- Define observability expectations, including correlation ID propagation, structured logs, and sensitive data masking.
- Define Postman debugging support as a first-class developer deliverable for common success and error scenarios.

**Non-Goals:**

- Implementing application code in this stage.
- Creating API code, tests, OpenAPI files, Postman files, or mock fixtures in this stage.
- Supporting cross-border, batch, recurring, standing instruction, cancellation, amendment, webhook, or callback payment flows.
- Accepting ISO 20022 XML or encrypted ISO 20022 `pacs.008` payloads.
- Integrating with real core banking, real payment scheme connectivity, clearing, settlement, balance checks, fraud screening, sanctions screening, or FX conversion.
- Designing a production-grade database, production key-management model, or secret-rotation process.
- Implementing a custom lightweight API Gateway service for the GKE-based MVP.

## Decisions

### API Architecture

The MVP will use a layered API architecture:

- API entrypoint/controller layer for HTTP routing, headers, request parsing, response mapping, and OpenAPI alignment.
- Authentication and authorization layer for JWT validation and scope checks before business processing.
- Request validation layer for required fields, payload shape, supported domestic RTP attributes, and header validation.
- Idempotency layer for `POST /v1/domestic-payments` replay and conflict handling.
- Payment application service for orchestration, payment ID creation, status transitions, and downstream mock coordination.
- Persistence abstraction for payment status records and idempotency records.
- Downstream payment processor client interface with a local mock implementation.
- Observability support for correlation IDs, structured logs, metrics, and masking.

This keeps external API concerns separate from payment orchestration and downstream integration. It also gives the later implementation a clear path to replace in-memory persistence or mock downstream processing without changing the public API contract.

Alternative considered: put validation, idempotency, and downstream mock calls directly inside endpoint handlers. This is simpler for a demo but makes payment behavior harder to test, harder to document, and harder to evolve into a reusable Payment-as-a-Service component.

### API Gateway Strategy

The target production architecture assumes that the Payment API will be exposed through an API gateway or API management layer.

For the GKE-based MVP, no custom lightweight API Gateway service will be implemented. Instead, the API will be exposed through GKE Gateway API or Kubernetes Ingress.

Gateway-like application controls required for the MVP, including JWT validation, scope validation, correlation ID handling, request validation, and consistent error handling, will be implemented inside the Payment Service API.

This keeps the MVP simple while allowing the architecture to evolve toward a dedicated API management platform such as Apigee or Kong in the future.

Alternative considered: build a custom lightweight API Gateway service for the MVP. This would add an extra deployable service and duplicate responsibilities that can be handled inside the Payment Service API for the MVP, while still leaving room for managed API gateway capabilities later.

### API Contract Shape

The MVP public contract will use a practical domestic payment JSON payload rather than an ISO 20022 XML payload or a minimal demo-only shape.

Payment creation requires:

- `debtorAccount` with `bankCode`, `accountNumber`, and `accountName`.
- `creditorAccount` with `bankCode`, `accountNumber`, and `accountName`.
- `amount` with configured domestic RTP `currency` and decimal string `value`.
- `paymentReference` as the required client-visible payment reference.

Payment creation may include `remittanceInformation` and `requestedExecutionDate` when the request remains within domestic real-time payment scope. Unknown top-level request fields are rejected so clients do not depend on unsupported behavior and idempotency fingerprinting remains stable.

The public `paymentId` is represented as a UUID string. Accepted creation responses include `paymentId`, current `status`, `createdAt`, `updatedAt`, `correlationId`, and `links.status`. Status query responses include `paymentId`, current `status`, timestamps, `correlationId`, optional reason details for rejected, failed, or timed-out payments, and `links.self`.

The OpenAPI implementation task will define the exact machine-readable schemas, examples, and field constraints for `CreateDomesticPaymentRequest`, `AccountReference`, `Money`, `PaymentResponse`, `PaymentStatusResponse`, `PaymentReason`, `ErrorResponse`, and `ValidationErrorDetail`.

Alternative considered: defer all payload details to implementation. That would leave OpenAPI, validation, idempotency fingerprinting, Postman examples, mock fixtures, and contract tests without a stable target.

### Payment Creation Flow

`POST /v1/domestic-payments` will follow this flow:

1. Accept JSON payload and required headers, including `Authorization: Bearer <token>` and `Idempotency-Key`.
2. Resolve or generate a correlation ID from the inbound `X-Correlation-ID` header.
3. Validate JWT signature, issuer, audience, expiry, and required claims.
4. Validate required payment scope for domestic payment creation.
5. Validate the request payload and reject malformed or unsupported payment instructions before idempotency persistence creates a successful record.
6. Compute a stable request fingerprint from the normalized accepted business request body, authenticated client identity, and behaviorally relevant request context.
7. Check idempotency records for the client and `Idempotency-Key`.
8. If the key is new, create a payment record with a new `paymentId` and initial asynchronous status.
9. Store the idempotency record with the request fingerprint and the accepted response.
10. Invoke the downstream payment processor mock using the payment instruction, authorization context, and correlation ID.
11. Update the payment status according to the downstream mock outcome.
12. Return `202 Accepted` with `paymentId`, current status, timestamps, correlation ID, and status query link.

The API returns acceptance of processing, not final settlement. Final or terminal outcomes are observed through the status query endpoint.

Alternative considered: make payment creation synchronous and return final success or rejection from `POST`. This is less realistic for payment rails, weakens timeout handling, and makes downstream latency part of client-facing latency. The asynchronous model better matches real payment processing and the proposed embedded banking API boundary.

### Async Status Model

Payment status will be modeled as a lifecycle stored against `paymentId`. The MVP should support these states:

- `ACCEPTED`: API accepted the request and created the payment record.
- `PROCESSING`: downstream processing has started or is awaiting a mock response.
- `COMPLETED`: downstream mock reports successful processing.
- `REJECTED`: downstream mock rejects the payment for business reasons.
- `FAILED`: downstream mock or internal processing fails.
- `TIMEOUT`: downstream mock simulates no timely response.

`GET /v1/domestic-payments/{paymentId}` returns the current known status, timestamps, correlation ID, a self link, and reason details when a status is rejected, failed, or timed out. Status query requires authentication and read scope validation. A missing or unauthorized payment returns a consistent error response rather than leaking sensitive details.

Alternative considered: only store the accepted response and omit a separate status lifecycle. That would satisfy basic idempotency but would not support asynchronous status tracking, downstream mock scenarios, or realistic payment demonstrations.

### Idempotency Design

`Idempotency-Key` is required for payment creation and scoped to the authenticated client. The idempotency store records:

- Authenticated client or subject identifier.
- Idempotency key.
- Stable request fingerprint.
- Initial accepted response, including `paymentId`.
- Processing status reference.
- Created and updated timestamps.

Behavior:

- Same client, same key, same request fingerprint returns the same accepted response without creating a second payment.
- Same client, same key, different request fingerprint returns `409 Conflict`.
- Missing or invalid `Idempotency-Key` returns a validation error.
- `Idempotency-Key` is not required for status query requests.
- Invalid requests, including requests with unknown top-level fields, are rejected before accepted idempotency records are created.
- Concurrent duplicate submissions should resolve to one payment record and one idempotency record.

For the MVP, retention can be short-lived and backed by in-memory or lightweight storage. The later implementation should isolate this behind an interface so production storage can be introduced without changing endpoint behavior.

Alternative considered: make idempotency global across all clients. That creates accidental collisions between clients and is inappropriate for partner-facing Banking-as-a-Service APIs. Client-scoped keys are safer and easier for integrators to reason about.

### JWT Authentication and Scope Validation

All endpoints require JWT Bearer authentication. The API layer validates token signature and standard claims before request handling. The MVP should define separate scopes so access can be granted narrowly:

- `payments:create` for `POST /v1/domestic-payments`.
- `payments:read` for `GET /v1/domestic-payments/{paymentId}`.

Authorization context, including client identifier, subject, scopes, and correlation ID, is passed to the payment service and downstream mock. The downstream mock will not enforce production authorization rules, but it will receive this context to support future micro-authorization extension and test visibility.

Alternative considered: validate only token presence in the API and skip scope checks for MVP. That would make the API easier to call locally but would not satisfy embedded banking security expectations or the proposal requirement for scope-based access control.

### Authentication Context and JWT Claims

The Payment Service API uses a JWT Bearer token to authenticate the external API client and derive the authorization context for payment processing.

In this MVP, the JWT primarily represents the calling external client, such as a corporate system, fintech backend, ERP platform, or treasury application. Where available, the token may also carry tenant and actor context to support embedded banking audit and future delegated authorization models.

#### Required JWT Claims

The token SHALL include the following claims:

| Claim | Description |
|---|---|
| `sub` | Calling client identifier, such as corporate client, fintech application, or partner system |
| `scope` | Space-delimited API scopes, including `payments:create` and/or `payments:read` |
| `aud` | Intended audience, expected to be `domestic-payment-api` |
| `iss` | Trusted token issuer |
| `exp` | Token expiry timestamp |

#### Recommended JWT Claims

The token SHOULD include the following claims for better auditability and embedded banking support:

| Claim | Description |
|---|---|
| `tenant_id` | Tenant, partner, or corporate customer identifier |
| `actor` | Entity that triggered the payment action inside the external client system |
| `iat` | Token issued-at timestamp |
| `jti` | Unique token identifier for audit and replay analysis |

#### Actor Model

The `actor` claim represents the entity that triggered the payment action inside the external client system. The claim is optional for the MVP because some Banking-as-a-Service clients are backend-to-backend integrations without an end user, but it gives embedded banking clients a place to carry system, user, or application context for audit.

Example system-triggered payment:

```json
{
  "sub": "client-id-123",
  "tenant_id": "tenant-001",
  "actor": {
    "type": "SYSTEM",
    "id": "system-user"
  },
  "scope": "payments:create payments:read",
  "aud": "domestic-payment-api",
  "iss": "bank-auth-server",
  "exp": 1770000000,
  "iat": 1769996400,
  "jti": "token-unique-id-123"
}
```

The validated authorization context derived from these claims will be passed into payment orchestration, idempotency handling, audit logging, and the downstream payment processor mock.

### Downstream Payment Processor Mock

The downstream processor is represented by an interface and a mock implementation. The mock accepts the payment instruction, authorization context, and correlation ID, then returns a controlled outcome.

The mock will support scenarios for:

- Success, mapping to `COMPLETED`.
- Business rejection, mapping to `REJECTED` with reason details.
- Timeout, mapping to `TIMEOUT`.
- Internal failure, mapping to `FAILED`.

Scenario selection should be deterministic for automated tests and Postman debugging. The MVP will use the local/test-only `X-Mock-Scenario` header with allowed values `success`, `rejection`, `timeout`, and `internal_failure`. This header is not part of production payment business semantics and must be documented as non-production behavior in OpenAPI, Postman, and developer documentation.

Alternative considered: skip downstream integration entirely and set statuses internally. A dedicated mock is better because it exercises integration boundaries, authorization-context propagation, correlation IDs, timeout handling, and test fixtures without depending on real payment systems.

### Error Handling

The API returns consistent JSON error responses with:

- Stable error code.
- Human-readable message.
- HTTP status.
- Correlation ID.
- Field-level validation details where applicable.

Expected mappings:

- `400 Bad Request` for malformed JSON, invalid fields, unsupported payment attributes, or missing `Idempotency-Key`.
- `401 Unauthorized` for missing, expired, invalid, or unverifiable JWT.
- `403 Forbidden` for valid JWT without required scope.
- `404 Not Found` for unknown payment IDs, with care not to disclose cross-client payment existence.
- `409 Conflict` for idempotency key reuse with a different request body.
- `422 Unprocessable Entity` for semantically invalid payment instructions when syntax is valid.
- `500 Internal Server Error` for unexpected API failures.
- `502 Bad Gateway` or status-level failure handling for downstream mock internal failure, depending on whether the failure occurs before or after request acceptance.
- `504 Gateway Timeout` or status-level timeout handling for downstream timeout, depending on whether timeout occurs before or after request acceptance.

Because payment creation is asynchronous, downstream outcomes after acceptance should usually be represented in payment status rather than by changing the original `202 Accepted` response. Pre-acceptance failures should return immediate errors.

Alternative considered: expose downstream raw errors directly. Normalizing errors protects internal details, improves developer experience, and keeps the public API stable.

### Observability and Correlation ID

The API will accept an inbound `X-Correlation-ID` header when present or generate one when absent. The correlation ID is included in:

- API responses.
- `X-Correlation-ID` response headers.
- Structured application logs.
- Payment status records.
- Idempotency records.
- Downstream mock requests.
- Error responses.

Logging must avoid sensitive account data in plain text and must mask or omit full account identifiers. Operational metrics should cover request count, accepted payments, validation failures, authentication failures, authorization failures, idempotency replays, idempotency conflicts, downstream mock outcomes, latency, and status distribution.

Alternative considered: rely on platform-generated request IDs only. Explicit correlation ID handling is better for BaaS and embedded banking support because clients need a stable identifier across API calls, downstream mock behavior, and support/debug workflows.

### Postman Debugging Support

Postman support is a developer deliverable for the implementation phase. The design expects:

- A collection with payment creation and status query requests.
- Environment files for local base URL, JWT/token variable, correlation ID, idempotency key, local/test-only mock scenario, and captured `paymentId`.
- Pre-request helpers or documented variables to generate unique idempotency keys for normal creation tests.
- Saved examples for success, rejection, timeout, internal failure, invalid request, authentication failure, authorization failure, and idempotency conflict.
- Collection flows that create a payment, store `paymentId`, and query status.

Postman artifacts should align with the OpenAPI contract and mock data so client developers can debug the API without reading internal code.

Alternative considered: rely only on Swagger UI or Redoc. Rendered OpenAPI documentation is useful for contract discovery, but Postman better supports hands-on partner onboarding, request replay, environment switching, and common error scenario testing.

## Risks / Trade-offs

- In-memory or lightweight persistence may lose payment and idempotency state on restart -> Keep it explicitly MVP-scoped and hide persistence behind interfaces so durable storage can replace it later.
- Asynchronous mock processing can make tests flaky if timing is uncontrolled -> Provide deterministic mock scenarios and predictable status transitions for automated tests and Postman examples.
- Idempotency fingerprinting can produce false conflicts if request normalization is inconsistent -> Define canonical JSON normalization and include only behaviorally relevant request content in the fingerprint.
- Returning `202 Accepted` before downstream completion can confuse clients expecting final payment outcome -> Document the asynchronous model clearly and make status query examples prominent in OpenAPI and Postman.
- Mock-only controls can leak into the public API if not separated -> Keep `X-Mock-Scenario` limited to local/test use and clearly documented as non-production behavior, not payment business semantics.
- JWT validation can slow local development if token setup is cumbersome -> Provide local development token guidance and Postman environment variables while preserving authentication behavior.
- Sensitive account data could appear in logs during debugging -> Apply masking at the logging boundary and avoid logging raw payment payloads.
- Scope validation rules may change as the bank's authorization model matures -> Keep scope names and authorization checks centralized so they can evolve without changing payment orchestration.
- Downstream failures can be represented either as immediate HTTP errors or asynchronous payment statuses -> Use immediate errors only before payment acceptance; after acceptance, prefer status transitions for stable client behavior.

## Migration Plan

This is a new API capability, so no existing production migration is required.

Suggested rollout sequence for the later implementation phase:

1. Create API contract and domain/status model.
2. Add JWT and scope validation at the API boundary.
3. Add payment creation and status query orchestration with lightweight persistence.
4. Add idempotency storage and replay/conflict behavior.
5. Add downstream payment processor mock and deterministic mock scenarios.
6. Add consistent error response mapping and correlation ID propagation.
7. Add OpenAPI rendering support.
8. Add Postman collection and environment files.
9. Add contract, integration, idempotency, mock scenario, and error-path test coverage.

Rollback for MVP implementation can disable or remove the new endpoints because no existing API behavior is being changed. If persisted state is introduced later, rollback must account for orphaned payment/idempotency records created during testing.

## Open Questions

- What exact JWT issuer, audience, signing algorithm, and local development key material should be used for MVP validation?
- What retention duration should idempotency records use in MVP?
- Should downstream timeout after acceptance map only to `TIMEOUT` status, or should any synchronous timeout path return `504 Gateway Timeout` before acceptance?
