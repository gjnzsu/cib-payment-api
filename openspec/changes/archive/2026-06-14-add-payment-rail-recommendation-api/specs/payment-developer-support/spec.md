## ADDED Requirements

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
