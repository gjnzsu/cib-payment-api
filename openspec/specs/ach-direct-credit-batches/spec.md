# ach-direct-credit-batches Specification

## Purpose
TBD - created by archiving change add-classic-payment-rail-simulation. Update Purpose after archive.
## Requirements
### Requirement: Accept ACH Direct Credit batch creation
The system SHALL accept one JSON ACH Direct Credit batch envelope per request at `POST /v1/ach-batches`.

#### Scenario: Valid ACH direct credit batch is accepted
- **GIVEN** an authenticated client has `ach-batches:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the request body contains a supported ACH Direct Credit batch with one or more credit entries
- **WHEN** the client submits `POST /v1/ach-batches`
- **THEN** the system SHALL create an ACH batch record owned by the authenticated client
- **AND** the system SHALL return a JSON acknowledgement containing batch ID, rail, batch status, entry count, total amount, entry status summaries, links, and correlation ID

#### Scenario: Empty ACH batch is rejected
- **GIVEN** an authenticated client submits an ACH Direct Credit batch request with no entries
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create an ACH batch record

#### Scenario: Duplicate entry reference is rejected
- **GIVEN** an authenticated client submits an ACH Direct Credit batch containing duplicate entry references
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with field-level validation details

### Requirement: Validate ACH Direct Credit batch fields
The system SHALL validate supported ACH Direct Credit batch and entry fields before creating an ACH batch record.

#### Scenario: ACH batch contains required fields
- **GIVEN** an authenticated client submits an ACH batch containing `batchReference`, `originatorName`, `effectiveEntryDate`, `settlementAccount`, and one or more `entries`
- **AND** each entry contains `entryReference`, `receiverName`, `receiverAccount`, `amount`, and `purpose`
- **WHEN** the system validates the request
- **THEN** the system SHALL allow the request to proceed to idempotency evaluation

#### Scenario: ACH batch omits required field
- **GIVEN** an authenticated client submits an ACH batch missing a required batch or entry field
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with field-level validation details

#### Scenario: ACH batch uses unsupported currency
- **GIVEN** an authenticated client submits an ACH batch containing an entry amount with a currency other than `USD`
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: ACH totals are derived from entries
- **GIVEN** an authenticated client submits a valid ACH Direct Credit batch
- **WHEN** the system creates the ACH batch acknowledgement or status response
- **THEN** the system SHALL derive entry count and total amount from the accepted entries

### Requirement: Store ACH batch and entry lifecycle status
The system SHALL store ACH batch-level status and entry-level status summaries separately from domestic RTP, RTGS, and FI payment records.

#### Scenario: ACH batch is accepted for clearing
- **GIVEN** a valid ACH Direct Credit batch passes admission
- **AND** the deterministic simulator scenario is `ach_direct_credit_accepted`
- **WHEN** the system records the ACH simulator outcome
- **THEN** the system SHALL store the batch with status `ACCEPTED_FOR_CLEARING`
- **AND** the system SHALL store each supported entry summary with status `ACCEPTED`

#### Scenario: ACH batch settles
- **GIVEN** a valid ACH Direct Credit batch passes admission
- **AND** the deterministic simulator scenario is `ach_direct_credit_settled`
- **WHEN** the system records the ACH simulator outcome
- **THEN** the system SHALL store the batch with status `SETTLED`
- **AND** the system SHALL store each supported entry summary with status `SETTLED`

#### Scenario: ACH batch is settlement pending
- **GIVEN** a valid ACH Direct Credit batch passes admission
- **AND** the deterministic simulator scenario is `ach_direct_credit_settlement_pending`
- **WHEN** the system records the ACH simulator outcome
- **THEN** the system SHALL store the batch with status `SETTLEMENT_PENDING`
- **AND** the system SHALL store entry summaries that show accepted or pending settlement state

#### Scenario: ACH batch is partially returned
- **GIVEN** a valid ACH Direct Credit batch passes admission
- **AND** the deterministic simulator scenario is `ach_direct_credit_partially_returned`
- **WHEN** the system records the ACH simulator outcome
- **THEN** the system SHALL store the batch with status `PARTIALLY_RETURNED`
- **AND** the system SHALL store at least one entry summary with status `RETURNED` and reason details
- **AND** the system SHALL preserve settled or accepted statuses for unaffected entries

#### Scenario: ACH batch is rejected before clearing
- **GIVEN** a valid ACH Direct Credit batch passes admission
- **AND** the deterministic simulator scenario is `ach_direct_credit_rejected`
- **WHEN** the system records the ACH simulator outcome
- **THEN** the system SHALL store the batch with status `REJECTED`
- **AND** the system SHALL include deterministic rejection reason details

### Requirement: Query ACH batch status
The system SHALL allow an authenticated client with `ach-batches:read` scope to query its own ACH batch status at `GET /v1/ach-batches/{batchId}`.

#### Scenario: ACH batch owner queries status
- **GIVEN** an authenticated client owns an ACH batch
- **AND** the client has `ach-batches:read` scope
- **WHEN** the client submits `GET /v1/ach-batches/{batchId}`
- **THEN** the system SHALL return a JSON status response containing batch ID, rail, batch status, entry count, total amount, entry status summaries, reason details where applicable, links, and correlation ID

#### Scenario: Unrelated client queries ACH batch
- **GIVEN** an authenticated client does not own an ACH batch
- **WHEN** the client submits `GET /v1/ach-batches/{batchId}`
- **THEN** the system SHALL reject the request without exposing unrelated ACH batch data

#### Scenario: Unknown ACH batch is queried
- **GIVEN** an authenticated client has `ach-batches:read` scope
- **WHEN** the client submits `GET /v1/ach-batches/{batchId}` for an unknown batch ID
- **THEN** the system SHALL return a consistent not-found error

### Requirement: Keep ACH Direct Debit and file formats out of runtime scope
The system SHALL NOT require ACH Direct Debit, NACHA file parsing, NACHA file generation, or ISO 20022 parsing/rendering to create or query ACH Direct Credit batches in this change.

#### Scenario: ACH Direct Credit JSON request is used
- **WHEN** a client creates an ACH Direct Credit batch in the MVP
- **THEN** the system SHALL accept supported JSON request payloads
- **AND** the system SHALL NOT require a NACHA file or ISO 20022 message as the external request format

#### Scenario: ACH Direct Debit request is outside current scope
- **GIVEN** a client attempts to use an ACH Direct Debit-specific payment type or debit collection semantics
- **WHEN** the system validates the ACH request
- **THEN** the system SHALL reject the request as unsupported by the current simulator profile

