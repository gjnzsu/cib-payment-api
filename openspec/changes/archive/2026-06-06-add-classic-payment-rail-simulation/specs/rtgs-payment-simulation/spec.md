## ADDED Requirements

### Requirement: Accept RTGS payment creation
The system SHALL accept one JSON RTGS payment request per request at `POST /v1/rtgs-payments`.

#### Scenario: Corporate RTGS payment is accepted
- **GIVEN** an authenticated client has `rtgs-payments:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the request body contains a supported corporate RTGS payment
- **WHEN** the client submits `POST /v1/rtgs-payments`
- **THEN** the system SHALL create an RTGS payment record owned by the authenticated client
- **AND** the system SHALL return a JSON acknowledgement containing payment ID, rail, client segment, status, settlement finality, links, and correlation ID

#### Scenario: FI RTGS payment is accepted
- **GIVEN** an authenticated FI client has `rtgs-payments:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the request body contains a supported FI RTGS payment with instructing and instructed agent identifiers
- **WHEN** the client submits `POST /v1/rtgs-payments`
- **THEN** the system SHALL create an RTGS payment record owned by the authenticated FI client
- **AND** the system SHALL return a JSON acknowledgement containing payment ID, rail, client segment `FI`, status, settlement finality, links, and correlation ID

#### Scenario: Unsupported client segment is rejected
- **GIVEN** an authenticated client submits an RTGS payment with an unsupported client segment
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create an RTGS payment record

#### Scenario: Corporate RTGS request is missing party fields
- **GIVEN** an authenticated client submits an RTGS request with client segment `CORPORATE`
- **AND** the request is missing required debtor or creditor fields
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with field-level validation details

#### Scenario: FI RTGS request is missing FI agent identifiers
- **GIVEN** an authenticated client submits an RTGS request with client segment `FI`
- **AND** the request is missing required instructing or instructed agent identifiers
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with field-level validation details

### Requirement: Validate RTGS payment fields
The system SHALL validate supported RTGS payment fields before creating an RTGS payment record.

#### Scenario: Corporate RTGS request contains required fields
- **GIVEN** an authenticated client submits an RTGS request with client segment `CORPORATE`
- **AND** the request contains `paymentReference`, debtor account, creditor account, `amount`, `requestedSettlementDate`, `settlementPriority`, and `purpose`
- **WHEN** the system validates the request
- **THEN** the system SHALL allow the request to proceed to idempotency evaluation

#### Scenario: FI RTGS request contains required fields
- **GIVEN** an authenticated client submits an RTGS request with client segment `FI`
- **AND** the request contains `paymentReference`, `instructingAgentBic`, `instructedAgentBic`, `amount`, `requestedSettlementDate`, `settlementPriority`, and `purpose`
- **WHEN** the system validates the request
- **THEN** the system SHALL allow the request to proceed to idempotency evaluation

#### Scenario: RTGS request uses unsupported currency
- **GIVEN** an authenticated client submits an RTGS request containing an amount with a currency other than `USD`
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: RTGS request has no hard high-value threshold
- **GIVEN** an authenticated client submits an RTGS request with a valid positive USD amount
- **WHEN** the system validates the amount
- **THEN** the system SHALL NOT reject the request solely because it is below a simulator high-value threshold

### Requirement: Store RTGS lifecycle and settlement finality
The system SHALL store RTGS payment lifecycle status and settlement finality separately from domestic RTP, ACH, and FI correspondent payment records.

#### Scenario: RTGS payment settles with finality
- **GIVEN** a valid RTGS payment passes admission
- **AND** the deterministic simulator scenario is `rtgs_settled`
- **WHEN** the system records the RTGS simulator outcome
- **THEN** the system SHALL store the RTGS payment with status `SETTLED`
- **AND** the system SHALL mark settlement finality as true

#### Scenario: RTGS payment queues for liquidity
- **GIVEN** a valid RTGS payment passes admission
- **AND** the deterministic simulator scenario is `rtgs_queued_for_liquidity`
- **WHEN** the system records the RTGS simulator outcome
- **THEN** the system SHALL store the RTGS payment with status `QUEUED_FOR_LIQUIDITY`
- **AND** the system SHALL mark settlement finality as false
- **AND** the system SHALL include deterministic liquidity queue reason details

#### Scenario: RTGS payment is rejected before settlement
- **GIVEN** a valid RTGS payment passes admission
- **AND** the deterministic simulator scenario is `rtgs_rejected`
- **WHEN** the system records the RTGS simulator outcome
- **THEN** the system SHALL store the RTGS payment with status `REJECTED`
- **AND** the system SHALL mark settlement finality as false
- **AND** the system SHALL include deterministic rejection reason details

### Requirement: Query RTGS payment status
The system SHALL allow an authenticated client with `rtgs-payments:read` scope to query its own RTGS payment status at `GET /v1/rtgs-payments/{paymentId}`.

#### Scenario: RTGS payment owner queries status
- **GIVEN** an authenticated client owns an RTGS payment
- **AND** the client has `rtgs-payments:read` scope
- **WHEN** the client submits `GET /v1/rtgs-payments/{paymentId}`
- **THEN** the system SHALL return a JSON status response containing payment ID, rail, client segment, lifecycle status, settlement finality, reason details where applicable, links, and correlation ID

#### Scenario: Unrelated client queries RTGS payment
- **GIVEN** an authenticated client does not own an RTGS payment
- **WHEN** the client submits `GET /v1/rtgs-payments/{paymentId}`
- **THEN** the system SHALL reject the request without exposing unrelated RTGS payment data

#### Scenario: Unknown RTGS payment is queried
- **GIVEN** an authenticated client has `rtgs-payments:read` scope
- **WHEN** the client submits `GET /v1/rtgs-payments/{paymentId}` for an unknown payment ID
- **THEN** the system SHALL return a consistent not-found error

### Requirement: Keep RTGS independent from FI correspondent runtime
The system SHALL keep RTGS payment simulation independent from FI correspondent payment runtime processing.

#### Scenario: FI RTGS payment is created
- **GIVEN** an authenticated FI client creates an RTGS payment
- **WHEN** the system processes the RTGS request
- **THEN** the system SHALL NOT automatically create an FI correspondent payment
- **AND** the system SHALL NOT invoke the FI correspondent simulator

#### Scenario: FI correspondent capability is referenced as related
- **GIVEN** an FI RTGS scenario is documented or returned with related capability guidance
- **WHEN** the system describes FI correspondent payment
- **THEN** the system SHALL describe it as a separate FI correspondent arrangement using correspondent account context
- **AND** the system SHALL NOT classify FI correspondent payment as RTP, ACH, or RTGS

### Requirement: Keep ISO 20022 and real RTGS infrastructure out of runtime scope
The system SHALL NOT require ISO 20022 parsing/rendering, real central bank ledger, real liquidity reservation, queue optimization, cancellation, amendment, or cross-border RTGS behavior for RTGS payment simulation in this change.

#### Scenario: RTGS JSON request is used
- **WHEN** a client creates an RTGS payment in the MVP
- **THEN** the system SHALL accept supported JSON request payloads
- **AND** the system SHALL NOT require ISO 20022 XML as the external request format

#### Scenario: Liquidity queue is deterministic
- **GIVEN** a request selects the `rtgs_queued_for_liquidity` simulator scenario
- **WHEN** the RTGS simulator processes the request
- **THEN** the queued outcome SHALL be deterministic
- **AND** the system SHALL NOT calculate real account liquidity or optimize a real settlement queue
