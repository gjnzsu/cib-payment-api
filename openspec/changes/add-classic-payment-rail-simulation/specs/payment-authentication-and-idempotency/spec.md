## ADDED Requirements

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
