## ADDED Requirements

### Requirement: Accept supported camt.056 recall request
The system SHALL accept a supported minimal ISO 20022 `camt.056` XML recall or cancellation request for an existing FI payment.

#### Scenario: Supported camt.056 request is accepted
- **GIVEN** an authenticated FI client owns an FI payment
- **AND** the client has `fi-payments:investigate` scope
- **AND** the request includes a valid `Idempotency-Key`
- **AND** the `camt.056` request references the original FI payment
- **WHEN** the client submits `POST /v1/fi-payments/{paymentId}/recall-requests`
- **THEN** the system SHALL create a recall or investigation record linked to the FI payment
- **AND** the system SHALL return a supported `camt.029` XML resolution response

#### Scenario: camt.056 request is accepted for settled payment
- **GIVEN** an authenticated FI client owns an FI payment with status `SETTLED`
- **AND** the client has `fi-payments:investigate` scope
- **AND** the `camt.056` request references the original FI payment
- **WHEN** the client submits `POST /v1/fi-payments/{paymentId}/recall-requests`
- **THEN** the system SHALL allow the recall request to proceed to idempotency and payload validation

#### Scenario: camt.056 request is accepted for processing payment
- **GIVEN** an authenticated FI client owns an FI payment with status `PROCESSING`
- **AND** the client has `fi-payments:investigate` scope
- **AND** the `camt.056` request references the original FI payment
- **WHEN** the client submits `POST /v1/fi-payments/{paymentId}/recall-requests`
- **THEN** the system SHALL allow the recall request to proceed to idempotency and payload validation

#### Scenario: camt.056 request is rejected for rejected payment
- **GIVEN** an authenticated FI client owns an FI payment with status `REJECTED`
- **AND** the client has `fi-payments:investigate` scope
- **WHEN** the client submits `POST /v1/fi-payments/{paymentId}/recall-requests`
- **THEN** the system SHALL reject the recall request with a consistent validation error

#### Scenario: camt.056 references a different payment
- **GIVEN** an authenticated FI client owns an FI payment
- **AND** the client submits a `camt.056` request that does not reference the target FI payment
- **WHEN** the system validates the recall request
- **THEN** the system SHALL reject the request with a consistent validation error
- **AND** the system SHALL NOT create a recall or investigation record

#### Scenario: camt.056 is submitted for an unrelated payment
- **GIVEN** an authenticated FI client does not own an FI payment
- **WHEN** the client submits `POST /v1/fi-payments/{paymentId}/recall-requests`
- **THEN** the system SHALL reject the request without exposing unrelated FI payment data

### Requirement: Render deterministic camt.029 resolution
The system SHALL render a supported ISO 20022 `camt.029` XML response for deterministic recall and investigation outcomes.

#### Scenario: Recall is accepted
- **GIVEN** a supported `camt.056` recall request is accepted for processing
- **AND** the deterministic simulator outcome is recall accepted
- **WHEN** the system builds the response
- **THEN** the system SHALL return `camt.029` XML indicating accepted resolution

#### Scenario: Recall is rejected
- **GIVEN** a supported `camt.056` recall request is accepted for processing
- **AND** the deterministic simulator outcome is recall rejected
- **WHEN** the system builds the response
- **THEN** the system SHALL return `camt.029` XML indicating rejected resolution with reason details

#### Scenario: Investigation remains pending
- **GIVEN** a supported `camt.056` recall request is accepted for processing
- **AND** the deterministic simulator outcome is investigation pending
- **WHEN** the system builds the response
- **THEN** the system SHALL return `camt.029` XML indicating pending investigation with reason details

### Requirement: Store recall investigation lifecycle
The system SHALL store at most one recall and investigation record linked to each FI payment in the MVP.

#### Scenario: Recall record is stored
- **GIVEN** a supported `camt.056` request is accepted
- **WHEN** the deterministic simulator returns an investigation outcome
- **THEN** the system SHALL store recall request identifiers, original FI payment reference, outcome status, reason details, correlation ID, and timestamps

#### Scenario: FI payment status includes latest investigation summary
- **GIVEN** an FI payment has at least one recall or investigation record
- **WHEN** the FI payment owner queries `GET /v1/fi-payments/{paymentId}`
- **THEN** the system SHALL include the latest recall or investigation summary in the JSON status response

#### Scenario: Recall record already exists
- **GIVEN** an FI payment already has a recall or cancellation investigation record
- **WHEN** the FI payment owner submits a new `camt.056` request with different recall semantics for the same payment
- **THEN** the system SHALL reject the request with a consistent conflict or validation error

### Requirement: Securely parse and render investigation XML
The system SHALL parse `camt.056` XML securely and render `camt.029` XML deterministically for supported simulator outcomes.

#### Scenario: camt.056 contains unsafe XML content
- **GIVEN** a `camt.056` request contains external entity or unsafe document-type declarations
- **WHEN** the system parses the XML
- **THEN** the system SHALL reject the request without resolving external entities or exposing local resources

#### Scenario: camt.056 is malformed
- **GIVEN** a client submits malformed XML to the recall request endpoint
- **WHEN** the system parses the request body
- **THEN** the system SHALL return a consistent validation error with the request correlation ID

#### Scenario: camt.029 response is generated
- **GIVEN** a supported recall or investigation outcome exists
- **WHEN** the system renders the `camt.029` response
- **THEN** the response SHALL include supported original recall identifiers, original FI payment reference, resolution outcome, and correlation traceability
