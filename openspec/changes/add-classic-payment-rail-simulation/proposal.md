## Why

The simulator currently demonstrates domestic RTP, HK ISO payment initiation, and FI correspondent payment flows, but it does not yet show the classic differences between batch, real-time, and gross-settlement payment rails. Adding classic rail examples will make the product more useful for product and architecture discussions about why a client would choose RTP, ACH, RTGS, or a separate FI correspondent arrangement.

## What Changes

- Add ACH Direct Credit batch simulation as a JSON external API with multiple entries, batch-level status, and entry-level status summaries.
- Add RTGS payment simulation as a JSON external API for both corporate treasury and FI client scenarios.
- Preserve the existing domestic RTP endpoint as the baseline real-time rail example without renaming or changing its contract.
- Preserve existing FI correspondent payment and recall/investigation flows as a separate FI correspondent arrangement, not as an RTP, ACH, or RTGS rail.
- Reuse existing gateway-like controls for authentication, scope validation, idempotency, correlation ID handling, consistent JSON errors, ownership isolation, and sensitive logging.
- Extend OpenAPI, Postman, README, developer docs, and artifact validation coverage so the rail taxonomy is understandable and executable.
- Keep cross-border payment and payment rail recommendation copilot behavior as future-facing product hooks, not runtime scope for this change.

## Capabilities

### New Capabilities

- `ach-direct-credit-batches`: ACH Direct Credit batch creation and status simulation using JSON batch envelopes with multiple entries, deterministic batch outcomes, and entry-level status summaries.
- `rtgs-payment-simulation`: RTGS payment creation and status simulation for corporate treasury and FI client scenarios, including settled, queued-for-liquidity, and rejected deterministic outcomes.

### Modified Capabilities

- `payment-authentication-and-idempotency`: Add ACH and RTGS scopes, idempotency-key requirements, owner-only status access, and correlation behavior for the new external endpoints.
- `payment-developer-support`: Add OpenAPI, Postman, README, developer documentation, scenario catalog, and artifact validation expectations for the classic rail simulation journey.

## Impact

- New external endpoints:
  - `POST /v1/ach-batches`
  - `GET /v1/ach-batches/{batchId}`
  - `POST /v1/rtgs-payments`
  - `GET /v1/rtgs-payments/{paymentId}`
- New rail-specific authorization scopes:
  - `ach-batches:create`
  - `ach-batches:read`
  - `rtgs-payments:create`
  - `rtgs-payments:read`
- New application services, domain records/statuses, repositories, simulator implementations, API DTOs, controllers, observability events, OpenAPI schemas/examples, Postman requests, and focused tests.
- No runtime changes to existing domestic RTP, FI correspondent payment, or FI recall/investigation APIs except documentation and demo journey placement.
