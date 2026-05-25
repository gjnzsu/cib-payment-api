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

