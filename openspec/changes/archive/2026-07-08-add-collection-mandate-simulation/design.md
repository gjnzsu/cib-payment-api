## Context

The current Collections API models pre-authorized pull payments through `/v1/collections`. It requires `mandateReference` but intentionally treats it as external evidence instead of creating or validating mandate resources. That keeps collection execution focused, but it leaves product demos without a first-class payer authorization step.

The mandate simulation should fit the existing layered architecture: HTTP controllers handle routing and response mapping; application services own orchestration, idempotency, and access checks; domain models hold mandate status and lifecycle rules; infrastructure provides in-memory persistence and deterministic simulator behavior.

## Goals / Non-Goals

**Goals:**

- Add mandate creation, query, and cancellation simulation for direct debit and eDDA-style authorization journeys.
- Support deterministic local scenarios for active, pending, rejected, expired, timeout, and internal failure outcomes.
- Generate API-owned `mandateId` values and support a business `mandateReference`.
- Reuse JWT authentication, scope validation, idempotency, correlation ID propagation, JSON errors, ownership isolation, and sensitive logging patterns.
- Let Collections use a system-created active mandate reference.
- Keep Collections compatible with external mandate references that were not created by this API.

**Non-Goals:**

- No real mandate setup, account validation, payer notification, bank authorization, HK FPS/eDDA connectivity, ACH authorization storage, fraud, sanctions, or production decisioning.
- No recurring schedule engine, retry workflow, webhook/callback delivery, or mandate amendment flow.
- No hard requirement that every collection reference must be created by `/v1/mandates`.

## Decisions

### Expose `/v1/mandates` as a peer product surface

The API will add `POST /v1/mandates`, `GET /v1/mandates/{mandateId}`, and `POST /v1/mandates/{mandateId}/cancel`.

This keeps authorization setup separate from collection execution. The main alternative was adding mandate creation behavior inside `POST /v1/collections`, but that would mix two business actions and make idempotency, status, and ownership harder to explain.

### Keep external mandate references compatible

Collections will not require a local mandate record. If the submitted `mandateReference` does not match a system-created mandate reference for the authenticated client, the API will treat it as external evidence and continue the existing collection simulation path.

If the reference matches a system-created mandate for the authenticated client, Collections will require that mandate to be `ACTIVE`. This gives the new journey meaningful behavior without breaking current examples.

### Use a simple mandate status model

Mandate statuses will be `PENDING_AUTHORIZATION`, `ACTIVE`, `REJECTED`, `CANCELLED`, `EXPIRED`, `TIMEOUT`, and `FAILED`.

`ACTIVE`, `REJECTED`, `EXPIRED`, `TIMEOUT`, and `FAILED` are terminal for the create simulation. `CANCELLED` is terminal after cancellation. Cancellation is allowed from `ACTIVE` and `PENDING_AUTHORIZATION` only.

### Store mandates separately

Mandates will use a mandate-specific repository and records. They will not be stored in collection records or existing payment status repositories.

This follows the existing separation between domestic payments, ACH credit batches, RTGS, FI payments, and collections.

### Reuse request fingerprint idempotency

Mandate creation and cancellation will use existing idempotency infrastructure with mandate-specific fingerprint payloads and stored response bodies. Replays with the same authenticated client, idempotency key, and body return the original response; body mismatch returns `409 Conflict`.

## Risks / Trade-offs

- Users may assume local mandate validation is mandatory for all collections -> Mitigation: document that external `mandateReference` values remain supported for simulator journeys.
- A generated `mandateReference` could collide with client-supplied references -> Mitigation: lookup by authenticated client and exact reference; generated references use a deterministic prefix and UUID-derived suffix.
- Cancellation adds a second state-changing endpoint -> Mitigation: keep it narrow, idempotent, and limited to status transition simulation.
- Shared mandate API could imply production mandate management -> Mitigation: OpenAPI and developer docs explicitly frame it as deterministic simulation only.
