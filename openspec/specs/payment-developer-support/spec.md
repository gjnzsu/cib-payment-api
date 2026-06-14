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

### Requirement: Document classic payment rail simulation APIs
The system SHALL provide OpenAPI 3.0.3 documentation for ACH Direct Credit batch simulation and RTGS payment simulation.

#### Scenario: Developer views ACH API contract
- **GIVEN** OpenAPI 3.0.3 documentation is available
- **WHEN** a developer opens the ACH batch operations
- **THEN** the documentation SHALL describe `/v1/ach-batches`, `/v1/ach-batches/{batchId}`, required scopes, idempotency requirements, JSON request and response schemas, simulator scenarios, and error responses

#### Scenario: Developer views RTGS API contract
- **GIVEN** OpenAPI 3.0.3 documentation is available
- **WHEN** a developer opens the RTGS payment operations
- **THEN** the documentation SHALL describe `/v1/rtgs-payments`, `/v1/rtgs-payments/{paymentId}`, required scopes, idempotency requirements, JSON request and response schemas, simulator scenarios, settlement finality, and error responses

#### Scenario: Developer views simulator-only limitations
- **GIVEN** OpenAPI documentation describes ACH or RTGS behavior
- **WHEN** a developer reads the rail simulation documentation
- **THEN** the documentation SHALL state that the behavior is simulator-only and does not provide real ACH, RTGS, central bank, ledger, liquidity, NACHA, ISO 20022, sanctions, fraud, or settlement connectivity

### Requirement: Support deterministic ACH and RTGS simulator scenarios
The ACH and RTGS simulators SHALL support deterministic local/test-only scenarios for classic rail outcomes.

#### Scenario: ACH settled scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `ach_direct_credit_settled`
- **WHEN** the ACH simulator handles the request
- **THEN** the simulator SHALL produce a `SETTLED` ACH batch outcome with settled entry summaries

#### Scenario: ACH accepted scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `ach_direct_credit_accepted`
- **WHEN** the ACH simulator handles the request
- **THEN** the simulator SHALL produce an `ACCEPTED_FOR_CLEARING` ACH batch outcome with accepted entry summaries

#### Scenario: ACH settlement pending scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `ach_direct_credit_settlement_pending`
- **WHEN** the ACH simulator handles the request
- **THEN** the simulator SHALL produce a `SETTLEMENT_PENDING` ACH batch outcome

#### Scenario: ACH partially returned scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `ach_direct_credit_partially_returned`
- **WHEN** the ACH simulator handles the request
- **THEN** the simulator SHALL produce a `PARTIALLY_RETURNED` ACH batch outcome with at least one returned entry summary

#### Scenario: ACH rejected scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `ach_direct_credit_rejected`
- **WHEN** the ACH simulator handles the request
- **THEN** the simulator SHALL produce a `REJECTED` ACH batch outcome

#### Scenario: RTGS settled scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `rtgs_settled`
- **WHEN** the RTGS simulator handles the request
- **THEN** the simulator SHALL produce a `SETTLED` RTGS outcome with settlement finality set to true

#### Scenario: RTGS liquidity queue scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `rtgs_queued_for_liquidity`
- **WHEN** the RTGS simulator handles the request
- **THEN** the simulator SHALL produce a `QUEUED_FOR_LIQUIDITY` RTGS outcome with settlement finality set to false

#### Scenario: RTGS rejected scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `rtgs_rejected`
- **WHEN** the RTGS simulator handles the request
- **THEN** the simulator SHALL produce a `REJECTED` RTGS outcome with settlement finality set to false

### Requirement: Provide classic rail Postman scenarios
The system SHALL provide Postman requests for local classic payment rail simulation exploration.

#### Scenario: Developer runs RTP baseline scenario
- **GIVEN** a developer has selected the local Postman environment
- **WHEN** the developer runs the RTP baseline scenario
- **THEN** the collection SHALL exercise the existing domestic payment API as the real-time rail baseline

#### Scenario: Developer runs ACH direct credit scenarios
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with ACH scopes
- **WHEN** the developer runs ACH Direct Credit Postman requests
- **THEN** the collection SHALL include settled, settlement pending or status query, partially returned, and rejected ACH batch scenarios with expected results

#### Scenario: Developer runs RTGS scenarios
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with RTGS scopes
- **WHEN** the developer runs RTGS Postman requests
- **THEN** the collection SHALL include corporate settled, FI settled, FI queued-for-liquidity, rejected, and status query scenarios with expected results

#### Scenario: Developer compares FI correspondent flow
- **GIVEN** the Postman collection includes classic payment rail scenarios
- **WHEN** a developer reviews the FI correspondent comparison journey
- **THEN** the collection SHALL reference existing FI correspondent payment and recall/investigation requests as a separate FI arrangement comparison without requiring runtime changes to those APIs

### Requirement: Document classic rail product taxonomy
Developer documentation and the root README SHALL describe RTP, ACH Direct Credit, RTGS, and FI correspondent payment as distinct product concepts.

#### Scenario: Developer reads repository front door
- **GIVEN** a developer opens the root README
- **WHEN** the developer reviews the product surface
- **THEN** the README SHALL describe the suite as a classic payment rail simulation capability including RTP baseline, ACH Direct Credit batch simulation, RTGS payment simulation, and FI correspondent payment as a separate arrangement

#### Scenario: Developer reads rail comparison documentation
- **GIVEN** developer support documentation is available
- **WHEN** a developer reads the classic rail simulation guide
- **THEN** the guide SHALL compare RTP, ACH, and RTGS across processing mode, clearing, settlement, finality, batching, lifecycle states, and typical corporate or FI client use cases

#### Scenario: Developer reads future recommendation context
- **GIVEN** developer or product documentation mentions payment rail recommendation
- **WHEN** a developer reads that documentation
- **THEN** the documentation SHALL state that AI recommendation, LLM integration, scoring, and rail optimization are future-facing ideas and not runtime behavior in this change

### Requirement: Align classic rail artifacts with behavior specs
OpenAPI documentation, Postman artifacts, simulator scenarios, README, developer documentation, and test fixtures SHALL align with the ACH and RTGS behavior specs.

#### Scenario: Classic rail artifact describes API behavior
- **GIVEN** OpenAPI documentation, Postman artifacts, simulator mappings, README content, developer documentation, or test fixtures are available
- **WHEN** a developer uses one of those artifacts
- **THEN** the artifact SHALL reflect the same endpoints, scopes, idempotency behavior, correlation header, status lifecycle, response fields, mock scenario controls, and error response behavior defined by the ACH and RTGS behavior specs

#### Scenario: Expected results are documented
- **GIVEN** manual testing or Postman documentation describes an ACH or RTGS scenario
- **WHEN** a developer reads expected results
- **THEN** the expected results SHALL include preconditions, required token scopes, mock scenario value, expected HTTP status, expected lifecycle status, key response fields, and common setup mistakes
- **AND** the expected results SHALL trace back to automated tests, runtime simulator mapping code, OpenAPI examples validated by tests, or another executable source of truth

### Requirement: Document payment rail recommendation API
The system SHALL provide OpenAPI 3.0.3 documentation for the payment rail recommendation API.

#### Scenario: Developer views recommendation contract
- **GIVEN** OpenAPI 3.0.3 documentation is available
- **WHEN** a developer opens the payment rail recommendation operation
- **THEN** the documentation SHALL describe `POST /v1/payment-rail-recommendations`, required scope, JSON request schema, JSON response schema, correlation header, validation errors, unsupported recommendation responses, and simulator-only limitations

#### Scenario: Developer views recommendation schemas
- **GIVEN** OpenAPI documentation describes recommendation schemas
- **WHEN** a developer reviews the request and response models
- **THEN** the documentation SHALL describe amount summary, payment count, debtor account profile, arrangement preference, recommended option, confidence level, matched factors, warnings, alternatives, tradeoffs, intent fit, and next API guidance

#### Scenario: Developer views simulator-only threshold
- **GIVEN** OpenAPI documentation describes recommendation behavior
- **WHEN** a developer reads threshold or rule descriptions
- **THEN** the documentation SHALL state that `100000 USD` is a deterministic simulator threshold and not a real scheme limit, bank policy, or production decision threshold

### Requirement: Provide recommendation Postman scenarios
The system SHALL provide Postman requests for local payment rail recommendation exploration.

#### Scenario: Developer runs RTP recommendation scenario
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with recommendation scope
- **WHEN** the developer runs the RTP recommendation scenario
- **THEN** the request SHALL exercise an immediate low-value corporate domestic USD intent and expect rail `RTP`

#### Scenario: Developer runs ACH batch recommendation scenario
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with recommendation scope
- **WHEN** the developer runs the ACH vendor batch recommendation scenario
- **THEN** the request SHALL exercise a multiple-payment domestic USD intent and expect rail `ACH`

#### Scenario: Developer runs ACH warning scenario
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with recommendation scope
- **WHEN** the developer runs the ACH payroll batch scenario without `maxSingleAmount`
- **THEN** the response SHALL recommend ACH and include a warning that high-value individual entries were not evaluated

#### Scenario: Developer runs batch conflict scenario
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with recommendation scope
- **WHEN** the developer runs the ACH batch scenario with high maximum single amount or finality signal
- **THEN** the response SHALL recommend ACH and include RTGS warning or alternative guidance

#### Scenario: Developer runs RTGS recommendation scenario
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with recommendation scope
- **WHEN** the developer runs the high-value corporate finality recommendation scenario
- **THEN** the request SHALL expect rail `RTGS` and arrangement `DOMESTIC_INTERBANK_GROSS_SETTLEMENT`

#### Scenario: Developer runs FI recommendation scenarios
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with recommendation scope
- **WHEN** the developer runs FI domestic gross settlement and FI correspondent account path scenarios
- **THEN** the collection SHALL demonstrate the distinction between `RTGS` with domestic interbank gross settlement and `FI_CORRESPONDENT` with correspondent account path

#### Scenario: Developer runs unsupported recommendation scenarios
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with recommendation scope
- **WHEN** the developer runs cross-border or non-USD recommendation scenarios
- **THEN** the response SHALL use recommendation status `UNSUPPORTED` and explain the unsupported condition

#### Scenario: Developer runs recommendation auth failure scenario
- **GIVEN** a developer has selected the local Postman environment
- **WHEN** the developer runs a recommendation request without `payment-rail-recommendations:create` scope
- **THEN** the collection SHALL demonstrate the consistent authorization error response

### Requirement: Document recommendation copilot boundaries
Developer documentation and README content SHALL explain payment rail recommendation as a deterministic simulator interface for future copilot capability.

#### Scenario: Developer reads recommendation guide
- **GIVEN** developer support documentation is available
- **WHEN** a developer reads the payment rail recommendation section or guide
- **THEN** the documentation SHALL explain supported recommendation inputs, rule precedence, rail and arrangement output, confidence level, warnings, alternatives, tradeoffs, intent fit, and next API guidance

#### Scenario: Developer reads out-of-scope boundaries
- **GIVEN** developer or product documentation describes recommendation behavior
- **WHEN** a developer reads the limitations
- **THEN** the documentation SHALL state that the MVP does not provide real AI, LLM integration, payment execution, recommendation persistence, cross-border support, FX, sanctions, fraud, pricing, liquidity, compliance decisions, or real rail availability decisions

#### Scenario: Developer reads next API guidance
- **GIVEN** documentation describes next API guidance
- **WHEN** a developer reviews supported recommendations
- **THEN** the documentation SHALL explain that next API guidance identifies candidate endpoint, method, scopes, headers, and payload format but does not generate a complete payment creation payload

### Requirement: Align recommendation artifacts with behavior specs
OpenAPI documentation, Postman artifacts, README, developer documentation, local token guidance, and tests SHALL align with the payment rail recommendation behavior specs.

#### Scenario: Recommendation artifact describes API behavior
- **GIVEN** OpenAPI documentation, Postman artifacts, README content, developer documentation, or tests describe payment rail recommendation behavior
- **WHEN** a developer uses one of those artifacts
- **THEN** the artifact SHALL reflect the same endpoint, scope, no-idempotency behavior, correlation header, request fields, response fields, rule precedence, unsupported result behavior, and simulator-only limitations defined by the behavior specs

#### Scenario: Recommendation token guidance is available
- **GIVEN** developer support documentation or Postman environment guidance is available
- **WHEN** a developer prepares to call `POST /v1/payment-rail-recommendations`
- **THEN** the artifacts SHALL describe how to generate or configure a local JWT containing `payment-rail-recommendations:create`

