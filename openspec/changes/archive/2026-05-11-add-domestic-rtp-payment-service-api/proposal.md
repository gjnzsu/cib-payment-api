## Why

### Problem Statement

Corporate clients, fintech applications, ERP platforms, and treasury systems increasingly need to embed bank payment capabilities directly into their own workflows.

However, domestic real-time payment integration is often slow and costly because each client must deal with payment scheme rules, account formats, authentication, idempotency, validation, status tracking, and downstream processing differences.

The bank needs a secure, reusable, and developer-friendly Payment API that exposes domestic real-time payment capability as a service, while hiding internal payment processing complexity from external clients.

### Business Value

This API allows the bank to productize domestic real-time payment capability as an external-facing service for corporate clients, fintech partners, and embedded banking platforms.

It creates value by:

- Enabling Banking-as-a-Service and embedded banking payment use cases.
- Accelerating client and partner onboarding through a clear OpenAPI-based contract.
- Reducing custom integration cost by replacing bespoke host-to-host or file-based integrations with a reusable API.
- Improving developer experience through JSON payloads, clear errors, sandbox support, and downstream mocks.
- Supporting safer payment initiation through JWT authentication, idempotency, and downstream authorization controls.
- Creating a foundation for future capabilities such as encrypted ISO 20022 payloads, cross-border payments, batch payments, webhooks, and developer portal integration.

### Why

This change is the first step toward exposing the bank's payment capability as a reusable Payment-as-a-Service offering.

The MVP focuses on domestic real-time payment initiation because it is a high-value embedded banking use case with a clear API boundary and manageable implementation scope.

The initial version uses a JSON business payload, asynchronous status tracking, idempotency control, and a downstream payment processor mock so the API can be specified, implemented, tested, and demonstrated without depending on real core banking or payment scheme connectivity.

## What Changes

This change adds an MVP version of an external-facing Domestic Real-Time Payment Service API.

The API will support:

- Creating a domestic real-time payment instruction.
- Querying payment status by payment ID.
- JSON-based business payment payload.
- JWT Bearer token authentication.
- Idempotent payment creation using `Idempotency-Key`.
- Asynchronous payment processing model.
- Payment status lifecycle.
- Downstream payment processor mock for local development and automated testing.
- Mock payment request data for contract, integration, downstream behavior, and idempotency tests.
- API exposure through GKE Gateway API or Kubernetes Ingress for the GKE-based MVP.
- OpenAPI 3.0.3 documentation for developer-facing API usage.
- Postman collection and environment files for API debugging and local testing.

### API Capabilities

The MVP API will include the following endpoints:

- `POST /v1/domestic-payments`
  - Create a domestic real-time payment.
  - Return `202 Accepted` when the payment request is accepted for processing.
- `GET /v1/domestic-payments/{paymentId}`
  - Query the current status of a payment.

### Key Behaviors

The system will:

- Validate JWT tokens before processing requests.
- Validate required payment request fields.
- Require a valid `Idempotency-Key` for payment creation.
- Prevent duplicate payment creation for repeated requests.
- Return the same response when the same `Idempotency-Key` and same request body are submitted.
- Return `409 Conflict` when the same `Idempotency-Key` is reused with a different request body.
- Create a unique `paymentId` for accepted payment requests.
- Track payment status asynchronously.
- Call a downstream payment processor mock instead of a real payment system.
- Support downstream mock scenarios for success, rejection, timeout, and internal failure.
- Provide consistent error responses for validation, authentication, authorization, idempotency conflict, and downstream failure scenarios.
- Include or generate a correlation ID for request tracing.

### Payment Scope

This change is limited to:

- Domestic payment only.
- Real-time payment scheme only.
- JSON business payload only.
- Single payment instruction per API request.
- External-facing payment initiation and status query only.
- Mock downstream payment processor only.
- GKE Gateway API or Kubernetes Ingress for API exposure in the GKE-based MVP.
- Gateway-like MVP application controls implemented inside the Payment Service API.
- MVP-level in-memory or lightweight persistence for payment status and idempotency tracking.

### Out of Scope

The following capabilities are not included in this change:

- Cross-border payment.
- Batch payment.
- Standing instruction or recurring payment.
- Full ISO 20022 XML ingestion.
- Encrypted ISO 20022 `pacs.008` payload.
- Real core banking integration.
- Real payment scheme connectivity.
- Real clearing or settlement.
- Real account balance check.
- Real fraud screening.
- Real sanctions screening.
- Real FX conversion.
- Payment cancellation.
- Payment amendment.
- Webhook or callback notification.
- Custom lightweight API Gateway service for the GKE-based MVP.
- Dedicated API management platform implementation such as Apigee or Kong.
- Production-grade database design.
- Production-grade key management or secret rotation.

### Non-Functional Requirements

The MVP should consider the following non-functional requirements:

- The API should support 50 TPS.
- P95 latency for accepted payment requests should be less than 1 second under the target load.
- The API should use JWT Bearer authentication.
- The API should support scope-based access control at the API layer.
- The API should implement MVP gateway-like controls inside the Payment Service API, including JWT validation, scope validation, correlation ID handling, request validation, and consistent error handling.
- The API should pass authorization context to the downstream payment processor mock for future micro-authorization extension.
- The API should not log sensitive account data in plain text.
- The API should mask or avoid logging full account identifiers.
- The API should include or generate a correlation ID for request tracing.
- The API should expose OpenAPI documentation.
- The API should support local development and automated testing using mock downstream services and mock test data.
- The API should support developer-friendly local debugging through Postman collection and environment configuration.
- The API should return consistent JSON error responses.

### Dependencies

This change depends on:

- OpenAPI 3.0.3 for API contract definition.
- JWT validation middleware or library.
- A local or in-memory persistence mechanism for MVP payment status tracking.
- A local or in-memory persistence mechanism for idempotency records.
- A mock downstream payment processor.
- GKE Gateway API or Kubernetes Ingress for exposing the MVP API on GKE.
- Test fixtures for successful, rejected, failed, timeout, invalid request, and idempotency scenarios.
- Swagger UI, Redoc, or equivalent tooling for rendering API documentation.
- Postman collection and environment files for local API debugging.

## Capabilities

### New Capabilities

- `domestic-rtp-payments`: External-facing domestic real-time payment initiation and asynchronous status query capability for Banking-as-a-Service and embedded banking clients.
- `payment-authentication-and-idempotency`: JWT Bearer authentication, scope-based API authorization, `Idempotency-Key` handling, duplicate request replay, and idempotency conflict behavior for payment creation.
- `payment-developer-support`: OpenAPI 3.0.3 documentation, downstream payment processor mock, mock payment test data, and Postman collection and environment files for local development, contract testing, integration testing, and API debugging.

### Modified Capabilities

- None.

## Impact

Affected APIs, dependencies, and systems include:

- New external API contract for `POST /v1/domestic-payments`.
- New external API contract for `GET /v1/domestic-payments/{paymentId}`.
- GKE Gateway API or Kubernetes Ingress exposure for the MVP.
- JWT validation middleware or library.
- Scope-based access control at the API layer.
- Idempotency storage for payment creation requests.
- Payment status storage for asynchronous tracking.
- Downstream payment processor mock.
- Mock payment request fixtures for success, rejection, timeout, failure, invalid request, and idempotency scenarios.
- OpenAPI 3.0.3 documentation rendered through Swagger UI, Redoc, or equivalent tooling.
- Postman collection and environment files for creating payments, querying payment status, and testing common error scenarios.

## Success Criteria

This change is considered successful when:

- `POST /v1/domestic-payments` is implemented according to the OpenAPI contract.
- `GET /v1/domestic-payments/{paymentId}` is implemented according to the OpenAPI contract.
- JWT authentication is enforced.
- The API can be exposed through GKE Gateway API or Kubernetes Ingress without a custom lightweight API Gateway service.
- Required request validation is implemented.
- `Idempotency-Key` behavior is implemented and tested.
- Payment status can be created and queried.
- Downstream payment processor mock is available for local development and automated tests.
- Mock payment test data is available.
- Success, rejection, timeout, failure, invalid request, and idempotency scenarios are covered by tests.
- OpenAPI documentation can be rendered through Swagger UI, Redoc, or equivalent tooling.
- Postman collection and environment files are available for creating payments, querying payment status, and testing common error scenarios.
- Contract tests and key integration tests pass.
