## ADDED Requirements

### Requirement: Accept supported pacs.009 FI payment initiation
The system SHALL accept a supported minimal ISO 20022 `pacs.009` XML profile as the external FI payment creation request format for one USD FI-to-FI customer credit transfer cover/interbank leg per request.

#### Scenario: Supported pacs.009 request is accepted
- **GIVEN** an authenticated FI client has `fi-payments:create` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the request body is a supported USD `pacs.009` XML FI payment initiation
- **WHEN** the client submits `POST /v1/fi-payments`
- **THEN** the system SHALL create an FI payment record owned by the authenticated FI client
- **AND** the system SHALL return a JSON acknowledgement containing payment ID, status, correspondent settlement context, and correlation ID

#### Scenario: Non-USD pacs.009 request is rejected
- **GIVEN** an authenticated FI client submits a supported `pacs.009` XML request with a non-USD settlement amount
- **WHEN** the client submits `POST /v1/fi-payments`
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create an FI payment record

#### Scenario: Multiple FI payments are submitted in one request
- **GIVEN** an authenticated FI client submits a `pacs.009` XML request containing multiple payment instructions
- **WHEN** the system validates the supported FI payment profile
- **THEN** the system SHALL reject the request because the MVP supports one FI payment per request

### Requirement: Extract FI payment business fields
The system SHALL extract supported FI payment identifiers, parties, settlement amount, settlement date, currency, and reference fields from accepted `pacs.009` XML.

#### Scenario: Required FI payment fields are extracted
- **GIVEN** a supported `pacs.009` request contains message ID, instruction ID, end-to-end or transaction reference, settlement amount, settlement currency, settlement date, instructing agent, and instructed agent
- **WHEN** the system normalizes the FI payment request
- **THEN** the system SHALL create an FI payment candidate containing the extracted business fields

#### Scenario: Required FI payment field is missing
- **GIVEN** a `pacs.009` request is missing a required FI payment identifier, FI party, amount, currency, or settlement date
- **WHEN** the system validates the supported profile
- **THEN** the system SHALL reject the request with a consistent validation error

### Requirement: Store FI payment lifecycle status
The system SHALL store FI payment lifecycle status separately from domestic payment records.

#### Scenario: FI payment accepted scenario settles
- **GIVEN** a supported USD `pacs.009` request passes admission
- **AND** the deterministic simulator outcome is `fi_payment_accepted`
- **WHEN** the system records the FI payment result
- **THEN** the system SHALL store the FI payment with status `SETTLED`

#### Scenario: FI payment pending correspondent review
- **GIVEN** a supported USD `pacs.009` request passes admission
- **AND** the deterministic simulator outcome is `fi_payment_pending_correspondent_review`
- **WHEN** the system records the FI payment result
- **THEN** the system SHALL store the FI payment with status `PROCESSING` and correspondent review reason details

#### Scenario: FI payment unsupported correspondent rejection
- **GIVEN** a supported USD `pacs.009` request passes admission
- **AND** the deterministic simulator outcome is `fi_payment_rejected_unsupported_correspondent`
- **WHEN** the system records the FI payment result
- **THEN** the system SHALL store the FI payment with status `REJECTED` and reason details

### Requirement: Query FI payment status
The system SHALL allow an authenticated FI client with read entitlement to query its own FI payment status.

#### Scenario: FI payment owner queries status
- **GIVEN** an authenticated FI client owns an FI payment
- **AND** the client has `fi-payments:read` scope
- **WHEN** the client submits `GET /v1/fi-payments/{paymentId}`
- **THEN** the system SHALL return a JSON status response containing payment ID, lifecycle status, FI parties, correspondent settlement context, latest recall or investigation summary when present, and correlation ID

#### Scenario: Unrelated client queries FI payment
- **GIVEN** an authenticated FI client does not own an FI payment
- **WHEN** the client submits `GET /v1/fi-payments/{paymentId}`
- **THEN** the system SHALL reject the request without exposing unrelated FI payment data

#### Scenario: Unknown FI payment is queried
- **GIVEN** an authenticated FI client has `fi-payments:read` scope
- **WHEN** the client submits `GET /v1/fi-payments/{paymentId}` for an unknown payment ID
- **THEN** the system SHALL return a consistent not-found error

### Requirement: Securely parse FI XML initiation
The system SHALL parse FI `pacs.009` XML using secure XML parser settings.

#### Scenario: FI XML contains unsafe external entity content
- **GIVEN** a `pacs.009` request contains external entity or unsafe document-type declarations
- **WHEN** the system parses the XML
- **THEN** the system SHALL reject the request without resolving external entities or exposing local resources

#### Scenario: FI XML is malformed
- **GIVEN** a client submits malformed XML to `POST /v1/fi-payments`
- **WHEN** the system parses the request body
- **THEN** the system SHALL return a consistent validation error with the request correlation ID
