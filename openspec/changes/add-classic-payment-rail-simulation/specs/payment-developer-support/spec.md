## ADDED Requirements

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
