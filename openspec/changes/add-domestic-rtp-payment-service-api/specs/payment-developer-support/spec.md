## ADDED Requirements

### Requirement: Provide OpenAPI documentation
The system SHALL provide OpenAPI 3.0.3 documentation for the Domestic RTP Payment Service API.

#### Scenario: Developer views API contract
- **GIVEN** OpenAPI 3.0.3 documentation is available
- **WHEN** a developer opens the API documentation
- **THEN** the documentation SHALL describe payment creation, payment status query, authentication, idempotency, request schemas, response schemas, and error responses

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
The downstream payment processor mock SHALL support deterministic success, rejection, timeout, and internal failure scenarios.

#### Scenario: Success scenario is selected
- **GIVEN** a test or local debugging request selects the success mock scenario
- **WHEN** the downstream payment processor mock handles the request
- **THEN** the downstream mock SHALL produce an outcome that maps to `COMPLETED`

#### Scenario: Rejection scenario is selected
- **GIVEN** a test or local debugging request selects the rejection mock scenario
- **WHEN** the downstream payment processor mock handles the request
- **THEN** the downstream mock SHALL produce an outcome that maps to `REJECTED`

#### Scenario: Timeout scenario is selected
- **GIVEN** a test or local debugging request selects the timeout mock scenario
- **WHEN** the downstream payment processor mock handles the request
- **THEN** the downstream mock SHALL produce an outcome that maps to `TIMEOUT`

#### Scenario: Internal failure scenario is selected
- **GIVEN** a test or local debugging request selects the internal failure mock scenario
- **WHEN** the downstream payment processor mock handles the request
- **THEN** the downstream mock SHALL produce an outcome that maps to `FAILED`

### Requirement: Provide mock payment test data
The system SHALL provide mock payment request data for success, rejection, timeout, internal failure, invalid request, and idempotency scenarios.

#### Scenario: Test fixtures are used
- **GIVEN** contract, integration, downstream behavior, or idempotency tests need payment request data
- **WHEN** the tests load mock payment fixtures
- **THEN** the system SHALL provide reusable mock payment fixtures for the relevant scenario

### Requirement: Provide Postman collection
The system SHALL provide a Postman collection for local API debugging and partner-style exploration.

#### Scenario: Developer creates payment from Postman
- **GIVEN** a developer has selected the local Postman environment
- **WHEN** the developer runs the payment creation request from the Postman collection
- **THEN** the request SHALL include the expected URL, headers, authentication placeholder, idempotency key, correlation ID, and JSON payload structure

#### Scenario: Developer queries payment status from Postman
- **GIVEN** a `paymentId` is captured or configured in Postman
- **WHEN** a developer runs the payment status query request from the Postman collection
- **THEN** the request SHALL use a `paymentId` captured or configured from a payment creation response

### Requirement: Provide Postman environment
The system SHALL provide a Postman environment for local API variables.

#### Scenario: Developer configures local environment
- **GIVEN** the Postman environment file is available
- **WHEN** a developer selects the local Postman environment
- **THEN** the environment SHALL provide variables for base URL, JWT token, correlation ID, idempotency key, and payment ID

### Requirement: Support Postman common error scenarios
The Postman collection SHALL support debugging common validation, authentication, authorization, idempotency conflict, downstream rejection, timeout, and internal failure scenarios.

#### Scenario: Developer runs an error scenario
- **GIVEN** the Postman collection contains saved requests or examples for common error scenarios
- **WHEN** a developer selects a saved Postman request or example for a common error scenario
- **THEN** the collection SHALL send the request data needed to exercise that scenario locally

### Requirement: Align developer artifacts with API behavior
OpenAPI documentation, Postman artifacts, downstream mock scenarios, and mock payment test data SHALL align with the Domestic RTP Payment Service API behavior specs.

#### Scenario: Developer artifact describes API behavior
- **GIVEN** OpenAPI documentation, Postman artifacts, downstream mock scenarios, or mock payment test data are available
- **WHEN** a developer uses one of those artifacts
- **THEN** the artifact SHALL reflect the same endpoints, authentication model, idempotency behavior, status lifecycle, and error response behavior defined by the behavior specs
