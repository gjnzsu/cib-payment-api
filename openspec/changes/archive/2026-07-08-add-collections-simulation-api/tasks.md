## 1. Domain And Application Model

- [x] 1.1 Add collection domain models for collection ID, profile, status, entry status, reason details, collection record, and collection entry summary.
- [x] 1.2 Add application ports for collection repository and deterministic collection simulator outcomes.
- [x] 1.3 Add request fingerprint support for normalized collection creation semantics.

## 2. Collection Creation And Status Services

- [x] 2.1 Implement collection creation orchestration with authorization context, validation, idempotency replay/conflict, simulator invocation, record persistence, and response mapping.
- [x] 2.2 Implement collection status query orchestration with owner-only access and not-found behavior.
- [x] 2.3 Add deterministic simulator behavior for US ACH Direct Debit scenarios.
- [x] 2.4 Add deterministic simulator behavior for HK FPS Direct Debit scenarios.

## 3. API Layer And Security

- [x] 3.1 Add collection request and response DTOs with validation for common fields, profile-specific fields, mandate or authorization reference, entries, amounts, and currencies.
- [x] 3.2 Add `POST /v1/collections` and `GET /v1/collections/{collectionId}` controller operations.
- [x] 3.3 Add `collections:create` and `collections:read` scope enforcement.
- [x] 3.4 Ensure collection errors use the existing consistent JSON error envelope and include correlation ID.

## 4. Persistence And Observability

- [x] 4.1 Add in-memory collection repository separate from payment, ACH credit, RTGS, and FI repositories.
- [x] 4.2 Add observability and sensitive logging protections for collection account references, authorization references, and raw request payloads.

## 5. Developer Artifacts

- [x] 5.1 Update OpenAPI with collection endpoints, schemas, security scopes, examples, simulator scenarios, and simulator-only limitations.
- [x] 5.2 Update Postman collection and local environment with collection tokens, US ACH Direct Debit scenarios, HK FPS Direct Debit scenarios, idempotency replay/conflict checks, and status queries.
- [x] 5.3 Add developer support documentation comparing US ACH Direct Debit and HK FPS Direct Debit collection profiles.
- [x] 5.4 Update README with collections simulation surface and local token guidance.

## 6. Tests And Verification

- [x] 6.1 Add domain and application service tests for collection validation, lifecycle mapping, idempotency replay/conflict, and ownership behavior.
- [x] 6.2 Add controller integration tests for authentication, authorization, validation, creation, status query, deterministic outcomes, and correlation propagation.
- [x] 6.3 Add sensitive logging tests for collection payloads and authorization references.
- [x] 6.4 Add OpenAPI and Postman artifact validation coverage for collections scenarios.
- [x] 6.5 Run `openspec validate add-collections-simulation-api` and `mvn test`.
