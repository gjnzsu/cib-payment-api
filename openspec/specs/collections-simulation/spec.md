# collections-simulation Specification

## Purpose
TBD - created by archiving change add-collections-simulation-api. Update Purpose after archive.
## Requirements
### Requirement: Accept collection creation requests
The system SHALL expose `POST /v1/collections` to create one collection request using a supported pre-authorized collection simulator profile.

#### Scenario: US ACH Direct Debit collection batch is accepted
- **GIVEN** an authenticated client has `collections:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the request body contains `collectionProfile` value `US_ACH_DIRECT_DEBIT_BATCH`
- **AND** the request body contains a mandate reference, creditor, debtor, settlement account, and one or more debit entries
- **WHEN** the client submits `POST /v1/collections`
- **THEN** the system SHALL create a collection record owned by the authenticated client
- **AND** the system SHALL return a JSON acknowledgement containing collection ID, collection profile, collection status, entry count, total amount, entry status summaries, links, and correlation ID

#### Scenario: HK FPS Direct Debit collection is accepted
- **GIVEN** an authenticated client has `collections:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the request body contains `collectionProfile` value `HK_FPS_DIRECT_DEBIT`
- **AND** the request body contains a direct debit authorization reference, merchant or creditor, payer, currency, amount, and purpose
- **WHEN** the client submits `POST /v1/collections`
- **THEN** the system SHALL create a collection record owned by the authenticated client
- **AND** the system SHALL return a JSON acknowledgement containing collection ID, collection profile, collection status, amount, links, and correlation ID

#### Scenario: Unsupported collection profile is rejected
- **GIVEN** an authenticated client submits a collection request with an unsupported `collectionProfile`
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create a collection record

### Requirement: Validate collection request fields
The system SHALL validate supported collection profile fields before creating a collection record.

#### Scenario: Mandate or authorization reference is required
- **GIVEN** an authenticated client submits a collection request without a mandate reference or direct debit authorization reference
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with field-level validation details
- **AND** the system SHALL NOT create a collection record

#### Scenario: US ACH Direct Debit batch requires entries
- **GIVEN** an authenticated client submits a `US_ACH_DIRECT_DEBIT_BATCH` request with no entries
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: Duplicate US ACH Direct Debit entry references are rejected
- **GIVEN** an authenticated client submits a `US_ACH_DIRECT_DEBIT_BATCH` request containing duplicate entry references
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with field-level validation details

#### Scenario: Profile currency is unsupported
- **GIVEN** an authenticated client submits a collection request with a currency unsupported by the selected collection profile
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error

### Requirement: Store collection lifecycle status
The system SHALL store collection status separately from domestic RTP, ACH Direct Credit, RTGS, and FI payment records.

#### Scenario: US ACH Direct Debit collection is collected
- **GIVEN** a valid `US_ACH_DIRECT_DEBIT_BATCH` request passes admission
- **AND** the deterministic simulator scenario is `us_ach_debit_collected`
- **WHEN** the system records the collection simulator outcome
- **THEN** the system SHALL store the collection with status `COLLECTED`
- **AND** the system SHALL store each entry summary with status `COLLECTED`

#### Scenario: US ACH Direct Debit collection is settlement pending
- **GIVEN** a valid `US_ACH_DIRECT_DEBIT_BATCH` request passes admission
- **AND** the deterministic simulator scenario is `us_ach_debit_settlement_pending`
- **WHEN** the system records the collection simulator outcome
- **THEN** the system SHALL store the collection with status `SETTLEMENT_PENDING`
- **AND** the system SHALL store entry summaries that show pending settlement state

#### Scenario: US ACH Direct Debit collection is partially returned
- **GIVEN** a valid `US_ACH_DIRECT_DEBIT_BATCH` request passes admission
- **AND** the deterministic simulator scenario is `us_ach_debit_partially_returned`
- **WHEN** the system records the collection simulator outcome
- **THEN** the system SHALL store the collection with status `PARTIALLY_RETURNED`
- **AND** the system SHALL store at least one entry summary with status `RETURNED` and reason details

#### Scenario: HK FPS Direct Debit collection completes
- **GIVEN** a valid `HK_FPS_DIRECT_DEBIT` request passes admission
- **AND** the deterministic simulator scenario is `hk_fps_collection_completed`
- **WHEN** the system records the collection simulator outcome
- **THEN** the system SHALL store the collection with status `COMPLETED`

#### Scenario: HK FPS Direct Debit collection is pending authorization
- **GIVEN** a valid `HK_FPS_DIRECT_DEBIT` request passes admission
- **AND** the deterministic simulator scenario is `hk_fps_collection_pending_authorization`
- **WHEN** the system records the collection simulator outcome
- **THEN** the system SHALL store the collection with status `PENDING_AUTHORIZATION`
- **AND** the system SHALL include reason details indicating authorization processing is pending

#### Scenario: Collection is rejected
- **GIVEN** a valid collection request passes admission
- **AND** the deterministic simulator scenario indicates rejected authorization or insufficient funds
- **WHEN** the system records the collection simulator outcome
- **THEN** the system SHALL store the collection with status `REJECTED`
- **AND** the system SHALL include deterministic reason details

#### Scenario: Collection times out or fails internally
- **GIVEN** a valid collection request passes admission
- **AND** the deterministic simulator scenario is `collection_timeout` or `collection_internal_failure`
- **WHEN** the system records the collection simulator outcome
- **THEN** the system SHALL store the collection with status `TIMEOUT` or `FAILED`
- **AND** the system SHALL include deterministic reason details

### Requirement: Query collection status
The system SHALL expose `GET /v1/collections/{collectionId}` to allow an authenticated client with `collections:read` scope to query its own collection status.

#### Scenario: Collection owner queries status
- **GIVEN** an authenticated client owns a collection record
- **AND** the client has `collections:read` scope
- **WHEN** the client submits `GET /v1/collections/{collectionId}`
- **THEN** the system SHALL return a JSON status response containing collection ID, collection profile, status, amount or total amount, entry summaries where applicable, reason details where applicable, links, and correlation ID

#### Scenario: Unrelated client queries collection
- **GIVEN** an authenticated client does not own a collection record
- **WHEN** the client submits `GET /v1/collections/{collectionId}`
- **THEN** the system SHALL reject the request without exposing unrelated collection data

#### Scenario: Unknown collection is queried
- **GIVEN** an authenticated client has `collections:read` scope
- **WHEN** the client submits `GET /v1/collections/{collectionId}` for an unknown collection ID
- **THEN** the system SHALL return a consistent not-found error

### Requirement: Keep mandate lifecycle out of collection runtime scope
The system SHALL NOT create, amend, activate, expire, or verify mandates or eDDA authorizations inside collection creation. The system SHALL allow collection requests to use external mandate references as simulator evidence, and SHALL allow collection requests to use active mandate references created by the mandate simulation API.

#### Scenario: External authorization reference is provided
- **GIVEN** a client submits a supported collection request
- **WHEN** the request includes a mandate reference or direct debit authorization reference that does not match a system-created mandate owned by the authenticated client
- **THEN** the system SHALL treat the reference as simulator input evidence of an existing external authorization
- **AND** the system SHALL NOT create a mandate resource

#### Scenario: Active simulated mandate reference is provided
- **GIVEN** a client owns a mandate created by the mandate simulation API
- **AND** the mandate status is `ACTIVE`
- **WHEN** the client submits a supported collection request using that mandate reference
- **THEN** the system SHALL accept the mandate reference for collection simulation
- **AND** the system SHALL NOT create a new mandate resource

#### Scenario: Inactive simulated mandate reference is rejected
- **GIVEN** a client owns a mandate created by the mandate simulation API
- **AND** the mandate status is not `ACTIVE`
- **WHEN** the client submits a supported collection request using that mandate reference
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create a collection record

#### Scenario: Mandate lifecycle request is outside collection scope
- **GIVEN** a client attempts to create, amend, cancel, activate, or expire a mandate through the collections API
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request as unsupported by the current collection simulator profile
