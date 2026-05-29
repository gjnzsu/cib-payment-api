## ADDED Requirements

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
