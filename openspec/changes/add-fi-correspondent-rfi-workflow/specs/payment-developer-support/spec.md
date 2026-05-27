## ADDED Requirements

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
