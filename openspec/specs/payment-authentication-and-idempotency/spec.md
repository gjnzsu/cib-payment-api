## Purpose
Define authentication, authorization, idempotency, correlation ID, error handling, and sensitive logging behavior for domestic payment API requests.

## Requirements

### Requirement: Enforce JWT Bearer authentication
The system SHALL require JWT Bearer authentication for domestic payment creation and payment status query requests.

#### Scenario: Request contains a valid JWT
- **GIVEN** a client has a valid Bearer token containing required claims `sub`, `scope`, `aud`, `iss`, and `exp`
- **WHEN** the client calls the Payment Service API
- **THEN** the system SHALL validate the token before evaluating the requested operation

#### Scenario: Request is missing or contains an invalid JWT
- **GIVEN** a client has no Bearer token or has an invalid, expired, or unverifiable token
- **WHEN** the client calls the Payment Service API
- **THEN** the system SHALL reject the request with a consistent authentication error

#### Scenario: Request token is missing required claims
- **GIVEN** a client has a Bearer token that is missing one or more required claims
- **WHEN** the client calls the Payment Service API
- **THEN** the system SHALL reject the request with a consistent authentication error

### Requirement: Enforce scope-based authorization
The system SHALL validate that the authenticated client has the required scope for the requested payment operation.

#### Scenario: Client has payment creation scope
- **GIVEN** an authenticated client has the `payments:create` scope
- **WHEN** the client calls `POST /v1/domestic-payments`
- **THEN** the system SHALL allow the request to proceed to payload and idempotency validation

#### Scenario: Client lacks payment creation scope
- **GIVEN** an authenticated client lacks the `payments:create` scope
- **WHEN** the client calls `POST /v1/domestic-payments`
- **THEN** the system SHALL reject the request with a consistent authorization error

#### Scenario: Client has payment status query scope
- **GIVEN** an authenticated client has the `payments:read` scope
- **WHEN** the client calls `GET /v1/domestic-payments/{paymentId}`
- **THEN** the system SHALL allow the request to proceed to status lookup

#### Scenario: Client lacks payment status query scope
- **GIVEN** an authenticated client lacks the `payments:read` scope
- **WHEN** the client calls `GET /v1/domestic-payments/{paymentId}`
- **THEN** the system SHALL reject the request with a consistent authorization error

### Requirement: Derive BaaS authorization context from JWT claims
The system SHALL derive Banking-as-a-Service authorization context from validated JWT claims for payment orchestration, idempotency handling, audit logging, and downstream mock calls.

#### Scenario: Token includes tenant and actor context
- **GIVEN** a valid Bearer token includes `sub`, `scope`, `tenant_id`, `actor`, `iat`, and `jti`
- **WHEN** the Payment Service API derives authorization context
- **THEN** the system SHALL include client identity, tenant identity, actor context, scopes, issued-at timestamp, and token identifier in the authorization context

#### Scenario: Token omits optional embedded banking context
- **GIVEN** a valid Bearer token includes required claims but omits `tenant_id`, `actor`, `iat`, or `jti`
- **WHEN** the Payment Service API derives authorization context
- **THEN** the system SHALL continue processing using the required client identity and scopes

### Requirement: Handle gateway-like MVP controls in the Payment Service API
The system SHALL implement MVP gateway-like controls inside the Payment Service API while the API is exposed through GKE Gateway API or Kubernetes Ingress.

#### Scenario: MVP request enters through GKE exposure layer
- **GIVEN** the MVP API is exposed through GKE Gateway API or Kubernetes Ingress
- **WHEN** a request reaches the Payment Service API
- **THEN** the Payment Service API SHALL enforce JWT validation, scope validation, correlation ID handling, request validation, and consistent error handling

#### Scenario: Custom lightweight API Gateway service is absent
- **GIVEN** the MVP is deployed on GKE
- **WHEN** payment API controls are enforced
- **THEN** the system SHALL NOT require a custom lightweight API Gateway service to enforce payment API controls

### Requirement: Require idempotency key for payment creation
The system SHALL require a valid `Idempotency-Key` header for every payment creation request.

#### Scenario: Payment creation includes idempotency key
- **GIVEN** a payment creation request includes a valid `Idempotency-Key`
- **WHEN** a client submits `POST /v1/domestic-payments`
- **THEN** the system SHALL evaluate the idempotency record before creating or replaying a payment response

#### Scenario: Payment creation omits idempotency key
- **GIVEN** a payment creation request does not include an `Idempotency-Key`
- **WHEN** a client submits `POST /v1/domestic-payments`
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: Payment status query omits idempotency key
- **GIVEN** an authenticated and authorized client submits a payment status query
- **WHEN** the client submits `GET /v1/domestic-payments/{paymentId}` without an `Idempotency-Key`
- **THEN** the system SHALL continue status query processing without requiring idempotency validation

### Requirement: Replay duplicate idempotent requests
The system SHALL return the same accepted response when the same authenticated client repeats the same payment creation request with the same `Idempotency-Key`.

#### Scenario: Same key and same request body are submitted again
- **GIVEN** an authenticated client has already submitted a payment creation request with an `Idempotency-Key`
- **AND** the client submits the same payment creation body with the same `Idempotency-Key`
- **WHEN** the system evaluates the idempotency record
- **THEN** the system SHALL return the original accepted response
- **AND** the system SHALL NOT create a second payment

#### Scenario: Duplicate request is submitted while payment is still processing
- **GIVEN** an authenticated client has an existing non-terminal payment created with an `Idempotency-Key`
- **WHEN** the client repeats the same payment creation request with the same `Idempotency-Key`
- **THEN** the system SHALL return the original accepted response
- **AND** the system SHALL preserve the existing payment record

### Requirement: Reject idempotency conflicts
The system SHALL reject payment creation when the same authenticated client reuses an `Idempotency-Key` with a different request body.

#### Scenario: Same key and different request body are submitted
- **GIVEN** an authenticated client has already submitted a payment creation request with an `Idempotency-Key`
- **WHEN** the same client submits a different payment creation body with the same `Idempotency-Key`
- **THEN** the system SHALL reject the request with `409 Conflict`

### Requirement: Scope idempotency to authenticated client
The system SHALL scope idempotency records by authenticated client identity and `Idempotency-Key`.

#### Scenario: Different clients use the same idempotency key
- **GIVEN** two different authenticated clients use the same `Idempotency-Key`
- **WHEN** both clients submit payment creation requests
- **THEN** the system SHALL evaluate each client's idempotency record independently

### Requirement: Fingerprint accepted payment requests
The system SHALL compute idempotency fingerprints from the normalized accepted business request body, authenticated client identity, and behaviorally relevant request context.

#### Scenario: Valid request is fingerprinted
- **GIVEN** an authenticated client submits a syntactically and semantically valid payment creation request
- **WHEN** the system evaluates idempotency
- **THEN** the system SHALL compute a stable fingerprint before creating or replaying an accepted payment response

#### Scenario: Invalid request is rejected before accepted idempotency storage
- **GIVEN** a payment creation request contains missing, invalid, unsupported, or unknown fields
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request before creating an accepted idempotency record

### Requirement: Propagate authorization context
The system SHALL pass authorization context to payment processing and the downstream payment processor mock.

#### Scenario: Downstream mock is called
- **GIVEN** a payment request has authenticated authorization context
- **WHEN** the Payment Service API calls the downstream payment processor mock
- **THEN** the system SHALL include relevant authorization context for future micro-authorization extension and test visibility

### Requirement: Include correlation ID in request handling
The system SHALL include or generate a correlation ID using the `X-Correlation-ID` header for request tracing across API responses, errors, payment records, idempotency records, and downstream mock calls.

#### Scenario: Client provides correlation ID
- **GIVEN** a client includes an `X-Correlation-ID` header
- **WHEN** the client submits a request
- **THEN** the system SHALL use that correlation ID in the `X-Correlation-ID` response header and response body where applicable
- **AND** the system SHALL use that correlation ID in the internal tracing context

#### Scenario: Client omits correlation ID
- **GIVEN** a client omits the `X-Correlation-ID` header
- **WHEN** the client submits a request
- **THEN** the system SHALL generate a correlation ID
- **AND** the system SHALL include the generated correlation ID in the `X-Correlation-ID` response header, response body where applicable, and internal tracing context

### Requirement: Return consistent JSON errors
The system SHALL return consistent JSON error responses for validation, authentication, authorization, idempotency conflict, not-found, and downstream failure scenarios.

#### Scenario: Error response is returned
- **GIVEN** the Payment Service API rejects or fails a request
- **WHEN** the system builds the error response
- **THEN** the system SHALL return a JSON error response containing a stable error code, message, HTTP status, and correlation ID

#### Scenario: Validation error is returned
- **GIVEN** the Payment Service API rejects a request due to invalid input
- **WHEN** the system builds the error response
- **THEN** the system SHALL include field-level validation details where applicable

### Requirement: Protect sensitive account data in logs
The system SHALL avoid logging sensitive account data in plain text and SHALL mask or omit full account identifiers.

#### Scenario: Payment request is logged
- **GIVEN** the system handles payment creation, status query, downstream mock calls, or error handling
- **WHEN** the system records logs
- **THEN** the logs SHALL omit or mask sensitive account identifiers
