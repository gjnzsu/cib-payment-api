## ADDED Requirements

### Requirement: Return pain.002 for processed ISO payment outcomes
The system SHALL return ISO 20022 `pain.002.001.10` XML for supported ISO payment initiation requests that become valid payment candidates and are processed by the Payment Engine.

#### Scenario: Settled payment returns ACSC
- **GIVEN** an authenticated and authorized client submits a valid supported `pain.001.001.09` payment initiation
- **AND** the Payment Engine receives a settled outcome from the HK simulator
- **WHEN** the system builds the response
- **THEN** the system SHALL return HTTP `200 OK` with `pain.002.001.10` XML containing transaction status `ACSC`

#### Scenario: Rejected payment returns RJCT
- **GIVEN** an authenticated and authorized client submits a valid supported `pain.001.001.09` payment initiation
- **AND** the Payment Engine receives a business or scheme rejection outcome from the HK simulator
- **WHEN** the system builds the response
- **THEN** the system SHALL return HTTP `200 OK` with `pain.002.001.10` XML containing transaction status `RJCT` and reason details

#### Scenario: Pending payment returns PDNG
- **GIVEN** an authenticated and authorized client submits a valid supported `pain.001.001.09` payment initiation
- **AND** the Payment Engine receives a pending, downstream instability, or timeout outcome from the HK simulator
- **WHEN** the system builds the response
- **THEN** the system SHALL return HTTP `200 OK` with `pain.002.001.10` XML containing transaction status `PDNG` and reason details

### Requirement: Query latest pain.002 status report
The system SHALL return the latest `pain.002.001.10` status report for ISO-created payments through the payment status query resource.

#### Scenario: Existing ISO payment status is queried
- **GIVEN** an authenticated and authorized client owns an ISO-created payment
- **WHEN** the client submits a status query for the payment
- **THEN** the Edge API SHALL query the Payment Engine status port and return the latest `pain.002.001.10` XML report

#### Scenario: Unknown ISO payment status is queried
- **GIVEN** an authenticated and authorized client queries an unknown payment ID
- **WHEN** the Edge API queries the Payment Engine status port
- **THEN** the system SHALL return a consistent not-found error without exposing unrelated payment data

### Requirement: Preserve ISO status report traceability
The generated `pain.002` report SHALL preserve traceability to the original `pain.001` initiation and engine payment record.

#### Scenario: pain.002 references original initiation
- **GIVEN** a valid `pain.001.001.09` initiation contains supported message, payment, instruction, or end-to-end identifiers
- **WHEN** the Payment Engine generates `pain.002.001.10`
- **THEN** the status report SHALL include supported original identifiers and be linked to the engine payment ID

### Requirement: Avoid pain.002 for admission failures
The system SHALL NOT generate `pain.002` for requests that fail Edge/API admission.

#### Scenario: Request fails admission
- **GIVEN** a request fails authentication, authorization, idempotency, XML syntax, supported message type, mandatory field, or HK profile validation before payment candidate creation
- **WHEN** the system rejects the request
- **THEN** the response SHALL be an HTTP error and SHALL NOT include `pain.002`
