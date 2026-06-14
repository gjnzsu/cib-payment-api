## ADDED Requirements

### Requirement: Enforce recommendation authorization scope
The system SHALL require JWT Bearer authentication and `payment-rail-recommendations:create` scope for payment rail recommendation requests.

#### Scenario: Client has recommendation create scope
- **GIVEN** an authenticated client has the `payment-rail-recommendations:create` scope
- **WHEN** the client calls `POST /v1/payment-rail-recommendations`
- **THEN** the system SHALL allow the request to proceed to recommendation request validation

#### Scenario: Client lacks recommendation create scope
- **GIVEN** an authenticated client lacks the `payment-rail-recommendations:create` scope
- **WHEN** the client calls `POST /v1/payment-rail-recommendations`
- **THEN** the system SHALL reject the request with a consistent authorization error

#### Scenario: Recommendation request is unauthenticated
- **GIVEN** a client omits a valid Bearer token
- **WHEN** the client calls `POST /v1/payment-rail-recommendations`
- **THEN** the system SHALL reject the request with a consistent authentication error

### Requirement: Do not require idempotency for recommendation requests
The system SHALL NOT require an `Idempotency-Key` header for payment rail recommendation requests because the MVP recommendation endpoint is stateless and does not create payment resources.

#### Scenario: Recommendation request omits idempotency key
- **GIVEN** an authenticated and authorized client submits a valid recommendation request without an `Idempotency-Key`
- **WHEN** the system validates request headers
- **THEN** the system SHALL continue recommendation processing without idempotency validation

#### Scenario: Recommendation request includes idempotency key
- **GIVEN** an authenticated and authorized client submits a recommendation request with an `Idempotency-Key`
- **WHEN** the system validates request headers
- **THEN** the system SHALL NOT create or evaluate an idempotency record for the recommendation request

### Requirement: Propagate recommendation correlation context
The system SHALL accept or generate `X-Correlation-ID` for payment rail recommendation requests and propagate it through response headers, response body, logs, and recommendation rule evaluation.

#### Scenario: Recommendation request includes correlation ID
- **GIVEN** an authenticated client includes an `X-Correlation-ID` header
- **WHEN** the client submits `POST /v1/payment-rail-recommendations`
- **THEN** the system SHALL use that correlation ID in the response header, response body, logs, and recommendation rule context

#### Scenario: Recommendation request omits correlation ID
- **GIVEN** an authenticated client omits `X-Correlation-ID`
- **WHEN** the client submits `POST /v1/payment-rail-recommendations`
- **THEN** the system SHALL generate a correlation ID
- **AND** the system SHALL include the generated correlation ID in the response header, response body, logs, and recommendation rule context

### Requirement: Protect recommendation request data in logs
The system SHALL avoid logging bearer tokens, raw recommendation request payloads, or sensitive payment intent values in plain text.

#### Scenario: Recommendation request is logged
- **GIVEN** the system handles a payment rail recommendation request
- **WHEN** the system records logs or diagnostics
- **THEN** the logs and diagnostics SHALL NOT contain bearer tokens or raw request payloads

#### Scenario: Recommendation request contains unexpected sensitive fields
- **GIVEN** a client submits unexpected fields that could contain account identifiers or sensitive payment data
- **WHEN** the system rejects or logs the request
- **THEN** the logs and diagnostics SHALL omit or mask full sensitive values
