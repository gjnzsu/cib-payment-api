## Why

Collections currently require `mandateReference` as evidence that payer authorization already exists, but the end-to-end pull-payment journey has no way to simulate mandate setup before collection execution. Adding a mandate simulation API lets product demos and developer tests show the authorization step without turning the MVP into a production mandate platform.

## What Changes

- Add a JSON mandate simulation API for creating and querying direct debit mandates.
- Add deterministic mandate mock scenarios for active, pending authorization, payer rejection, expiry, timeout, and internal failure.
- Add optional mandate cancellation simulation for active or pending mandates.
- Generate a system `mandateId` and support a business `mandateReference` on mandate creation.
- Keep Collections compatible with external `mandateReference` values.
- Allow Collections to recognize system-created `ACTIVE` mandates while rejecting system-created inactive mandates when their `mandateReference` is used.
- Extend OpenAPI, Postman, README/developer documentation, and tests for the mandate-to-collection journey.

## Capabilities

### New Capabilities
- `mandate-simulation`: Simulates direct debit mandate creation, status query, cancellation, ownership isolation, idempotency, mock outcomes, and sensitive logging boundaries.

### Modified Capabilities
- `collections-simulation`: Collections can use system-created active mandate references while still accepting external mandate references as simulator evidence.

## Impact

- New external endpoints under `/v1/mandates`.
- New JWT scopes: `mandates:create`, `mandates:read`, and `mandates:cancel`.
- New domain models, application services, repository port, in-memory persistence, deterministic simulator, controller, DTOs, and tests.
- Updates to OpenAPI 3.0.3, Postman collection/environment, README, and developer support documentation.
