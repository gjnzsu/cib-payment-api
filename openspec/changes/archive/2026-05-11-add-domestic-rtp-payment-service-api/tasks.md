## 1. Project and API Foundation

- [x] 1.1 Create the Payment Service API project scaffold with a layered structure for controllers, services, persistence adapters, downstream clients, configuration, and tests
- [x] 1.2 Add runtime and test dependencies for HTTP API handling, OpenAPI rendering, JWT validation, request validation, structured logging, and automated testing
- [x] 1.3 Configure local application settings for API port, JWT issuer, JWT audience `domestic-payment-api`, signing key or JWKS source, downstream mock mode, and persistence mode
- [x] 1.4 Add a health or readiness endpoint suitable for local development and GKE exposure checks

## 2. OpenAPI Contract and Models

- [x] 2.1 Create the OpenAPI 3.0.3 contract for `POST /v1/domestic-payments` and `GET /v1/domestic-payments/{paymentId}`
- [x] 2.2 Define request and response schemas for a single domestic real-time payment instruction, accepted payment response, payment status response, and consistent JSON error response
- [x] 2.3 Define required headers in the OpenAPI contract, including `Authorization`, `Idempotency-Key` for payment creation, optional inbound and returned `X-Correlation-ID`, and local/test-only `X-Mock-Scenario`
- [x] 2.4 Define API error responses for validation, authentication, authorization, not found, idempotency conflict, downstream failure, timeout, and unexpected error scenarios
- [x] 2.5 Add Swagger UI, Redoc, or equivalent local rendering for the OpenAPI contract

## 3. Authentication, Authorization, and Gateway-Like Controls

- [x] 3.1 Implement JWT Bearer token validation for signature, issuer, audience, expiry, subject, and required claims
- [x] 3.2 Derive Banking-as-a-Service authorization context from `sub`, `scope`, `tenant_id`, `actor`, `iat`, and `jti` claims when present
- [x] 3.3 Enforce `payments:create` scope for `POST /v1/domestic-payments`
- [x] 3.4 Enforce `payments:read` scope for `GET /v1/domestic-payments/{paymentId}`
- [x] 3.5 Implement MVP gateway-like controls inside the Payment Service API, including authentication, scope validation, correlation ID handling, request validation, and consistent error handling
- [x] 3.6 Add local JWT test token support for valid, expired, missing-claim, missing-scope, and invalid-signature scenarios

## 4. Payment Creation and Validation

- [x] 4.1 Implement the payment creation controller for `POST /v1/domestic-payments`
- [x] 4.2 Validate that each request contains exactly one domestic real-time payment instruction using a JSON business payload
- [x] 4.3 Reject cross-border, batch, recurring, cancellation, amendment, non-real-time, malformed, or unsupported payment requests with consistent validation errors
- [x] 4.4 Generate a unique UUID string `paymentId` for accepted payment requests
- [x] 4.5 Return `202 Accepted` with `paymentId`, current status, timestamps, correlation ID, and `links.status` for accepted payment requests

## 5. Idempotency

- [x] 5.1 Implement an MVP idempotency record store scoped by authenticated client identity and `Idempotency-Key`
- [x] 5.2 Compute a stable request fingerprint from the normalized accepted business request body, authenticated client identity, and behaviorally relevant request context
- [x] 5.3 Require `Idempotency-Key` for every payment creation request and reject missing keys with a consistent validation error
- [x] 5.4 Return the original accepted response when the same client repeats the same request body with the same `Idempotency-Key`
- [x] 5.5 Return `409 Conflict` when the same client reuses an `Idempotency-Key` with a different request body
- [x] 5.6 Ensure concurrent duplicate payment creation requests resolve to one payment record and one idempotency record

## 6. Async Payment Status and Persistence

- [x] 6.1 Implement an MVP payment status record store for `paymentId`, status, timestamps, correlation ID, client context, and reason details
- [x] 6.2 Implement the payment status lifecycle for `ACCEPTED`, `PROCESSING`, `COMPLETED`, `REJECTED`, `FAILED`, and `TIMEOUT`
- [x] 6.3 Implement the status query controller for `GET /v1/domestic-payments/{paymentId}`
- [x] 6.4 Return `paymentId`, current status, timestamps, correlation ID, optional reason details, and `links.self` for authorized status query requests
- [x] 6.5 Return a consistent not-found error for unknown or inaccessible payment IDs without exposing unrelated payment data

## 7. Downstream Payment Processor Mock

- [x] 7.1 Create a downstream payment processor client interface and local mock implementation
- [x] 7.2 Pass payment instruction, authorization context, and correlation ID to the downstream mock
- [x] 7.3 Implement deterministic local/test-only `X-Mock-Scenario` scenarios for success, business rejection, timeout, and internal failure
- [x] 7.4 Map downstream success to `COMPLETED`, business rejection to `REJECTED`, timeout to `TIMEOUT`, and internal failure to `FAILED`
- [x] 7.5 Add mock payment request fixtures for success, rejection, timeout, internal failure, invalid request, and idempotency scenarios

## 8. Observability and Error Handling

- [x] 8.1 Accept an inbound `X-Correlation-ID` header when present and generate a correlation ID when absent
- [x] 8.2 Include correlation ID in API responses, error responses, payment status records, idempotency records, logs, and downstream mock calls
- [x] 8.3 Implement consistent JSON error response mapping for validation, authentication, authorization, not found, idempotency conflict, downstream failure, timeout, and unexpected errors
- [x] 8.4 Add structured logging for payment creation, status query, idempotency decisions, downstream mock outcomes, and errors
- [x] 8.5 Mask or omit sensitive account identifiers and raw sensitive payment data from logs
- [x] 8.6 Add basic operational metrics for request count, accepted payments, auth failures, validation failures, idempotency replays, idempotency conflicts, downstream outcomes, latency, and status distribution

## 9. GKE Exposure Configuration

- [x] 9.1 Add Kubernetes manifests or deployment configuration for the Payment Service API workload and service
- [x] 9.2 Add GKE Gateway API or Kubernetes Ingress configuration to expose the MVP API without a custom lightweight API Gateway service
- [x] 9.3 Configure readiness and liveness checks for the GKE deployment path
- [x] 9.4 Document local and GKE exposure assumptions for future migration to a dedicated API management platform such as Apigee or Kong

## 10. Developer Support Artifacts

- [x] 10.1 Create a Postman collection for payment creation, payment status query, and common error scenarios
- [x] 10.2 Create a Postman local environment with variables for base URL, JWT token, correlation ID, idempotency key, payment ID, and local/test-only mock scenario selection
- [x] 10.3 Add Postman examples for success, rejection, timeout, internal failure, invalid request, authentication failure, authorization failure, and idempotency conflict
- [x] 10.4 Add local developer documentation for running the API, rendering OpenAPI docs, generating or using test JWTs, using mock scenarios, and running the Postman collection

## 11. Automated Verification

- [x] 11.1 Add contract tests covering the OpenAPI-defined payment creation, status query, and error responses
- [x] 11.2 Add authentication and authorization tests for valid JWTs, invalid JWTs, missing required claims, `payments:create`, and `payments:read`
- [x] 11.3 Add idempotency tests for missing key, first request, same key and same body replay, same key and different body conflict, client-scoped keys, and concurrent duplicates
- [x] 11.4 Add payment lifecycle tests for accepted, processing, completed, rejected, failed, timeout, and unknown payment statuses
- [x] 11.5 Add downstream mock integration tests for success, rejection, timeout, and internal failure scenarios
- [x] 11.6 Add observability tests or assertions for correlation ID propagation and sensitive account data masking
- [x] 11.7 Add Postman and fixture validation to ensure developer artifacts match the OpenAPI contract and behavior specs
- [x] 11.8 Run the project test suite and confirm contract and key integration tests pass
- [x] 11.9 Run `openspec validate add-domestic-rtp-payment-service-api` and confirm the OpenSpec change remains valid

## 12. Local JWT and Postman Usability Hardening

- [x] 12.1 Add local-only JWT generation support or documented helper workflow for Postman tokens that match the local JWT decoder configuration
- [x] 12.2 Verify and harden authentication and authorization error response bodies so `401` and `403` responses align with the OpenAPI contract and Postman examples
