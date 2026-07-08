## ADDED Requirements

### Requirement: Advisor scenario catalog
The system SHALL provide a curated Payment Scenario Advisor catalog with four MVP scenarios: urgent low-value supplier payment, vendor batch payment, high-value treasury transfer, and FI correspondent settlement.

#### Scenario: List supported advisor scenarios
- **WHEN** an API user requests the advisor scenario catalog
- **THEN** the system returns the four supported scenarios with stable scenario IDs, business labels, recommended rail, arrangement, and simulator-only indicator

#### Scenario: Retrieve one advisor scenario
- **WHEN** an API user requests one supported scenario by scenario ID
- **THEN** the system returns the scenario details, recommendation intent, recommendation summary, simulation plan, and feedback report framing for that scenario

#### Scenario: Reject unknown advisor scenario
- **WHEN** an API user requests an unsupported scenario ID
- **THEN** the system returns a consistent JSON `404` error response with a stable error code and correlation ID

### Requirement: Advisor recommendation mapping
The system SHALL map each supported payment scenario to deterministic recommendation intent and expected rail guidance that aligns with the existing payment rail recommendation rules.

#### Scenario: Urgent supplier scenario maps to RTP
- **WHEN** the urgent low-value supplier payment scenario is selected
- **THEN** the advisor identifies RTP with domestic real-time clearing as the recommended path

#### Scenario: Vendor batch scenario maps to ACH
- **WHEN** the vendor batch payment scenario is selected
- **THEN** the advisor identifies ACH with batch clearing net settlement as the recommended path

#### Scenario: High-value treasury scenario maps to RTGS
- **WHEN** the high-value treasury transfer scenario is selected
- **THEN** the advisor identifies RTGS with domestic interbank gross settlement as the recommended path

#### Scenario: FI correspondent scenario maps to FI correspondent arrangement
- **WHEN** the FI correspondent settlement scenario is selected
- **THEN** the advisor identifies FI correspondent with correspondent account path as the recommended path

### Requirement: Advisor simulation plan
The system SHALL present simulator validation guidance without executing production payment routing or creating payment resources.

#### Scenario: Advisor returns simulator plan
- **WHEN** a supported advisor scenario is returned
- **THEN** the response includes simulator method, endpoint, required scopes, idempotency requirement, payload format, mock scenario, expected status, and status lookup guidance

#### Scenario: Advisor marks simulation as explicit user action
- **WHEN** a supported advisor scenario is returned
- **THEN** the response indicates that user confirmation is required before running any simulator journey

#### Scenario: Advisor keeps mock metadata scoped
- **WHEN** a supported advisor scenario includes mock scenario metadata
- **THEN** the mock scenario appears only inside simulator guidance and is not presented as production payment semantics

### Requirement: Minimal advisor UI
The system SHALL serve a minimal Payment Scenario Advisor UI that visualizes the recommendation-to-simulation feedback loop.

#### Scenario: Advisor UI is available
- **WHEN** a user opens the advisor UI path
- **THEN** the system returns an HTML page with the Payment Scenario Advisor experience

#### Scenario: Advisor UI renders feedback loop
- **WHEN** the advisor UI loads the scenario catalog
- **THEN** the UI presents scenario selection, recommendation review, simulation plan, and validation result sections

#### Scenario: Advisor UI remains simulator-only
- **WHEN** the advisor UI presents simulation details
- **THEN** the UI labels the flow as simulator-only and does not claim real rail availability, real payment execution, or production routing

### Requirement: Advisor boundary protection
The system SHALL keep the Payment Scenario Advisor separate from secured payment execution semantics and sensitive payment data.

#### Scenario: Advisor does not require idempotency
- **WHEN** an API user requests advisor scenario metadata
- **THEN** the system does not require `Idempotency-Key` because the advisor does not create payment, recommendation, simulation, or report records

#### Scenario: Advisor does not expose sensitive account data
- **WHEN** advisor scenario metadata is returned
- **THEN** the response does not include full account identifiers, bearer tokens, signing keys, or raw sensitive payment payloads

#### Scenario: Existing payment APIs remain unchanged
- **WHEN** the advisor capability is added
- **THEN** existing payment rail recommendation, RTP, ACH, RTGS, and FI correspondent simulator API contracts remain unchanged
