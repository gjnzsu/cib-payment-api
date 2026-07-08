## 1. Contract and Tests

- [x] 1.1 Add mandate domain and service tests for create scenarios, status query, cancellation, ownership, and idempotency.
- [x] 1.2 Add mandate controller integration tests for authentication, scope validation, idempotency, error responses, and correlation ID propagation.
- [x] 1.3 Add collection integration and service tests for active system mandate acceptance, inactive system mandate rejection, and external mandate reference compatibility.
- [x] 1.4 Add OpenAPI contract tests for mandate endpoints, schemas, scopes, and mock scenario enum values.

## 2. Mandate Core Implementation

- [x] 2.1 Add mandate domain models for ID, profile, status, record, and cancellation rules.
- [x] 2.2 Add mandate repository, simulator port/outcome, and in-memory repository implementation.
- [x] 2.3 Add create, get status, and cancel mandate application services with idempotency and authorization context handling.
- [x] 2.4 Add deterministic mandate simulator scenarios for active, pending authorization, rejected by payer, expired, timeout, and internal failure.

## 3. API and Integration

- [x] 3.1 Add mandate request/response DTOs and validation.
- [x] 3.2 Add `MandateController` with `/v1/mandates` create, status, and cancel endpoints.
- [x] 3.3 Wire mandate repository and simulator into Spring configuration.
- [x] 3.4 Update collection creation to check system-created mandate references while preserving external reference compatibility.

## 4. Documentation and Developer Artifacts

- [x] 4.1 Update OpenAPI 3.0.3 with mandate paths, schemas, security scopes, examples, and scenario headers.
- [x] 4.2 Update Postman collection and local environment with mandate journey requests and tokens.
- [x] 4.3 Update README and developer support docs for the mandate-to-collection journey.
- [x] 4.4 Add sensitive logging coverage for mandate payloads and references.

## 5. Verification

- [x] 5.1 Run `openspec validate add-collection-mandate-simulation`.
- [x] 5.2 Run focused mandate and collection tests.
- [x] 5.3 Run the full Maven test suite.
- [x] 5.4 Review implementation against architecture constraints and update task status.
