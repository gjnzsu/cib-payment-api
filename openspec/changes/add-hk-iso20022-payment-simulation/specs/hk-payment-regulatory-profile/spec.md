## ADDED Requirements

### Requirement: Require beneficiary account or FPS proxy
The supported HK payment profile SHALL require each payment initiation to identify the beneficiary using either a bank/SVF account reference or a supported FPS proxy.

#### Scenario: Beneficiary account is provided
- **GIVEN** a `pain.001` initiation identifies the beneficiary using a supported participant and account number
- **WHEN** the HK payment profile validates the initiation
- **THEN** the system SHALL accept the beneficiary identifier for further processing

#### Scenario: FPS proxy is provided
- **GIVEN** a `pain.001` initiation identifies the beneficiary using a supported FPS proxy type
- **WHEN** the HK payment profile validates the initiation
- **THEN** the system SHALL accept the beneficiary identifier for further processing

#### Scenario: Beneficiary identifier is missing
- **GIVEN** a `pain.001` initiation does not contain a supported beneficiary account or FPS proxy
- **WHEN** the HK payment profile validates the initiation
- **THEN** the system SHALL reject the request with a validation error

### Requirement: Require payment reference or end-to-end identifier
The supported HK payment profile SHALL require a payment reference or ISO end-to-end identifier for traceability.

#### Scenario: End-to-end identifier is present
- **GIVEN** a `pain.001` initiation contains a supported end-to-end identifier
- **WHEN** the HK payment profile validates the initiation
- **THEN** the system SHALL preserve the identifier for payment traceability

#### Scenario: Traceability identifier is missing
- **GIVEN** a `pain.001` initiation omits both payment reference and supported end-to-end identifier
- **WHEN** the HK payment profile validates the initiation
- **THEN** the system SHALL reject the request with a validation error

### Requirement: Support optional purpose fields
The supported HK payment profile SHALL support purpose and category purpose fields without requiring them in v1.

#### Scenario: Purpose is present
- **GIVEN** a `pain.001` initiation contains supported purpose or category purpose information
- **WHEN** the HK payment profile validates the initiation
- **THEN** the system SHALL preserve the purpose information

#### Scenario: Purpose is absent
- **GIVEN** a `pain.001` initiation omits purpose and category purpose information
- **WHEN** all required payment profile fields are otherwise valid
- **THEN** the system SHALL continue payment processing

### Requirement: Simulate suspicious proxy or account rejection
The system SHALL provide a deterministic suspicious proxy or account simulator scenario for HK payment profile testing.

#### Scenario: Suspicious beneficiary is detected
- **GIVEN** the local/test scenario marks the beneficiary proxy or account as suspicious
- **WHEN** the simulator evaluates the payment
- **THEN** the system SHALL reject the payment with a reason indicating suspicious proxy or account

### Requirement: Protect HK payment sensitive data
The system SHALL mask or omit sensitive HK payment identifiers at logging, error, and developer artifact boundaries.

#### Scenario: Sensitive beneficiary data is logged
- **GIVEN** the system handles account numbers, FPS proxy values, mobile numbers, email addresses, or HKID-like proxy identifiers
- **WHEN** the system writes logs or error diagnostics
- **THEN** the system SHALL mask or omit the sensitive values

#### Scenario: Developer examples are provided
- **GIVEN** OpenAPI, Postman, or fixture examples include HK payment data
- **WHEN** the artifacts are published in the repository
- **THEN** the artifacts SHALL use clearly synthetic data and avoid real personal identifiers
