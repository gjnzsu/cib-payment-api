## ADDED Requirements

### Requirement: Create domestic RTP payment
The system SHALL expose `POST /v1/domestic-payments` to create one domestic real-time payment instruction per request using a JSON business payload.

#### Scenario: Payment creation request is accepted
- **GIVEN** an authenticated and authorized client
- **AND** a valid domestic real-time payment request with a valid `Idempotency-Key`
- **WHEN** the client submits `POST /v1/domestic-payments`
- **THEN** the system SHALL create a unique UUID string `paymentId`
- **AND** the system SHALL return `202 Accepted` with `paymentId`, current `status`, `createdAt`, `updatedAt`, `correlationId`, and `links.status`

#### Scenario: Request contains more than one payment instruction
- **GIVEN** a payment creation request containing multiple payment instructions
- **WHEN** a client submits `POST /v1/domestic-payments`
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: Request is outside domestic RTP scope
- **GIVEN** a payment creation request for a cross-border, batch, recurring, cancellation, amendment, or non-real-time payment
- **WHEN** a client submits `POST /v1/domestic-payments`
- **THEN** the system SHALL reject the request with a consistent validation error

### Requirement: Validate domestic RTP payment payload
The system SHALL validate required JSON payment request fields before accepting a payment for processing.

#### Scenario: Required payment fields are present
- **GIVEN** a payment creation request contains `debtorAccount`, `creditorAccount`, `amount`, and `paymentReference`
- **AND** each account contains `bankCode`, `accountNumber`, and `accountName`
- **AND** `amount` contains `currency` and decimal string `value`
- **WHEN** the system validates the JSON payload
- **THEN** the system SHALL continue payment creation processing

#### Scenario: Optional payment fields are present
- **GIVEN** a payment creation request contains optional `remittanceInformation` or `requestedExecutionDate`
- **WHEN** the system validates the JSON payload
- **THEN** the system SHALL accept those fields only when the request remains within domestic real-time payment scope

#### Scenario: Required payment fields are missing or invalid
- **GIVEN** a payment creation request is missing required fields or contains invalid field values
- **WHEN** the system validates the JSON payload
- **THEN** the system SHALL reject the request with a consistent validation error that includes the correlation ID

#### Scenario: Amount format is invalid
- **GIVEN** a payment creation request contains an amount value that is not a valid decimal string
- **WHEN** the system validates the JSON payload
- **THEN** the system SHALL reject the request with a consistent field-level validation error

#### Scenario: Currency is unsupported
- **GIVEN** a payment creation request contains a currency that does not match the configured domestic RTP currency
- **WHEN** the system validates the JSON payload
- **THEN** the system SHALL reject the request with a consistent validation error

#### Scenario: Request contains unknown top-level fields
- **GIVEN** a payment creation request contains top-level fields outside the supported payment contract
- **WHEN** the system validates the JSON payload
- **THEN** the system SHALL reject the request with a consistent field-level validation error

### Requirement: Track asynchronous payment status
The system SHALL track payment status asynchronously after a payment creation request is accepted.

#### Scenario: Payment starts asynchronous processing
- **GIVEN** the system has accepted a valid payment creation request
- **WHEN** asynchronous payment processing starts
- **THEN** the system SHALL persist a payment status for the generated `paymentId`

#### Scenario: Payment status changes after downstream processing
- **GIVEN** a payment has a stored status record
- **WHEN** downstream mock processing produces a terminal or intermediate outcome
- **THEN** the system SHALL update the stored payment status for the payment

### Requirement: Query domestic RTP payment status
The system SHALL expose `GET /v1/domestic-payments/{paymentId}` to return the current known status of a payment.

#### Scenario: Existing payment status is queried
- **GIVEN** an authenticated and authorized client
- **AND** a known `paymentId`
- **WHEN** the client submits `GET /v1/domestic-payments/{paymentId}`
- **THEN** the system SHALL return `paymentId`, current `status`, `createdAt`, `updatedAt`, `correlationId`, and `links.self`
- **AND** the system SHALL include `reason` details when the current status is `REJECTED`, `FAILED`, or `TIMEOUT`

#### Scenario: Unknown payment status is queried
- **GIVEN** an authenticated and authorized client
- **AND** an unknown `paymentId`
- **WHEN** the client submits `GET /v1/domestic-payments/{paymentId}`
- **THEN** the system SHALL return a consistent not-found error without exposing unrelated payment data

### Requirement: Support payment status lifecycle
The system SHALL support the MVP payment statuses `ACCEPTED`, `PROCESSING`, `COMPLETED`, `REJECTED`, `FAILED`, and `TIMEOUT`.

#### Scenario: Payment is accepted
- **GIVEN** a valid payment creation request
- **WHEN** the API accepts the request
- **THEN** the payment status SHALL be recorded as `ACCEPTED` or another valid processing status

#### Scenario: Payment reaches terminal status
- **GIVEN** a payment is being processed asynchronously
- **WHEN** downstream mock processing completes with success, rejection, timeout, or internal failure
- **THEN** the payment status SHALL become `COMPLETED`, `REJECTED`, `TIMEOUT`, or `FAILED` according to the downstream outcome

### Requirement: Map downstream mock outcomes to payment statuses
The system SHALL map downstream payment processor mock outcomes to the payment status lifecycle.

#### Scenario: Downstream mock returns success
- **GIVEN** an accepted payment is sent to the downstream payment processor mock
- **WHEN** the downstream mock returns a success outcome
- **THEN** the system SHALL record the payment status as `COMPLETED`

#### Scenario: Downstream mock returns rejection
- **GIVEN** an accepted payment is sent to the downstream payment processor mock
- **WHEN** the downstream mock returns a business rejection outcome
- **THEN** the system SHALL record the payment status as `REJECTED` with reason details

#### Scenario: Downstream mock times out
- **GIVEN** an accepted payment is sent to the downstream payment processor mock
- **WHEN** the downstream mock simulates a timeout after payment acceptance
- **THEN** the system SHALL record the payment status as `TIMEOUT`

#### Scenario: Downstream mock returns internal failure
- **GIVEN** an accepted payment is sent to the downstream payment processor mock
- **WHEN** the downstream mock returns an internal failure outcome after payment acceptance
- **THEN** the system SHALL record the payment status as `FAILED`
