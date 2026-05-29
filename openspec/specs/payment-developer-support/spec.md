## Purpose
Define the developer-facing OpenAPI, mock scenario, fixture, and Postman artifacts used to test and explore the Domestic RTP Payment Service API.
## Requirements
### Requirement: Provide OpenAPI documentation
The system SHALL provide OpenAPI 3.0.3 documentation for the Domestic RTP Payment Service API, including supported ISO 20022 `pain.001.001.09` payment initiation, ISO 20022 `pain.002.001.10` status report responses, payment status query, authentication, idempotency, request schemas, response schemas, simulator scenarios, and error responses.

#### Scenario: Developer views API contract
- **GIVEN** OpenAPI 3.0.3 documentation is available
- **WHEN** a developer opens the API documentation
- **THEN** the documentation SHALL describe payment creation, payment status query, authentication, idempotency, request schemas, response schemas, and error responses

#### Scenario: Developer views ISO payment initiation contract
- **GIVEN** OpenAPI 3.0.3 documentation is available
- **WHEN** a developer opens the payment creation operation
- **THEN** the documentation SHALL describe the supported XML content type, `pain.001` example, HKD-only profile constraints, and simulator-only scope

### Requirement: Render API documentation
The system SHALL support rendering API documentation through Swagger UI, Redoc, or equivalent tooling.

#### Scenario: Documentation is rendered locally
- **GIVEN** Swagger UI, Redoc, or equivalent tooling is configured
- **WHEN** a developer runs the local documentation tooling
- **THEN** the developer SHALL be able to view the Domestic RTP Payment Service API documentation in a browser-compatible format

### Requirement: Provide downstream payment processor mock
The system SHALL provide a downstream payment processor mock for local development and automated testing.

#### Scenario: Mock is available locally
- **GIVEN** the Payment Service API is run in local or test mode
- **WHEN** downstream payment processing is required
- **THEN** the downstream payment processor mock SHALL be available without real core banking or payment scheme connectivity

### Requirement: Support deterministic downstream mock scenarios
The downstream payment processor mock and HK clearing and settlement simulator SHALL support deterministic success, rejection, suspicious proxy or account rejection, timeout, and internal failure scenarios.

#### Scenario: Success scenario is selected
- **GIVEN** a test or local debugging request includes local/test-only header `X-Mock-Scenario` with value `success`
- **WHEN** the downstream payment processor mock or simulator handles the request
- **THEN** the mock or simulator SHALL produce an outcome that maps to `COMPLETED`

#### Scenario: Rejection scenario is selected
- **GIVEN** a test or local debugging request includes local/test-only header `X-Mock-Scenario` with value `rejection`
- **WHEN** the downstream payment processor mock or simulator handles the request
- **THEN** the mock or simulator SHALL produce an outcome that maps to `REJECTED`

#### Scenario: Suspicious proxy or account scenario is selected
- **GIVEN** a test or local debugging request includes local/test-only header `X-Mock-Scenario` with value `suspicious_proxy_or_account`
- **WHEN** the HK clearing and settlement simulator handles the request
- **THEN** the simulator SHALL produce an outcome that maps to `REJECTED` with suspicious proxy or account reason details

#### Scenario: Timeout scenario is selected
- **GIVEN** a test or local debugging request includes local/test-only header `X-Mock-Scenario` with value `timeout`
- **WHEN** the downstream payment processor mock or simulator handles the request
- **THEN** the mock or simulator SHALL produce an outcome that maps to `TIMEOUT`

#### Scenario: Internal failure scenario is selected
- **GIVEN** a test or local debugging request includes local/test-only header `X-Mock-Scenario` with value `internal_failure`
- **WHEN** the downstream payment processor mock or simulator handles the request
- **THEN** the mock or simulator SHALL produce an outcome that maps to `FAILED`

#### Scenario: Mock scenario control is documented as non-production behavior
- **GIVEN** OpenAPI documentation, Postman artifacts, or mock fixtures describe `X-Mock-Scenario`
- **WHEN** a developer uses the deterministic downstream mock or simulator scenarios
- **THEN** the artifacts SHALL document `X-Mock-Scenario` as local/test-only behavior that is not part of production payment semantics

### Requirement: Provide mock payment test data
The system SHALL provide mock payment request data for ISO `pain.001.001.09` success, rejection, suspicious proxy or account rejection, pending, timeout, internal failure, invalid request, and idempotency scenarios.

#### Scenario: Test fixtures are used
- **GIVEN** contract, integration, downstream behavior, simulator behavior, ISO mapping, or idempotency tests need payment request data
- **WHEN** the tests load mock payment fixtures
- **THEN** the system SHALL provide reusable mock payment fixtures for the relevant scenario

### Requirement: Provide Postman collection
The system SHALL provide a Postman collection for local API debugging and partner-style exploration using supported `pain.001.001.09` XML payment creation and `pain.002.001.10` XML status reports.

#### Scenario: Developer creates pain.001 payment from Postman
- **GIVEN** a developer has selected the local Postman environment
- **WHEN** the developer runs the `pain.001` payment creation request from the Postman collection
- **THEN** the request SHALL include the expected URL, XML content type, XML accept header, authentication placeholder, idempotency key, `X-Correlation-ID`, optional local/test-only `X-Mock-Scenario`, and synthetic XML payload
- **AND** the saved response example SHALL show `pain.002.001.10` XML

#### Scenario: Developer queries payment status from Postman
- **GIVEN** a `paymentId` is captured or configured in Postman
- **WHEN** a developer runs the payment status query request from the Postman collection
- **THEN** the request SHALL use a payment identifier captured or configured from a payment creation response and request the latest `pain.002.001.10` XML report

### Requirement: Provide Postman environment
The system SHALL provide a Postman environment for local API variables.

#### Scenario: Developer configures local environment
- **GIVEN** the Postman environment file is available
- **WHEN** a developer selects the local Postman environment
- **THEN** the environment SHALL provide variables for base URL, JWT token, correlation ID, idempotency key, payment ID, and local/test-only mock scenario selection

### Requirement: Support Postman common error scenarios
The Postman collection SHALL support debugging common validation, authentication, authorization, idempotency conflict, downstream rejection, timeout, and internal failure scenarios.

#### Scenario: Developer runs an error scenario
- **GIVEN** the Postman collection contains saved requests or examples for common error scenarios
- **WHEN** a developer selects a saved Postman request or example for a common error scenario
- **THEN** the collection SHALL send the request data needed to exercise that scenario locally

### Requirement: Align developer artifacts with API behavior
OpenAPI documentation, Postman artifacts, downstream mock scenarios, HK simulator scenarios, ISO examples, and mock payment test data SHALL align with the Domestic RTP Payment Service API behavior specs.

#### Scenario: Developer artifact describes API behavior
- **GIVEN** OpenAPI documentation, Postman artifacts, downstream mock scenarios, HK simulator scenarios, ISO examples, or mock payment test data are available
- **WHEN** a developer uses one of those artifacts
- **THEN** the artifact SHALL reflect the same endpoints, authentication model, idempotency behavior, correlation header, status lifecycle, response fields, mock scenario controls, and error response behavior defined by the behavior specs

### Requirement: Document FI correspondent payment APIs
The system SHALL provide OpenAPI 3.0.3 documentation for FI payment initiation, FI payment status query, and FI recall or investigation requests.

#### Scenario: Developer views FI payment contract
- **GIVEN** OpenAPI 3.0.3 documentation is available
- **WHEN** a developer opens the FI payment operations
- **THEN** the documentation SHALL describe `/v1/fi-payments`, `/v1/fi-payments/{paymentId}`, `/v1/fi-payments/{paymentId}/recall-requests`, required scopes, idempotency requirements, supported media types, simulator scenarios, and error responses

#### Scenario: Developer views simulator-only limitations
- **GIVEN** OpenAPI documentation describes FI correspondent payment behavior
- **WHEN** a developer reads the FI payment documentation
- **THEN** the documentation SHALL state that the behavior is simulator-only and does not provide real SWIFT, CBPR+, correspondent banking, ledger, AML, sanctions, fraud, or settlement connectivity

### Requirement: Provide FI ISO fixtures
The system SHALL provide synthetic FI XML fixtures for supported `pacs.009`, `camt.056`, and `camt.029` scenarios.

#### Scenario: FI payment fixtures are used
- **GIVEN** contract, integration, XML parser, simulator, idempotency, or developer artifact tests need FI payment data
- **WHEN** the tests load mock FI payment fixtures
- **THEN** the system SHALL provide reusable synthetic fixtures for accepted, rejected, pending, invalid, recall accepted, recall rejected, and investigation pending scenarios

#### Scenario: Fixtures avoid real sensitive data
- **GIVEN** FI XML fixtures are available
- **WHEN** the fixtures are reviewed or used in tests
- **THEN** the fixtures SHALL use synthetic BICs, account references, identifiers, and narrative values without real customer, account, or payment data

### Requirement: Provide Postman FI scenarios
The system SHALL provide Postman requests for local FI payment and recall/investigation exploration.

#### Scenario: Developer creates FI payment from Postman
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with FI scopes
- **WHEN** the developer runs the FI payment creation request
- **THEN** the request SHALL include the expected URL, `application/pacs.009+xml` content type, authentication placeholder, idempotency key, `X-Correlation-ID`, optional local/test-only `X-Mock-Scenario`, and synthetic `pacs.009` payload

#### Scenario: Developer submits recall request from Postman
- **GIVEN** a developer has created or configured an FI payment ID
- **WHEN** the developer runs the FI recall request
- **THEN** the request SHALL include the expected URL, `application/camt.056+xml` content type, `application/camt.029+xml` accept header, authentication placeholder, idempotency key, `X-Correlation-ID`, optional local/test-only `X-Mock-Scenario`, and synthetic `camt.056` payload
- **AND** the saved response example SHALL show supported `camt.029` XML

### Requirement: Support deterministic FI simulator scenarios
The FI payment and investigation simulator SHALL support deterministic local/test-only scenarios for FI payment and recall outcomes.

#### Scenario: FI payment accepted scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `fi_payment_accepted`
- **WHEN** the FI simulator handles the request
- **THEN** the simulator SHALL produce a `SETTLED` FI payment outcome

#### Scenario: Unsupported correspondent scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `fi_payment_rejected_unsupported_correspondent`
- **WHEN** the FI simulator handles the request
- **THEN** the simulator SHALL produce a `REJECTED` FI payment outcome with unsupported correspondent reason details

#### Scenario: Correspondent review scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `fi_payment_pending_correspondent_review`
- **WHEN** the FI simulator handles the request
- **THEN** the simulator SHALL produce a `PROCESSING` FI payment outcome with correspondent review reason details

#### Scenario: Recall accepted scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `recall_accepted`
- **WHEN** the recall simulator handles the request
- **THEN** the simulator SHALL produce a `camt.029` accepted resolution

#### Scenario: Recall rejected scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `recall_rejected`
- **WHEN** the recall simulator handles the request
- **THEN** the simulator SHALL produce a `camt.029` rejected resolution

#### Scenario: Investigation pending scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `investigation_pending`
- **WHEN** the recall simulator handles the request
- **THEN** the simulator SHALL produce a `camt.029` pending investigation resolution

### Requirement: Align FI developer artifacts with behavior specs
OpenAPI documentation, Postman artifacts, FI simulator scenarios, ISO fixtures, and developer documentation SHALL align with the FI payment and investigation behavior specs.

#### Scenario: FI artifact describes API behavior
- **GIVEN** OpenAPI documentation, Postman artifacts, FI simulator scenarios, ISO examples, or developer documentation are available
- **WHEN** a developer uses one of those artifacts
- **THEN** the artifact SHALL reflect the same endpoints, scopes, idempotency behavior, correlation header, supported XML profiles, JSON status shape, `camt.029` response behavior, mock scenario controls, and error response behavior defined by the behavior specs

