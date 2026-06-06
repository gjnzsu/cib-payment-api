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
The system SHALL compute idempotency fingerprints from the normalized accepted `pain.001.001.09` business semantics, authenticated client identity, and behaviorally relevant request context.

#### Scenario: Valid pain.001 request is fingerprinted
- **GIVEN** an authenticated client submits a syntactically and semantically valid supported `pain.001.001.09` payment creation request
- **WHEN** the system evaluates idempotency
- **THEN** the system SHALL compute a stable fingerprint from the normalized payment initiation semantics before invoking the Payment Engine or replaying an accepted payment response

#### Scenario: Equivalent XML formatting is submitted again
- **GIVEN** an authenticated client has already submitted a supported `pain.001.001.09` request with an `Idempotency-Key`
- **AND** the client submits the same payment semantics with XML formatting differences that do not change the accepted business request
- **WHEN** the system evaluates the idempotency record
- **THEN** the Edge API SHALL treat the request as an idempotent replay and return the original `pain.002` response

#### Scenario: Invalid request is rejected before accepted idempotency storage
- **GIVEN** a payment creation request contains missing, invalid, unsupported, or unknown fields
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request before creating an accepted idempotency record or invoking the Payment Engine

### Requirement: Propagate authorization context
The system SHALL pass authorization context to payment processing and the downstream payment processor mock.

#### Scenario: Downstream mock is called
- **GIVEN** a payment request has authenticated authorization context
- **WHEN** the Payment Service API calls the downstream payment processor mock
- **THEN** the system SHALL include relevant authorization context for future micro-authorization extension and test visibility

### Requirement: Include correlation ID in request handling
The system SHALL include or generate a correlation ID using the `X-Correlation-ID` header for request tracing across API responses, errors, payment records, idempotency records, payment engine processing, internal ISO mapping, and clearing and settlement simulator calls.

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

#### Scenario: ISO mapping and simulator are called
- **GIVEN** a supported `pain.001` payment initiation is accepted
- **WHEN** the system maps the initiation to internal `pacs.008` and calls the clearing and settlement simulator
- **THEN** the same correlation ID SHALL be available to mapping, simulator processing, logs, records, and responses

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
The system SHALL avoid logging sensitive account data, ISO payloads, FPS proxy values, HKID-like identifiers, and bearer tokens in plain text and SHALL mask or omit full sensitive identifiers.

#### Scenario: Payment request is logged
- **GIVEN** the system handles payment creation, status query, downstream mock calls, payment engine mapping, simulator calls, or error handling
- **WHEN** the system records logs
- **THEN** the logs SHALL omit or mask sensitive account identifiers

#### Scenario: ISO XML request is handled
- **GIVEN** the system receives a supported or invalid ISO 20022 XML payment initiation
- **WHEN** the system records logs or error diagnostics
- **THEN** the logs and diagnostics SHALL NOT contain raw sensitive XML payloads or full sensitive identifiers

### Requirement: Enforce FI payment authorization scopes
The system SHALL require FI-specific scopes for FI payment creation, FI payment status query, and FI recall or investigation requests.

#### Scenario: Client has FI payment creation scope
- **GIVEN** an authenticated client has the `fi-payments:create` scope
- **WHEN** the client calls `POST /v1/fi-payments`
- **THEN** the system SHALL allow the request to proceed to payload and idempotency validation

#### Scenario: Client lacks FI payment creation scope
- **GIVEN** an authenticated client lacks the `fi-payments:create` scope
- **WHEN** the client calls `POST /v1/fi-payments`
- **THEN** the system SHALL reject the request with a consistent authorization error

#### Scenario: Client has FI payment read scope
- **GIVEN** an authenticated client has the `fi-payments:read` scope
- **WHEN** the client calls `GET /v1/fi-payments/{paymentId}`
- **THEN** the system SHALL allow the request to proceed to ownership and status lookup

#### Scenario: Client lacks FI payment read scope
- **GIVEN** an authenticated client lacks the `fi-payments:read` scope
- **WHEN** the client calls `GET /v1/fi-payments/{paymentId}`
- **THEN** the system SHALL reject the request with a consistent authorization error

#### Scenario: Client has FI investigation scope
- **GIVEN** an authenticated client has the `fi-payments:investigate` scope
- **WHEN** the client calls `POST /v1/fi-payments/{paymentId}/recall-requests`
- **THEN** the system SHALL allow the request to proceed to ownership, payload, and idempotency validation

#### Scenario: Client lacks FI investigation scope
- **GIVEN** an authenticated client lacks the `fi-payments:investigate` scope
- **WHEN** the client calls `POST /v1/fi-payments/{paymentId}/recall-requests`
- **THEN** the system SHALL reject the request with a consistent authorization error

### Requirement: Require idempotency for FI state-changing requests
The system SHALL require a valid `Idempotency-Key` header for FI payment creation and FI recall or investigation requests.

#### Scenario: FI payment creation omits idempotency key
- **GIVEN** an authenticated FI client submits `POST /v1/fi-payments` without an `Idempotency-Key`
- **WHEN** the system validates the request headers
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: FI recall request omits idempotency key
- **GIVEN** an authenticated FI client submits `POST /v1/fi-payments/{paymentId}/recall-requests` without an `Idempotency-Key`
- **WHEN** the system validates the request headers
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: FI payment status query omits idempotency key
- **GIVEN** an authenticated FI client submits `GET /v1/fi-payments/{paymentId}` without an `Idempotency-Key`
- **WHEN** the system validates the request
- **THEN** the system SHALL continue status query processing without requiring idempotency validation

### Requirement: Replay duplicate FI idempotent requests
The system SHALL return the original accepted response when the same authenticated FI client repeats the same FI state-changing request with the same `Idempotency-Key`.

#### Scenario: Duplicate FI payment creation is replayed
- **GIVEN** an authenticated FI client has already submitted an FI payment creation request with an `Idempotency-Key`
- **AND** the client submits the same normalized `pacs.009` business semantics with the same `Idempotency-Key`
- **WHEN** the system evaluates idempotency
- **THEN** the system SHALL return the original FI payment acknowledgement
- **AND** the system SHALL NOT create a second FI payment

#### Scenario: Duplicate FI recall request is replayed
- **GIVEN** an authenticated FI client has already submitted a supported `camt.056` recall request with an `Idempotency-Key`
- **AND** the client submits the same normalized recall request semantics with the same `Idempotency-Key`
- **WHEN** the system evaluates idempotency
- **THEN** the system SHALL return the original `camt.029` response
- **AND** the system SHALL NOT create a second recall or investigation record

#### Scenario: FI idempotency conflict is detected
- **GIVEN** an authenticated FI client has already submitted an FI state-changing request with an `Idempotency-Key`
- **WHEN** the same client submits different FI payment or recall semantics with the same `Idempotency-Key`
- **THEN** the system SHALL reject the request with `409 Conflict`

### Requirement: Propagate FI correlation and ownership context
The system SHALL propagate authorization context and correlation ID through FI payment creation, FI status query, recall request handling, simulator calls, idempotency records, logs, and responses.

#### Scenario: FI payment request includes correlation ID
- **GIVEN** an authenticated FI client includes an `X-Correlation-ID` header
- **WHEN** the client submits an FI payment or recall request
- **THEN** the system SHALL use that correlation ID in response headers, records, idempotency entries, logs, and simulator calls

#### Scenario: FI payment request omits correlation ID
- **GIVEN** an authenticated FI client omits `X-Correlation-ID`
- **WHEN** the client submits an FI payment or recall request
- **THEN** the system SHALL generate a correlation ID and propagate it through response headers, records, idempotency entries, logs, and simulator calls

#### Scenario: FI client accesses unrelated FI record
- **GIVEN** an authenticated FI client attempts to query or recall an FI payment owned by another client
- **WHEN** the system evaluates ownership
- **THEN** the system SHALL reject the request without exposing unrelated FI payment or investigation data

### Requirement: Protect FI sensitive data in logs
The system SHALL avoid logging raw FI ISO XML payloads, bearer tokens, full account references, full BIC-linked account references, or sensitive correspondent account data.

#### Scenario: FI XML request is handled
- **GIVEN** the system receives supported or invalid `pacs.009`, `camt.056`, or `camt.029` XML content
- **WHEN** the system records logs or error diagnostics
- **THEN** the logs and diagnostics SHALL NOT contain raw sensitive XML payloads or full sensitive identifiers

#### Scenario: Correspondent account reference is logged
- **GIVEN** the system logs FI payment or investigation context
- **WHEN** the log includes simulated correspondent account reference information
- **THEN** the system SHALL mask or omit the full account reference

### Requirement: Enforce ACH and RTGS authorization scopes
The system SHALL require rail-specific scopes for ACH batch creation, ACH batch status query, RTGS payment creation, and RTGS payment status query.

#### Scenario: Client has ACH batch creation scope
- **GIVEN** an authenticated client has the `ach-batches:create` scope
- **WHEN** the client calls `POST /v1/ach-batches`
- **THEN** the system SHALL allow the request to proceed to payload and idempotency validation

#### Scenario: Client lacks ACH batch creation scope
- **GIVEN** an authenticated client lacks the `ach-batches:create` scope
- **WHEN** the client calls `POST /v1/ach-batches`
- **THEN** the system SHALL reject the request with a consistent authorization error

#### Scenario: Client has ACH batch read scope
- **GIVEN** an authenticated client has the `ach-batches:read` scope
- **WHEN** the client calls `GET /v1/ach-batches/{batchId}`
- **THEN** the system SHALL allow the request to proceed to ownership and status lookup

#### Scenario: Client lacks ACH batch read scope
- **GIVEN** an authenticated client lacks the `ach-batches:read` scope
- **WHEN** the client calls `GET /v1/ach-batches/{batchId}`
- **THEN** the system SHALL reject the request with a consistent authorization error

#### Scenario: Client has RTGS payment creation scope
- **GIVEN** an authenticated client has the `rtgs-payments:create` scope
- **WHEN** the client calls `POST /v1/rtgs-payments`
- **THEN** the system SHALL allow the request to proceed to payload and idempotency validation

#### Scenario: Client lacks RTGS payment creation scope
- **GIVEN** an authenticated client lacks the `rtgs-payments:create` scope
- **WHEN** the client calls `POST /v1/rtgs-payments`
- **THEN** the system SHALL reject the request with a consistent authorization error

#### Scenario: Client has RTGS payment read scope
- **GIVEN** an authenticated client has the `rtgs-payments:read` scope
- **WHEN** the client calls `GET /v1/rtgs-payments/{paymentId}`
- **THEN** the system SHALL allow the request to proceed to ownership and status lookup

#### Scenario: Client lacks RTGS payment read scope
- **GIVEN** an authenticated client lacks the `rtgs-payments:read` scope
- **WHEN** the client calls `GET /v1/rtgs-payments/{paymentId}`
- **THEN** the system SHALL reject the request with a consistent authorization error

### Requirement: Require idempotency for ACH and RTGS creation
The system SHALL require a valid `Idempotency-Key` header for ACH batch creation and RTGS payment creation.

#### Scenario: ACH batch creation omits idempotency key
- **GIVEN** an authenticated client submits `POST /v1/ach-batches` without an `Idempotency-Key`
- **WHEN** the system validates request headers
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: RTGS payment creation omits idempotency key
- **GIVEN** an authenticated client submits `POST /v1/rtgs-payments` without an `Idempotency-Key`
- **WHEN** the system validates request headers
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: ACH batch status query omits idempotency key
- **GIVEN** an authenticated client submits `GET /v1/ach-batches/{batchId}` without an `Idempotency-Key`
- **WHEN** the system validates the request
- **THEN** the system SHALL continue status query processing without requiring idempotency validation

#### Scenario: RTGS payment status query omits idempotency key
- **GIVEN** an authenticated client submits `GET /v1/rtgs-payments/{paymentId}` without an `Idempotency-Key`
- **WHEN** the system validates the request
- **THEN** the system SHALL continue status query processing without requiring idempotency validation

### Requirement: Replay duplicate ACH and RTGS idempotent requests
The system SHALL return the original accepted response when the same authenticated client repeats the same ACH or RTGS creation request with the same `Idempotency-Key`.

#### Scenario: Duplicate ACH batch creation is replayed
- **GIVEN** an authenticated client has already submitted an ACH batch creation request with an `Idempotency-Key`
- **AND** the client submits the same ACH batch business semantics with the same `Idempotency-Key`
- **WHEN** the system evaluates idempotency
- **THEN** the system SHALL return the original ACH batch acknowledgement
- **AND** the system SHALL NOT create a second ACH batch

#### Scenario: Duplicate RTGS payment creation is replayed
- **GIVEN** an authenticated client has already submitted an RTGS payment creation request with an `Idempotency-Key`
- **AND** the client submits the same RTGS payment business semantics with the same `Idempotency-Key`
- **WHEN** the system evaluates idempotency
- **THEN** the system SHALL return the original RTGS payment acknowledgement
- **AND** the system SHALL NOT create a second RTGS payment

#### Scenario: ACH idempotency conflict is detected
- **GIVEN** an authenticated client has already submitted an ACH batch creation request with an `Idempotency-Key`
- **WHEN** the same client submits different ACH batch semantics with the same `Idempotency-Key`
- **THEN** the system SHALL reject the request with `409 Conflict`

#### Scenario: RTGS idempotency conflict is detected
- **GIVEN** an authenticated client has already submitted an RTGS payment creation request with an `Idempotency-Key`
- **WHEN** the same client submits different RTGS payment semantics with the same `Idempotency-Key`
- **THEN** the system SHALL reject the request with `409 Conflict`

### Requirement: Propagate ACH and RTGS correlation and ownership context
The system SHALL propagate authorization context and correlation ID through ACH and RTGS creation, status query, simulator calls, idempotency records, logs, and responses.

#### Scenario: ACH or RTGS request includes correlation ID
- **GIVEN** an authenticated client includes an `X-Correlation-ID` header
- **WHEN** the client submits an ACH or RTGS request
- **THEN** the system SHALL use that correlation ID in response headers, response bodies where applicable, records, idempotency entries, logs, and simulator calls

#### Scenario: ACH or RTGS request omits correlation ID
- **GIVEN** an authenticated client omits `X-Correlation-ID`
- **WHEN** the client submits an ACH or RTGS request
- **THEN** the system SHALL generate a correlation ID and propagate it through response headers, response bodies where applicable, records, idempotency entries, logs, and simulator calls

#### Scenario: Client accesses unrelated ACH or RTGS record
- **GIVEN** an authenticated client attempts to query an ACH batch or RTGS payment owned by another client
- **WHEN** the system evaluates ownership
- **THEN** the system SHALL reject the request without exposing unrelated ACH or RTGS data

### Requirement: Protect ACH and RTGS sensitive data in logs
The system SHALL avoid logging bearer tokens, raw sensitive payloads, full account identifiers, settlement account references, receiver account references, debtor accounts, or creditor accounts for ACH and RTGS requests.

#### Scenario: ACH request is handled
- **GIVEN** the system receives supported or invalid ACH batch content
- **WHEN** the system records logs or error diagnostics
- **THEN** the logs and diagnostics SHALL NOT contain bearer tokens, raw sensitive payloads, or full account identifiers

#### Scenario: RTGS request is handled
- **GIVEN** the system receives supported or invalid RTGS payment content
- **WHEN** the system records logs or error diagnostics
- **THEN** the logs and diagnostics SHALL NOT contain bearer tokens, raw sensitive payloads, or full debtor or creditor account identifiers

