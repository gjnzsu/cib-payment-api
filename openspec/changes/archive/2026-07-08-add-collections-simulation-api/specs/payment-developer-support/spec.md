## ADDED Requirements

### Requirement: Document collections simulation APIs
The system SHALL provide OpenAPI 3.0.3 documentation for collection creation and collection status query APIs.

#### Scenario: Developer views collections API contract
- **GIVEN** OpenAPI 3.0.3 documentation is available
- **WHEN** a developer opens the collections operations
- **THEN** the documentation SHALL describe `POST /v1/collections`, `GET /v1/collections/{collectionId}`, required scopes, idempotency requirements, JSON request and response schemas, supported collection profiles, simulator scenarios, and error responses

#### Scenario: Developer views collection profile limitations
- **GIVEN** OpenAPI documentation describes collections behavior
- **WHEN** a developer reads the profile descriptions
- **THEN** the documentation SHALL state that `US_ACH_DIRECT_DEBIT_BATCH` and `HK_FPS_DIRECT_DEBIT` are simulator profiles and do not provide real ACH, NACHA, HK FPS, HKICL, eDDA, clearing, settlement, account validation, balance check, fraud, sanctions, or production decisioning connectivity

### Requirement: Support deterministic collection simulator scenarios
The collection simulator SHALL support deterministic local/test-only scenarios for US ACH Direct Debit and HK FPS Direct Debit collection outcomes.

#### Scenario: US ACH Direct Debit collected scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `us_ach_debit_collected`
- **WHEN** the collection simulator handles a `US_ACH_DIRECT_DEBIT_BATCH` request
- **THEN** the simulator SHALL produce a `COLLECTED` collection outcome with collected entry summaries

#### Scenario: US ACH Direct Debit partially returned scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `us_ach_debit_partially_returned`
- **WHEN** the collection simulator handles a `US_ACH_DIRECT_DEBIT_BATCH` request
- **THEN** the simulator SHALL produce a `PARTIALLY_RETURNED` collection outcome with at least one returned entry summary

#### Scenario: HK FPS collection completed scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `hk_fps_collection_completed`
- **WHEN** the collection simulator handles a `HK_FPS_DIRECT_DEBIT` request
- **THEN** the simulator SHALL produce a `COMPLETED` collection outcome

#### Scenario: HK FPS collection pending authorization scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `hk_fps_collection_pending_authorization`
- **WHEN** the collection simulator handles a `HK_FPS_DIRECT_DEBIT` request
- **THEN** the simulator SHALL produce a `PENDING_AUTHORIZATION` collection outcome

#### Scenario: Collection rejection scenarios are selected
- **GIVEN** a local or test request includes a collection rejection mock scenario
- **WHEN** the collection simulator handles the request
- **THEN** the simulator SHALL produce a `REJECTED` collection outcome with deterministic reason details

#### Scenario: Collection timeout or failure scenario is selected
- **GIVEN** a local or test request includes `X-Mock-Scenario` with value `collection_timeout` or `collection_internal_failure`
- **WHEN** the collection simulator handles the request
- **THEN** the simulator SHALL produce a `TIMEOUT` or `FAILED` collection outcome with deterministic reason details

### Requirement: Provide collection Postman scenarios
The system SHALL provide Postman requests for local collections simulation exploration.

#### Scenario: Developer runs US ACH Direct Debit collection scenarios
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with collection scopes
- **WHEN** the developer runs US ACH Direct Debit collection Postman requests
- **THEN** the collection SHALL include collected, settlement pending, partially returned, rejected authorization, insufficient funds, timeout, and status query scenarios with expected results

#### Scenario: Developer runs HK FPS Direct Debit collection scenarios
- **GIVEN** a developer has selected the local Postman environment and configured a JWT with collection scopes
- **WHEN** the developer runs HK FPS Direct Debit collection Postman requests
- **THEN** the collection SHALL include completed, pending authorization, invalid authorization, insufficient funds, timeout, and status query scenarios with expected results

### Requirement: Document collections product taxonomy
Developer documentation and the root README SHALL describe collections as a pull-payment product capability distinct from push-payment rails.

#### Scenario: Developer reads repository front door
- **GIVEN** a developer opens the root README
- **WHEN** the developer reviews the product surface
- **THEN** the README SHALL describe collections simulation as a pre-authorized pull-payment capability alongside RTP, ACH Direct Credit, RTGS, FI correspondent, and payment rail recommendations

#### Scenario: Developer reads collections guide
- **GIVEN** developer support documentation is available
- **WHEN** a developer reads the collections simulation guide
- **THEN** the guide SHALL compare US ACH Direct Debit and HK FPS Direct Debit profiles across business use case, authorization reference, request shape, status lifecycle, simulator scenarios, and out-of-scope production behavior

### Requirement: Align collections artifacts with behavior specs
OpenAPI documentation, Postman artifacts, simulator mappings, README content, developer documentation, and tests SHALL align with the collections behavior specs.

#### Scenario: Collections artifact describes API behavior
- **GIVEN** OpenAPI documentation, Postman artifacts, simulator mappings, README content, developer documentation, or tests describe collections behavior
- **WHEN** a developer uses one of those artifacts
- **THEN** the artifact SHALL reflect the same endpoints, scopes, idempotency behavior, correlation header, collection profiles, status lifecycle, response fields, mock scenario controls, and simulator-only limitations defined by the behavior specs

#### Scenario: Collections token guidance is available
- **GIVEN** developer support documentation or Postman environment guidance is available
- **WHEN** a developer prepares to call the collections API
- **THEN** the artifacts SHALL describe how to generate or configure a local JWT containing `collections:create` and `collections:read`
