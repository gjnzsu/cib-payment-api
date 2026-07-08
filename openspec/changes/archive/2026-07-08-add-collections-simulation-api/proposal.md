## Why

Corporate clients often need to collect money from customers or counterparties through pre-authorized pull-payment flows, not only push payments such as RTP, ACH Direct Credit, RTGS, or FI correspondent transfers. This change adds a collections simulation surface so the suite can demonstrate both US ACH Direct Debit and Hong Kong FPS/eDDA-style direct debit collection scenarios without claiming real scheme connectivity.

## What Changes

- Add a `POST /v1/collections` API to submit one collection request using a supported simulator profile.
- Add a `GET /v1/collections/{collectionId}` API to query collection status.
- Support two initial collection profiles:
  - `US_ACH_DIRECT_DEBIT_BATCH` for batch-oriented ACH debit collection simulation.
  - `HK_FPS_DIRECT_DEBIT` for HK FPS/eDDA-style direct debit collection simulation when a direct debit authorization already exists.
- Require JWT Bearer authentication, `collections:create` scope for creation, and `collections:read` scope for status query.
- Require `Idempotency-Key` for collection creation and apply client-scoped replay/conflict behavior.
- Require a mandate or direct debit authorization reference in collection requests, but do not implement mandate creation, amendment, cancellation, activation, or expiry lifecycle.
- Return deterministic simulator outcomes for collected/completed, pending, partially returned, rejected authorization, rejected insufficient funds, timeout, and internal failure scenarios.
- Keep collection records separate from existing domestic RTP, ACH Direct Credit, RTGS, and FI payment records.
- Update OpenAPI, Postman, README/developer documentation, and tests to explain the collection profiles and simulator-only boundaries.

## Capabilities

### New Capabilities

- `collections-simulation`: Collections API simulator for pre-authorized pull-payment scenarios, including US ACH Direct Debit batch and HK FPS/eDDA-style direct debit profiles.

### Modified Capabilities

- `payment-authentication-and-idempotency`: Add collections-specific scopes, idempotency-key requirements, owner-only status access, correlation propagation, and sensitive logging rules.
- `payment-developer-support`: Add OpenAPI, Postman, README, developer documentation, simulator scenarios, and artifact validation expectations for collections simulation.

## Impact

- API layer: new collection creation and status endpoints, JSON request/response DTOs, validation, scope checks, and error mapping.
- Application layer: collection orchestration, idempotency handling, request fingerprinting, profile-specific simulator outcome handling, ownership checks, and status query.
- Domain layer: collection ID, collection record, collection profile, collection status, collection entry status, mandate authorization reference, and reason models.
- Infrastructure layer: in-memory collection repository, deterministic collection simulator, observability events, and sensitive value masking.
- Developer support: OpenAPI contract updates, Postman scenarios and environment variables, README updates, and focused contract/integration tests.
- No real ACH, NACHA, HK FPS, HKICL, eDDA setup, account validation, balance check, fraud, sanctions, settlement, clearing, webhook, recurring scheduler, or production decisioning integration is added.
