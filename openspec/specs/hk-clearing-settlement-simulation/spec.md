# hk-clearing-settlement-simulation Specification

## Purpose
TBD - created by archiving change add-hk-iso20022-payment-simulation. Update Purpose after archive.
## Requirements
### Requirement: Simulate HKD FPS clearing and settlement
The system SHALL provide an HKD-only HKICL/FPS-style behavioral simulator for clearing and settlement outcomes.

#### Scenario: HKD payment is settled
- **GIVEN** the payment engine submits an internal `pacs.008` representation for a supported HKD domestic realtime payment
- **WHEN** the simulator processes the payment using the success scenario
- **THEN** the simulator SHALL return a settled outcome that maps to `COMPLETED`

#### Scenario: Non-HKD payment reaches simulator
- **GIVEN** the payment engine submits an internal transfer that is not denominated in HKD
- **WHEN** the simulator validates the transfer
- **THEN** the simulator SHALL reject the transfer with reason details

### Requirement: Model simulator participants
The simulator SHALL model payer and payee participants sufficiently to support deterministic routing and rejection behavior.

#### Scenario: Known participants are used
- **GIVEN** an internal transfer references configured payer and payee participants
- **WHEN** the simulator validates participants
- **THEN** the simulator SHALL continue clearing and settlement simulation

#### Scenario: Unknown participant is used
- **GIVEN** an internal transfer references an unknown payer or payee participant
- **WHEN** the simulator validates participants
- **THEN** the simulator SHALL return a business rejection outcome

### Requirement: Support deterministic clearing scenarios
The simulator SHALL support deterministic local/test scenarios for success, business rejection, suspicious proxy or account rejection, timeout, and internal failure.

#### Scenario: Success scenario is selected
- **GIVEN** local or test scenario value `success` is selected
- **WHEN** the simulator processes the internal transfer
- **THEN** the simulator SHALL return a settled outcome

#### Scenario: Business rejection scenario is selected
- **GIVEN** local or test scenario value `rejection` is selected
- **WHEN** the simulator processes the internal transfer
- **THEN** the simulator SHALL return a business rejection outcome

#### Scenario: Suspicious proxy or account scenario is selected
- **GIVEN** local or test scenario value `suspicious_proxy_or_account` is selected
- **WHEN** the simulator processes the internal transfer
- **THEN** the simulator SHALL return a rejection outcome with a suspicious proxy or account reason

#### Scenario: Timeout scenario is selected
- **GIVEN** local or test scenario value `timeout` is selected
- **WHEN** the simulator processes the internal transfer
- **THEN** the simulator SHALL return a timeout outcome

#### Scenario: Internal failure scenario is selected
- **GIVEN** local or test scenario value `internal_failure` is selected
- **WHEN** the simulator processes the internal transfer
- **THEN** the simulator SHALL return an internal failure outcome

### Requirement: Keep simulator controls out of production semantics
The system SHALL treat simulator scenario controls as local/test-only behavior.

#### Scenario: Simulator scenario is documented
- **GIVEN** OpenAPI documentation, Postman artifacts, or local fixtures describe simulator scenarios
- **WHEN** a developer uses the simulator
- **THEN** the artifacts SHALL document the scenario controls as non-production behavior

