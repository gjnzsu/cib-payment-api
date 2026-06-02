# Classic Payment Rail Simulation Developer Guide

This guide explains the classic rail simulation surface for product demos, architecture walkthroughs, and local developer testing.

The feature compares RTP, ACH Direct Credit, RTGS, and FI correspondent payment arrangement behavior in one local simulation suite. It does not add cross-border runtime, FX conversion, NACHA processing, ISO runtime for ACH/RTGS, Direct Debit runtime, real ACH or RTGS connectivity, central bank ledger behavior, liquidity management, or automatic RTGS-to-FI correspondent orchestration.

## Rail Comparison

| Capability | RTP | ACH Direct Credit | RTGS | FI correspondent arrangement |
| --- | --- | --- | --- | --- |
| Runtime endpoint | `/v1/domestic-payments` | `/v1/ach-batches` | `/v1/rtgs-payments` | `/v1/fi-payments` |
| Primary teaching point | Domestic real-time payment initiation and status | Batch model with multiple payment entries and clear clearing/settlement distinction | Gross settlement style for high-priority corporate and FI transfers | Correspondent account path using nostro, vostro, and loro context |
| Participant shape | Corporate or API client initiating one domestic payment | Originator submits a JSON Direct Credit batch with multiple entries | `CORPORATE` or `FI` client segment | FI client using correspondent-account-path semantics |
| Payload style | ISO 20022 `pain.001.001.09` request and `pain.002.001.10` status report | JSON batch envelope only | JSON payment instruction only | ISO 20022 `pacs.009.001.08` plus recall/investigation XML |
| Authorization scopes | `payments:create`, `payments:read` | `ach-batches:create`, `ach-batches:read` | `rtgs-payments:create`, `rtgs-payments:read` | `fi-payments:create`, `fi-payments:read`, `fi-payments:investigate` |
| Idempotency | Required on create | Required on create | Required on create | Required on create and recall |
| Status model | `ACSC`, `RJCT`, `PDNG` in ISO status reports | Batch statuses plus entry-level status summary | Payment status plus `settlementFinality` | JSON FI status plus correspondent settlement context |
| What it is not | Not real FPS/HKICL connectivity | no NACHA, no ISO runtime for ACH/RTGS, no Direct Debit runtime | No central bank ledger, no real liquidity queue | Not an RTP, ACH, or RTGS rail |

## Scenario Catalog

| Journey | Endpoint | Scope | `X-Mock-Scenario` | Expected status/result |
| --- | --- | --- | --- | --- |
| RTP baseline create | `POST /v1/domestic-payments` | `payments:create` | `success` | `200 OK`, ISO status `ACSC` |
| RTP baseline status | `GET /v1/domestic-payments/{paymentId}` | `payments:read` | none | `200 OK`, latest ISO status report |
| ACH Direct Credit settled | `POST /v1/ach-batches` | `ach-batches:create` | `ach_direct_credit_settled` | `202 Accepted`, batch `SETTLED`, entries `SETTLED` |
| ACH Direct Credit partially returned | `POST /v1/ach-batches` | `ach-batches:create` | `ach_direct_credit_partially_returned` | `202 Accepted`, batch `PARTIALLY_RETURNED`, at least one entry `RETURNED` |
| ACH Direct Credit rejected | `POST /v1/ach-batches` | `ach-batches:create` | `ach_direct_credit_rejected` | `202 Accepted`, batch `REJECTED`, entries `REJECTED` |
| ACH batch status | `GET /v1/ach-batches/{batchId}` | `ach-batches:read` | none | `200 OK`, batch status and entry-level status summary |
| Corporate RTGS settled | `POST /v1/rtgs-payments` | `rtgs-payments:create` | `rtgs_settled` | `202 Accepted`, status `SETTLED`, `settlementFinality=true` |
| FI RTGS settled | `POST /v1/rtgs-payments` | `rtgs-payments:create` | `rtgs_settled` | `202 Accepted`, FI segment status `SETTLED`, `settlementFinality=true` |
| FI RTGS queued | `POST /v1/rtgs-payments` | `rtgs-payments:create` | `rtgs_queued_for_liquidity` | `202 Accepted`, status `QUEUED_FOR_LIQUIDITY`, `settlementFinality=false` |
| RTGS rejected | `POST /v1/rtgs-payments` | `rtgs-payments:create` | `rtgs_rejected` | `202 Accepted`, status `REJECTED`, `settlementFinality=false` |
| RTGS status | `GET /v1/rtgs-payments/{paymentId}` | `rtgs-payments:read` | none | `200 OK`, status and settlement-finality view |
| FI correspondent comparison | `POST /v1/fi-payments` | `fi-payments:create` | `fi_payment_accepted` | `202 Accepted`, JSON FI acknowledgement with `NOSTRO`, `VOSTRO`, or `LORO` context |

## Postman

Import:

- `postman/domestic-rtp-payment-api.postman_collection.json`
- `postman/domestic-rtp-payment-api.local.postman_environment.json`

Use the `Classic Payment Rail Simulation` folder for the comparison journey. It contains:

- RTP baseline create and status requests.
- ACH Direct Credit settled, partially returned, rejected, and status requests.
- Corporate RTGS settled request.
- FI RTGS settled, queued for liquidity, rejected, and status requests.
- FI Correspondent Comparison as a separate arrangement, not an RTP, ACH, or RTGS rail.

Set these token variables before running the journey:

- `jwtToken` and `readOnlyJwtToken` for RTP baseline.
- `achJwtToken` and `achReadOnlyJwtToken` for ACH.
- `rtgsJwtToken` and `rtgsReadOnlyJwtToken` for RTGS.
- `fiJwtToken` and `fiReadOnlyJwtToken` for FI correspondent comparison.

Use per-scenario idempotency keys. Do not reuse one key across different create request bodies:

- `achSettledIdempotencyKey`
- `achPartiallyReturnedIdempotencyKey`
- `achRejectedIdempotencyKey`
- `rtgsCorporateSettledIdempotencyKey`
- `rtgsFiSettledIdempotencyKey`
- `rtgsFiQueuedIdempotencyKey`
- `rtgsRejectedIdempotencyKey`

Status requests do not use `Idempotency-Key`. The collection captures `achBatchId` and `rtgsPaymentId` from create responses for later status lookups.

## Common Setup Mistakes

- Using a token with `payments:create` for ACH or RTGS. ACH requires `ach-batches:create`; RTGS requires `rtgs-payments:create`.
- Reusing the same idempotency key across different ACH or RTGS scenario bodies. That should produce `409 Conflict`.
- Adding `Idempotency-Key` to status queries. Status queries should be read-only and do not need idempotency.
- Expecting NACHA input for ACH. The runtime accepts JSON Direct Credit batches only.
- Expecting ISO 20022 runtime payloads for ACH or RTGS. There is no ISO runtime for ACH/RTGS in this feature.
- Expecting ACH Direct Debit behavior. There is no Direct Debit runtime.
- Treating FI correspondent payment as a rail selected by RTGS. FI correspondent payment is a separate arrangement and there is no automatic RTGS-to-FI correspondent orchestration.
- Expecting cross-border behavior, FX conversion, sanctions screening, real liquidity checks, settlement connectivity, or real ledger movement. There is no cross-border runtime or production payment connectivity.

## Future Copilot Hook

The classic rail comparison creates a future hook for a payment rail recommendation copilot. A future AI feature could use the same rail taxonomy and scenario metadata to explain why a payment might fit RTP, ACH Direct Credit, RTGS, or an FI correspondent arrangement.

That copilot is future-facing only. This service does not make runtime recommendations, does not execute cross-border or FX behavior, and does not automatically route from RTGS into FI correspondent payment processing.
