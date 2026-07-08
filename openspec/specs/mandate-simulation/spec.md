# mandate-simulation Specification

## Purpose
Simulate direct debit mandate and eDDA authorization setup for collections journeys without real scheme, bank, payer notification, account validation, or production decisioning connectivity.

## Requirements
### Requirement: Create mandate simulation
The system SHALL expose `POST /v1/mandates` to create one direct debit mandate simulation request for a supported mandate profile.

#### Scenario: Mandate is created active
- **GIVEN** an authenticated client has `mandates:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the request body contains a supported mandate profile, creditor, debtor, and authorization terms
- **AND** the deterministic simulator scenario is `mandate_active`
- **WHEN** the client submits `POST /v1/mandates`
- **THEN** the system SHALL create a mandate record owned by the authenticated client
- **AND** the system SHALL return a JSON acknowledgement containing mandate ID, mandate reference, profile, status `ACTIVE`, links, and correlation ID

#### Scenario: Mandate is pending authorization
- **GIVEN** an authenticated client has `mandates:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the deterministic simulator scenario is `mandate_pending_authorization`
- **WHEN** the client submits `POST /v1/mandates`
- **THEN** the system SHALL store the mandate with status `PENDING_AUTHORIZATION`
- **AND** the system SHALL include deterministic reason details

#### Scenario: Mandate is rejected by payer
- **GIVEN** an authenticated client has `mandates:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the deterministic simulator scenario is `mandate_rejected_by_payer`
- **WHEN** the client submits `POST /v1/mandates`
- **THEN** the system SHALL store the mandate with status `REJECTED`
- **AND** the system SHALL include deterministic reason details

#### Scenario: Mandate expires during setup
- **GIVEN** an authenticated client has `mandates:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the deterministic simulator scenario is `mandate_expired`
- **WHEN** the client submits `POST /v1/mandates`
- **THEN** the system SHALL store the mandate with status `EXPIRED`
- **AND** the system SHALL include deterministic reason details

#### Scenario: Mandate creation times out or fails internally
- **GIVEN** an authenticated client has `mandates:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the deterministic simulator scenario is `mandate_timeout` or `mandate_internal_failure`
- **WHEN** the client submits `POST /v1/mandates`
- **THEN** the system SHALL store the mandate with status `TIMEOUT` or `FAILED`
- **AND** the system SHALL include deterministic reason details

### Requirement: Validate mandate creation fields
The system SHALL validate supported mandate request fields before creating a mandate record.

#### Scenario: Unsupported mandate profile is rejected
- **GIVEN** an authenticated client submits a mandate request with an unsupported `mandateProfile`
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create a mandate record

#### Scenario: Required mandate parties are missing
- **GIVEN** an authenticated client submits a mandate request without creditor or debtor details
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with field-level validation details
- **AND** the system SHALL NOT create a mandate record

#### Scenario: Mandate amount currency is unsupported
- **GIVEN** an authenticated client submits a mandate request with a currency unsupported by the selected mandate profile
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create a mandate record

### Requirement: Query mandate status
The system SHALL expose `GET /v1/mandates/{mandateId}` to allow an authenticated client with `mandates:read` scope to query its own mandate status.

#### Scenario: Mandate owner queries status
- **GIVEN** an authenticated client owns a mandate record
- **AND** the client has `mandates:read` scope
- **WHEN** the client submits `GET /v1/mandates/{mandateId}`
- **THEN** the system SHALL return a JSON status response containing mandate ID, mandate reference, profile, status, reason details where applicable, links, and correlation ID

#### Scenario: Unrelated client queries mandate
- **GIVEN** an authenticated client does not own a mandate record
- **AND** the client has `mandates:read` scope
- **WHEN** the client submits `GET /v1/mandates/{mandateId}`
- **THEN** the system SHALL reject the request without exposing unrelated mandate data

#### Scenario: Unknown mandate is queried
- **GIVEN** an authenticated client has `mandates:read` scope
- **WHEN** the client submits `GET /v1/mandates/{mandateId}` for an unknown mandate ID
- **THEN** the system SHALL return a consistent not-found error

### Requirement: Cancel mandate simulation
The system SHALL expose `POST /v1/mandates/{mandateId}/cancel` to simulate cancellation of an owned active or pending mandate.

#### Scenario: Active mandate is cancelled
- **GIVEN** an authenticated client owns an `ACTIVE` mandate record
- **AND** the client has `mandates:cancel` scope
- **AND** the request includes a valid `Idempotency-Key`
- **WHEN** the client submits `POST /v1/mandates/{mandateId}/cancel`
- **THEN** the system SHALL store the mandate with status `CANCELLED`
- **AND** the system SHALL return a JSON response containing the cancelled mandate status and correlation ID

#### Scenario: Pending mandate is cancelled
- **GIVEN** an authenticated client owns a `PENDING_AUTHORIZATION` mandate record
- **AND** the client has `mandates:cancel` scope
- **AND** the request includes a valid `Idempotency-Key`
- **WHEN** the client submits `POST /v1/mandates/{mandateId}/cancel`
- **THEN** the system SHALL store the mandate with status `CANCELLED`

#### Scenario: Terminal mandate cannot be cancelled
- **GIVEN** an authenticated client owns a mandate record with status `REJECTED`, `CANCELLED`, `EXPIRED`, `TIMEOUT`, or `FAILED`
- **AND** the client has `mandates:cancel` scope
- **AND** the request includes a valid `Idempotency-Key`
- **WHEN** the client submits `POST /v1/mandates/{mandateId}/cancel`
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT change the mandate status

### Requirement: Enforce mandate security and idempotency controls
The system SHALL require JWT Bearer authentication, scope validation, idempotency for state-changing mandate operations, correlation ID propagation, and consistent JSON errors for mandate endpoints.

#### Scenario: Missing idempotency key is rejected
- **GIVEN** an authenticated client has the required mandate scope
- **WHEN** the client submits a state-changing mandate request without `Idempotency-Key`
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: Idempotent create replay returns original mandate response
- **GIVEN** an authenticated client previously created a mandate with an `Idempotency-Key`
- **WHEN** the same authenticated client repeats the same mandate request with the same `Idempotency-Key`
- **THEN** the system SHALL return the original mandate response

#### Scenario: Idempotency conflict is rejected
- **GIVEN** an authenticated client previously used an `Idempotency-Key` for a different mandate request body
- **WHEN** the same authenticated client submits a changed mandate request with the same `Idempotency-Key`
- **THEN** the system SHALL reject the request with `409 Conflict`

#### Scenario: Mandate endpoint requires scope
- **GIVEN** an authenticated client lacks the required mandate scope
- **WHEN** the client submits a mandate request
- **THEN** the system SHALL reject the request with a consistent authorization error

### Requirement: Protect sensitive mandate data
The system SHALL avoid logging raw sensitive mandate payloads, account identifiers, bearer tokens, signing keys, and secrets.

#### Scenario: Sensitive mandate values are masked or omitted
- **GIVEN** a mandate request contains debtor account information or authorization reference values
- **WHEN** the system logs request processing or errors
- **THEN** the logs SHALL NOT contain full account identifiers, raw authorization references, bearer tokens, signing keys, or secrets
