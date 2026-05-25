# hk-payment-engine-mapping Specification

## Purpose
TBD - created by archiving change add-hk-iso20022-payment-simulation. Update Purpose after archive.
## Requirements
### Requirement: Route admitted pain.001 candidates through Payment Engine
The system SHALL route admitted HK `pain.001.001.09` payment candidates through a Payment Engine boundary before clearing and settlement simulation.

#### Scenario: Engine receives normalized initiation
- **GIVEN** the Edge/API admission layer has accepted and normalized a supported `pain.001.001.09` request
- **WHEN** payment orchestration begins
- **THEN** the Payment Engine SHALL receive the normalized payment candidate, authorization context, correlation ID, idempotency context, and local/test scenario context

### Requirement: Own payment records in Payment Engine
The Payment Engine SHALL create and own payment records for admitted ISO payment candidates.

#### Scenario: Payment candidate enters engine
- **GIVEN** an admitted ISO payment candidate is submitted through the Payment Engine initiation port
- **WHEN** the Payment Engine starts processing
- **THEN** the Payment Engine SHALL create the payment record and own lifecycle status, latest `pain.002`, and status truth for that payment

#### Scenario: Edge does not create payment record
- **GIVEN** the Edge/API admission layer admits a supported ISO payment candidate
- **WHEN** the Edge calls the Payment Engine initiation port
- **THEN** the Edge SHALL NOT directly create or update the payment record

### Requirement: Map pain.001 to internal pacs.008 representation
The Payment Engine SHALL map an admitted customer credit transfer initiation to an internal `pacs.008` interbank transfer representation.

#### Scenario: Internal pacs.008 is produced
- **GIVEN** a normalized HKD `pain.001` initiation satisfies the supported HK profile
- **WHEN** the payment engine prepares the interbank transfer
- **THEN** the Payment Engine SHALL produce an internal `pacs.008` representation for the clearing and settlement simulator

#### Scenario: pacs.008 is not externally exposed
- **GIVEN** the payment engine produces an internal `pacs.008` representation
- **WHEN** the API builds the external payment creation response
- **THEN** the response SHALL NOT expose a `pacs.008` payload as an external API contract

### Requirement: Preserve payment identifiers across mapping
The Payment Engine SHALL preserve traceable payment identifiers across `pain.001`, internal `pacs.008`, engine payment record, `pain.002`, and simulator processing.

#### Scenario: Payment identifiers are linked
- **GIVEN** a `pain.001` initiation contains a payment information identifier, instruction identifier, or end-to-end identifier
- **WHEN** the engine maps the initiation to internal `pacs.008`
- **THEN** the system SHALL retain traceability between the external payment ID, supported ISO identifiers, internal message reference, and correlation ID

### Requirement: Map engine outcomes to payment status lifecycle
The Payment Engine SHALL map clearing and settlement simulator outcomes to internal payment lifecycle status and ISO `pain.002` transaction status.

#### Scenario: Simulator settles payment
- **GIVEN** the clearing and settlement simulator returns a settled outcome
- **WHEN** the payment engine updates the payment record
- **THEN** the internal payment status SHALL become `COMPLETED`
- **AND** the generated `pain.002` transaction status SHALL be `ACSC`

#### Scenario: Simulator rejects payment
- **GIVEN** the clearing and settlement simulator returns a business rejection outcome
- **WHEN** the payment engine updates the payment record
- **THEN** the internal payment status SHALL become `REJECTED` with reason details
- **AND** the generated `pain.002` transaction status SHALL be `RJCT`

#### Scenario: Simulator times out
- **GIVEN** the clearing and settlement simulator returns a timeout outcome
- **WHEN** the payment engine updates the payment record
- **THEN** the internal payment status SHALL become `TIMEOUT` with reason details
- **AND** the generated `pain.002` transaction status SHALL be `PDNG`

#### Scenario: Simulator fails internally
- **GIVEN** the clearing and settlement simulator returns an internal failure outcome
- **WHEN** the payment engine updates the payment record
- **THEN** the internal payment status SHALL become `FAILED` with reason details
- **AND** the generated status report SHALL include failure reason details appropriate for the simulator outcome

