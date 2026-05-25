# iso20022-payment-initiation Specification

## Purpose
TBD - created by archiving change add-hk-iso20022-payment-simulation. Update Purpose after archive.
## Requirements
### Requirement: Accept ISO 20022 pain.001 payment initiation
The system SHALL accept supported ISO 20022 `pain.001.001.09` XML customer credit transfer initiation as the external domestic payment creation request format.

#### Scenario: Supported pain.001 request is accepted
- **GIVEN** an authenticated and authorized client submits `POST /v1/domestic-payments` with a supported XML content type and a valid HKD `pain.001.001.09` customer credit transfer initiation
- **WHEN** the system parses and validates the XML initiation
- **THEN** the system SHALL admit the request as a payment candidate for Payment Engine processing
- **AND** the system SHALL return a `pain.002.001.10` response after Payment Engine processing

#### Scenario: JSON initiation is not supported
- **GIVEN** an authenticated and authorized client submits `POST /v1/domestic-payments` with a custom JSON payment initiation payload
- **WHEN** the system evaluates the request content type
- **THEN** the system SHALL reject the request because payment initiation requires supported `pain.001.001.09` XML

### Requirement: Reject unsupported ISO 20022 payloads
The system SHALL reject XML payment initiation payloads outside the supported HK `pain.001.001.09` profile with consistent HTTP error responses.

#### Scenario: XML document is not pain.001
- **GIVEN** an authenticated and authorized client submits XML that is not a supported `pain.001` document
- **WHEN** the system validates the XML request
- **THEN** the system SHALL reject the request with a consistent validation error that includes the correlation ID

#### Scenario: pain.001 contains unsupported payment structures
- **GIVEN** an authenticated and authorized client submits a `pain.001` document containing unsupported cross-border, batch, recurring, amendment, cancellation, or non-realtime payment structures
- **WHEN** the system validates the supported HK profile
- **THEN** the system SHALL reject the request with a consistent validation error

### Requirement: Extract payment initiation business fields
The system SHALL extract the supported payment business fields from `pain.001.001.09` for payment candidate admission and Payment Engine orchestration.

#### Scenario: Required fields are extracted
- **GIVEN** a supported `pain.001` document contains debtor account, debtor name, creditor identifier, amount, currency, and payment reference or end-to-end identifier
- **WHEN** the payment initiation is normalized
- **THEN** the system SHALL create an internal payment instruction containing the extracted business fields

#### Scenario: Optional purpose fields are extracted
- **GIVEN** a supported `pain.001` document contains purpose or category purpose information
- **WHEN** the payment initiation is normalized
- **THEN** the system SHALL preserve the supported purpose information for payment engine validation and downstream simulation

### Requirement: Securely parse XML payment initiation
The system SHALL parse ISO 20022 XML using secure XML parser settings.

#### Scenario: XML contains external entity content
- **GIVEN** an XML payment initiation contains external entity or unsafe document-type declarations
- **WHEN** the system parses the XML
- **THEN** the system SHALL reject the request without resolving external entities or exposing local resources

#### Scenario: XML parse error occurs
- **GIVEN** a client submits malformed XML
- **WHEN** the system parses the request body
- **THEN** the system SHALL return a consistent validation error with the request correlation ID

