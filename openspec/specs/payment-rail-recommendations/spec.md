# payment-rail-recommendations Specification

## Purpose
TBD - created by archiving change add-payment-rail-recommendation-api. Update Purpose after archive.
## Requirements
### Requirement: Accept payment rail recommendation requests
The system SHALL accept one JSON payment rail recommendation request at `POST /v1/payment-rail-recommendations`.

#### Scenario: Valid domestic USD intent is accepted
- **GIVEN** an authenticated client has `payment-rail-recommendations:create` scope
- **AND** the request body contains a supported domestic USD payment intent
- **WHEN** the client submits `POST /v1/payment-rail-recommendations`
- **THEN** the system SHALL return a JSON recommendation response with HTTP `200 OK`
- **AND** the response SHALL include recommendation ID, recommendation status, recommended option, confidence level, matched factors, tradeoffs, alternatives, warnings, next API guidance, and correlation ID

#### Scenario: Missing required intent field is rejected
- **GIVEN** an authenticated client submits a recommendation request missing a required field
- **WHEN** the system validates the request
- **THEN** the system SHALL reject the request with a consistent validation error and field-level details

#### Scenario: Recommendation request omits account identifiers
- **GIVEN** an authenticated client submits a recommendation request
- **WHEN** the request is validated
- **THEN** the system SHALL NOT require debtor account number, creditor account number, settlement account number, receiver account number, or correspondent account reference

### Requirement: Validate rail-neutral recommendation intent
The system SHALL validate rail-neutral payment intent fields before applying recommendation rules.

#### Scenario: Required intent fields are present
- **GIVEN** a recommendation request includes `clientSegment`, `paymentCount`, `amountSummary.currency`, `amountSummary.totalAmount`, `debtorCountry`, `creditorCountry`, and `urgency`
- **WHEN** the system validates the request
- **THEN** the system SHALL allow the request to proceed to recommendation rule evaluation

#### Scenario: Multiple payment intent uses amount summary
- **GIVEN** a recommendation request has `paymentCount` greater than one
- **AND** the request includes `amountSummary.totalAmount`
- **WHEN** the system evaluates the intent
- **THEN** the system SHALL treat `paymentCount` as an intent-level volume signal
- **AND** the system SHALL NOT require an `averageAmount` request field

#### Scenario: Maximum single amount is omitted for a batch intent
- **GIVEN** a recommendation request has `paymentCount` greater than one
- **AND** `amountSummary.maxSingleAmount` is omitted
- **WHEN** the system creates a recommendation
- **THEN** the system SHALL still return a recommendation when other required fields are valid
- **AND** the response SHALL include a warning that high-value individual entries were not evaluated

#### Scenario: Debtor account profile is omitted
- **GIVEN** a recommendation request omits `debtorAccountProfile.count`
- **WHEN** the system evaluates the intent
- **THEN** the system SHALL treat debtor account count as one for recommendation purposes

### Requirement: Return rail and arrangement recommendation
The system SHALL return recommendations using both rail and arrangement fields.

#### Scenario: RTP is recommended
- **GIVEN** a domestic USD single-payment intent is immediate, at or below the simulator threshold, and does not require settlement finality
- **WHEN** the system evaluates the recommendation rules
- **THEN** the system SHALL recommend rail `RTP`
- **AND** the system SHALL use arrangement `DOMESTIC_REAL_TIME_CLEARING`

#### Scenario: ACH is recommended
- **GIVEN** a domestic USD intent has `paymentCount` greater than one or `batchPreferred` set to true
- **WHEN** the system evaluates the recommendation rules
- **THEN** the system SHALL recommend rail `ACH`
- **AND** the system SHALL use arrangement `BATCH_CLEARING_NET_SETTLEMENT`

#### Scenario: RTGS is recommended
- **GIVEN** a domestic USD single-payment intent requires finality or has amount at or above the simulator high-value threshold
- **WHEN** the system evaluates the recommendation rules
- **THEN** the system SHALL recommend rail `RTGS`
- **AND** the system SHALL use arrangement `DOMESTIC_INTERBANK_GROSS_SETTLEMENT`

#### Scenario: FI correspondent is recommended
- **GIVEN** a domestic USD FI client intent has arrangement preference `CORRESPONDENT_ACCOUNT_PATH`
- **WHEN** the system evaluates the recommendation rules
- **THEN** the system SHALL recommend rail `FI_CORRESPONDENT`
- **AND** the system SHALL use arrangement `CORRESPONDENT_ACCOUNT_PATH`

### Requirement: Apply deterministic recommendation precedence
The system SHALL apply deterministic recommendation rules in a stable precedence order.

#### Scenario: Unsupported guardrail takes precedence
- **GIVEN** a recommendation request contains a valid but unsupported non-domestic or non-USD intent
- **WHEN** the system evaluates the request
- **THEN** the system SHALL return recommendation status `UNSUPPORTED`
- **AND** the system SHALL NOT return a recommended option or next API guidance

#### Scenario: FI correspondent preference takes precedence
- **GIVEN** a recommendation request has client segment `FI` and arrangement preference `CORRESPONDENT_ACCOUNT_PATH`
- **WHEN** the system evaluates the request
- **THEN** the system SHALL recommend FI correspondent account path before evaluating ACH, RTGS, or RTP default rules

#### Scenario: Batch signal takes precedence over finality signal
- **GIVEN** a recommendation request has `paymentCount` greater than one
- **AND** the request also indicates finality or high-value signals
- **WHEN** the system evaluates the request
- **THEN** the system SHALL recommend ACH as the primary option
- **AND** the system SHALL include RTGS warning or alternative guidance for conflicting high-value or finality factors

#### Scenario: Immediate low-value single payment falls back to RTP
- **GIVEN** a recommendation request is domestic USD, has `paymentCount` equal to one, urgency `IMMEDIATE`, total amount at or below the simulator threshold, and no finality requirement
- **WHEN** the system evaluates the request
- **THEN** the system SHALL recommend RTP

### Requirement: Explain recommendation rationale
The system SHALL return structured explanation fields for supported recommendations.

#### Scenario: Recommendation includes confidence level
- **GIVEN** a recommendation is supported
- **WHEN** the system returns the response
- **THEN** the response SHALL include `confidenceLevel` with value `HIGH`, `MEDIUM`, or `LOW`
- **AND** the response SHALL NOT include a numeric confidence score

#### Scenario: Recommendation includes matched factors
- **GIVEN** a recommendation rule matches one or more intent factors
- **WHEN** the system returns the response
- **THEN** the response SHALL include matched factor codes describing the signals that drove the recommendation

#### Scenario: Recommendation includes structured tradeoffs
- **GIVEN** a supported recommendation is returned
- **WHEN** the system builds tradeoffs
- **THEN** each tradeoff SHALL include rail, arrangement, speed, cost, finality, intent fit, intent fit reason, and summary

#### Scenario: Intent fit is simulator-only
- **GIVEN** a response includes `intentFit`
- **WHEN** a developer reads the response or OpenAPI description
- **THEN** the system SHALL describe `intentFit` as simulator rule explanation
- **AND** the system SHALL NOT describe it as bank operational capability, production suitability score, compliance result, pricing result, or real rail SLA

### Requirement: Return next API guidance without payment creation
The system SHALL provide next API guidance for supported recommendations without creating or drafting a complete payment request.

#### Scenario: ACH guidance is returned
- **GIVEN** ACH is recommended
- **WHEN** the system returns next API guidance
- **THEN** the response SHALL identify `POST /v1/ach-batches`, required scopes, required headers, and JSON batch payload format

#### Scenario: RTGS guidance is returned
- **GIVEN** RTGS is recommended
- **WHEN** the system returns next API guidance
- **THEN** the response SHALL identify `POST /v1/rtgs-payments`, required scopes, required headers, and JSON payment payload format

#### Scenario: FI correspondent guidance is returned
- **GIVEN** FI correspondent is recommended
- **WHEN** the system returns next API guidance
- **THEN** the response SHALL identify `POST /v1/fi-payments`, required scopes, required headers, and supported FI payment payload format

#### Scenario: Recommendation does not create payment
- **GIVEN** a recommendation request is accepted
- **WHEN** the system returns a recommendation response
- **THEN** the system SHALL NOT create a domestic payment, ACH batch, RTGS payment, FI payment, or recall/investigation record
- **AND** the system SHALL NOT return a complete create-payment payload

### Requirement: Keep recommendation runtime simulator-only
The system SHALL keep payment rail recommendation runtime deterministic and simulator-only.

#### Scenario: Recommendation is generated
- **WHEN** the system generates a payment rail recommendation
- **THEN** the system SHALL use deterministic local rules
- **AND** the system SHALL NOT call a real AI model, LLM provider, rail pricing service, liquidity service, sanctions service, fraud service, FX service, or real payment rail

#### Scenario: Simulator threshold is used
- **GIVEN** the recommendation rules use a `100000 USD` threshold
- **WHEN** documentation or response examples describe that threshold
- **THEN** the system SHALL state that the threshold is a simulator rule and not a real payment scheme limit, bank policy, or production decision threshold

#### Scenario: Recommendation ID is returned
- **GIVEN** a recommendation response includes a recommendation ID
- **WHEN** the client receives the response
- **THEN** the recommendation ID SHALL be a response-local diagnostic reference
- **AND** the system SHALL NOT expose a recommendation query endpoint in the MVP

