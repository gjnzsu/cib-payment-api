## MODIFIED Requirements

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
