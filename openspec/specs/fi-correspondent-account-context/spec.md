# fi-correspondent-account-context Specification

## Purpose
TBD - created by archiving change add-fi-correspondent-rfi-workflow. Update Purpose after archive.
## Requirements
### Requirement: Capture simulated correspondent settlement context
The system SHALL capture simulated correspondent settlement context for accepted FI payments without performing real ledger accounting.

#### Scenario: Correspondent context is captured from supported FI payment
- **GIVEN** a supported USD `pacs.009` request includes instructing agent, instructed agent, and optional correspondent or intermediary agent information
- **WHEN** the system creates the FI payment record
- **THEN** the system SHALL derive the account relationship role and simulated account reference from the supported correspondent route profile
- **AND** the system SHALL store the instructing agent, instructed agent, optional correspondent or intermediary bank, settlement currency, account relationship role, and masked simulated account reference

#### Scenario: Correspondent context is returned in FI status
- **GIVEN** an authenticated FI client owns an FI payment with correspondent settlement context
- **WHEN** the client queries `GET /v1/fi-payments/{paymentId}`
- **THEN** the system SHALL return the correspondent settlement context using masked simulated account references

### Requirement: Support nostro vostro and loro roles
The system SHALL support `NOSTRO`, `VOSTRO`, and `LORO` as simulator-derived account relationship roles for FI payment routing and investigation context.

#### Scenario: Nostro role is accepted
- **GIVEN** a supported FI payment route profile derives a `NOSTRO` settlement account relationship
- **WHEN** the system creates the FI payment record
- **THEN** the system SHALL store `NOSTRO` as the account relationship role

#### Scenario: Vostro role is accepted
- **GIVEN** a supported FI payment route profile derives a `VOSTRO` settlement account relationship
- **WHEN** the system creates the FI payment record
- **THEN** the system SHALL store `VOSTRO` as the account relationship role

#### Scenario: Loro role is accepted
- **GIVEN** a supported FI payment route profile derives a `LORO` settlement account relationship
- **WHEN** the system creates the FI payment record
- **THEN** the system SHALL store `LORO` as the account relationship role

#### Scenario: Invalid route cannot derive account role
- **GIVEN** an FI payment request contains a correspondent route outside the supported route profile
- **WHEN** the system derives correspondent settlement context
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT accept client-supplied `NOSTRO`, `VOSTRO`, or `LORO` values as authoritative business fields

#### Scenario: Supported route reaches unsupported correspondent simulator outcome
- **GIVEN** a supported FI payment request contains a syntactically valid route profile
- **AND** the deterministic simulator outcome is `fi_payment_rejected_unsupported_correspondent`
- **WHEN** the system records the FI payment result
- **THEN** the system SHALL keep the derived correspondent settlement context
- **AND** the system SHALL store the FI payment as `REJECTED` with unsupported correspondent reason details

### Requirement: Exclude real correspondent ledger behavior
The system SHALL NOT perform real nostro, vostro, or loro ledger accounting for the FI payment simulation.

#### Scenario: FI payment is processed
- **GIVEN** an FI payment is accepted, rejected, settled, or placed into investigation
- **WHEN** the simulator records the FI payment outcome
- **THEN** the system SHALL NOT perform real balance checks, debit postings, credit postings, reconciliation entries, or statement generation

#### Scenario: Developer artifacts describe account context
- **GIVEN** OpenAPI documentation, Postman examples, fixtures, or developer docs describe simulated correspondent account context
- **WHEN** a developer reads the artifact
- **THEN** the artifact SHALL state that account roles are routing and investigation context only and not production ledger accounting

### Requirement: Link account context to investigation records
The system SHALL link recall and investigation records to the original FI payment's correspondent settlement context.

#### Scenario: Recall request is created for FI payment
- **GIVEN** an FI payment has correspondent settlement context
- **WHEN** the authenticated FI client submits a supported `camt.056` recall request for that payment
- **THEN** the system SHALL link the recall or investigation record to the original payment and its settlement context

