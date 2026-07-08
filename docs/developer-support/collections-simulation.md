# Collections Simulation Developer Guide

This guide explains the collections simulation surface for product demos, architecture walkthroughs, and local developer testing.

The feature models pre-authorized pull-payment journeys. It supports mandate setup simulation before collection execution, while still allowing external mandate references when mandate setup is not the focus of a scenario.
The collection runtime profiles remain US ACH Direct Debit and HK FPS Direct Debit.

| Profile | Runtime endpoint | Primary teaching point | Authorization reference | Payload style |
| --- | --- | --- | --- | --- |
| `US_ACH_DEBIT_MANDATE` | `/v1/mandates` | US ACH debit authorization setup simulation | Generated or client-supplied `mandateReference` | JSON mandate envelope |
| `HK_FPS_EDDA` | `/v1/mandates` | HK FPS/eDDA-style authorization setup simulation | Generated or client-supplied `mandateReference` | JSON mandate envelope |
| `US_ACH_DIRECT_DEBIT_BATCH` | `/v1/collections` | Batch-oriented debit collection for invoice, subscription, repayment, and premium use cases | `mandateReference` | JSON collection envelope with multiple debit entries |
| `HK_FPS_DIRECT_DEBIT` | `/v1/collections` | HK FPS/eDDA-style direct debit collection where authorization already exists | `mandateReference` as eDDA reference | JSON single collection envelope |

This is not a generic production Collections API. It provides no real ACH, no NACHA, no real HK FPS/eDDA setup, no HKICL connectivity, no payer notification, no account validation, no balance check, no fraud or sanctions screening, no clearing or settlement, no recurring scheduling, no webhooks, and no production decisioning.

## Runtime API

| Operation | Scope | Idempotency | Result |
| --- | --- | --- | --- |
| `POST /v1/mandates` | `mandates:create` | Required | `202 Accepted` JSON acknowledgement |
| `GET /v1/mandates/{mandateId}` | `mandates:read` | Not required | `200 OK` JSON status response |
| `POST /v1/mandates/{mandateId}/cancel` | `mandates:cancel` | Required | `200 OK` JSON cancellation response |
| `POST /v1/collections` | `collections:create` | Required | `202 Accepted` JSON acknowledgement |
| `GET /v1/collections/{collectionId}` | `collections:read` | Not required | `200 OK` JSON status response |

Every request accepts `X-Correlation-ID`; the API generates one when absent. Status query is owner-only and must not expose another client's collection data.

Collections continue to accept external `mandateReference` values. If the reference belongs to a mandate created by `/v1/mandates` for the same authenticated client, that mandate must be `ACTIVE` before it can be used for collection simulation.

## Scenario Catalog

### Mandates

| Journey | `mandateProfile` | `X-Mock-Scenario` | Expected status/result |
| --- | --- | --- | --- |
| Mandate active | `US_ACH_DEBIT_MANDATE` or `HK_FPS_EDDA` | `mandate_active` | `ACTIVE` |
| Mandate pending payer authorization | either profile | `mandate_pending_authorization` | `PENDING_AUTHORIZATION` |
| Mandate rejected by payer | either profile | `mandate_rejected_by_payer` | `REJECTED` |
| Mandate expired before activation | either profile | `mandate_expired` | `EXPIRED` |
| Timeout | either profile | `mandate_timeout` | `TIMEOUT` |
| Internal failure | either profile | `mandate_internal_failure` | `FAILED` |
| Cancellation | active or pending mandate | no mock scenario | `CANCELLED` |

### Collections

| Journey | `collectionProfile` | `X-Mock-Scenario` | Expected status/result |
| --- | --- | --- | --- |
| US ACH debit collected | `US_ACH_DIRECT_DEBIT_BATCH` | `us_ach_debit_collected` | `COLLECTED`, entries `COLLECTED` |
| US ACH debit pending | `US_ACH_DIRECT_DEBIT_BATCH` | `us_ach_debit_settlement_pending` | `SETTLEMENT_PENDING`, entries pending settlement |
| US ACH debit partial return | `US_ACH_DIRECT_DEBIT_BATCH` | `us_ach_debit_partially_returned` | `PARTIALLY_RETURNED`, at least one entry `RETURNED` |
| US ACH debit authorization rejected | `US_ACH_DIRECT_DEBIT_BATCH` | `us_ach_debit_rejected_authorization` | `REJECTED` |
| US ACH debit insufficient funds | `US_ACH_DIRECT_DEBIT_BATCH` | `us_ach_debit_rejected_insufficient_funds` | `REJECTED` |
| HK FPS collection completed | `HK_FPS_DIRECT_DEBIT` | `hk_fps_collection_completed` | `COMPLETED` |
| HK FPS authorization pending | `HK_FPS_DIRECT_DEBIT` | `hk_fps_collection_pending_authorization` | `PENDING_AUTHORIZATION` |
| HK FPS invalid authorization | `HK_FPS_DIRECT_DEBIT` | `hk_fps_collection_rejected_invalid_authorization` | `REJECTED` |
| HK FPS insufficient funds | `HK_FPS_DIRECT_DEBIT` | `hk_fps_collection_rejected_insufficient_funds` | `REJECTED` |
| Timeout | either profile | `collection_timeout` | `TIMEOUT` |
| Internal failure | either profile | `collection_internal_failure` | `FAILED` |

## Common Setup Mistakes

- Using `ach-batches:create` for collections. Collections require `collections:create`.
- Using `collections:create` for mandates. Mandates require `mandates:create`; cancellation requires `mandates:cancel`.
- Treating `mandateReference` inside `/v1/collections` as a mandate creation request. Create the mandate first through `/v1/mandates`, or pass an external reference when mandate setup is not the scenario focus.
- Trying to collect with a local mandate that is not `ACTIVE`. Pending, rejected, expired, cancelled, timed out, and failed local mandates are rejected by collection creation.
- Expecting NACHA files for US ACH Direct Debit. The runtime accepts JSON only.
- Expecting real HK FPS/eDDA setup or validation. HK behavior is deterministic simulator behavior only.
- Reusing one `Idempotency-Key` across different collection bodies or mock scenarios. That should produce `409 Conflict`.
- Adding `Idempotency-Key` to status query. Status query is read-only and does not need idempotency.

## Local Token

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a mandates:create,mandates:read,mandates:cancel,collections:create,collections:read 3600"
```
