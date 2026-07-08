## ADDED Requirements

### Requirement: Enforce collection authorization scopes
The system SHALL require collection-specific scopes for collection creation and collection status query.

#### Scenario: Client has collection creation scope
- **GIVEN** an authenticated client has the `collections:create` scope
- **WHEN** the client calls `POST /v1/collections`
- **THEN** the system SHALL allow the request to proceed to payload and idempotency validation

#### Scenario: Client lacks collection creation scope
- **GIVEN** an authenticated client lacks the `collections:create` scope
- **WHEN** the client calls `POST /v1/collections`
- **THEN** the system SHALL reject the request with a consistent authorization error

#### Scenario: Client has collection read scope
- **GIVEN** an authenticated client has the `collections:read` scope
- **WHEN** the client calls `GET /v1/collections/{collectionId}`
- **THEN** the system SHALL allow the request to proceed to ownership and status lookup

#### Scenario: Client lacks collection read scope
- **GIVEN** an authenticated client lacks the `collections:read` scope
- **WHEN** the client calls `GET /v1/collections/{collectionId}`
- **THEN** the system SHALL reject the request with a consistent authorization error

### Requirement: Require idempotency for collection creation
The system SHALL require a valid `Idempotency-Key` header for every collection creation request.

#### Scenario: Collection creation omits idempotency key
- **GIVEN** an authenticated client submits `POST /v1/collections` without an `Idempotency-Key`
- **WHEN** the system validates request headers
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: Collection status query omits idempotency key
- **GIVEN** an authenticated client submits `GET /v1/collections/{collectionId}` without an `Idempotency-Key`
- **WHEN** the system validates the request
- **THEN** the system SHALL continue status query processing without requiring idempotency validation

### Requirement: Replay duplicate collection idempotent requests
The system SHALL return the original accepted response when the same authenticated client repeats the same collection creation request with the same `Idempotency-Key`.

#### Scenario: Duplicate collection creation is replayed
- **GIVEN** an authenticated client has already submitted a collection creation request with an `Idempotency-Key`
- **AND** the client submits the same normalized collection business semantics with the same `Idempotency-Key`
- **WHEN** the system evaluates idempotency
- **THEN** the system SHALL return the original collection acknowledgement
- **AND** the system SHALL NOT create a second collection record

#### Scenario: Collection idempotency conflict is detected
- **GIVEN** an authenticated client has already submitted a collection creation request with an `Idempotency-Key`
- **WHEN** the same client submits different collection business semantics with the same `Idempotency-Key`
- **THEN** the system SHALL reject the request with `409 Conflict`

### Requirement: Propagate collection correlation and ownership context
The system SHALL propagate authorization context and correlation ID through collection creation, status query, simulator calls, idempotency records, logs, and responses.

#### Scenario: Collection request includes correlation ID
- **GIVEN** an authenticated client includes an `X-Correlation-ID` header
- **WHEN** the client submits a collection request
- **THEN** the system SHALL use that correlation ID in response headers, response bodies where applicable, records, idempotency entries, logs, and simulator calls

#### Scenario: Collection request omits correlation ID
- **GIVEN** an authenticated client omits `X-Correlation-ID`
- **WHEN** the client submits a collection request
- **THEN** the system SHALL generate a correlation ID and propagate it through response headers, response bodies where applicable, records, idempotency entries, logs, and simulator calls

#### Scenario: Client accesses unrelated collection record
- **GIVEN** an authenticated client attempts to query a collection record owned by another client
- **WHEN** the system evaluates ownership
- **THEN** the system SHALL reject the request without exposing unrelated collection data

### Requirement: Protect collection sensitive data in logs
The system SHALL avoid logging bearer tokens, raw sensitive payloads, full account identifiers, settlement account references, debtor accounts, creditor accounts, mandate references, or direct debit authorization references for collection requests.

#### Scenario: Collection request is handled
- **GIVEN** the system receives supported or invalid collection content
- **WHEN** the system records logs or error diagnostics
- **THEN** the logs and diagnostics SHALL NOT contain bearer tokens, raw sensitive payloads, full account identifiers, or full authorization references

#### Scenario: Collection simulator outcome is logged
- **GIVEN** the system records collection simulator outcome context
- **WHEN** the log includes collection, mandate, debtor, or creditor context
- **THEN** the system SHALL mask or omit full sensitive values
