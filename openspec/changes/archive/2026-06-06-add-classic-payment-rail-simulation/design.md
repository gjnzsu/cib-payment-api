## Context

The current service demonstrates domestic RTP-style payment behavior, HK ISO 20022 initiation/status reporting, and FI correspondent payment workflows. The new change adds classic domestic rail examples so the simulator can compare real-time single-payment processing, ACH-style batch/deferred settlement, and RTGS-style high-value gross settlement without turning into a production rail integration.

The repository already uses layered architecture, application ports, domain records, infrastructure simulators, in-memory repositories, OpenAPI, Postman artifacts, deterministic `X-Mock-Scenario` controls, JWT scopes, idempotency, correlation IDs, and sensitive logging protections. The new ACH and RTGS capabilities should follow those patterns instead of introducing a parallel architecture.

## Goals / Non-Goals

**Goals:**

- Add ACH Direct Credit batch simulation with JSON batch envelopes, multiple entries, batch-level status, and entry-level status summaries.
- Add RTGS payment simulation with JSON requests for both corporate treasury and FI client scenarios.
- Keep existing domestic RTP as the baseline real-time rail example without endpoint renaming.
- Keep existing FI correspondent payment as a separate FI arrangement and comparison journey, not an RTGS processing dependency.
- Reuse existing authentication, authorization, idempotency, correlation, error, observability, masking, and in-memory persistence patterns.
- Update OpenAPI, Postman, README, developer docs, and artifact validation tests so the rail taxonomy is executable and understandable.

**Non-Goals:**

- No NACHA file parsing or generation.
- No ACH Direct Debit runtime behavior.
- No ISO 20022 parsing or rendering for ACH or RTGS in this change.
- No real ODFI/RDFI model, banking calendar, holiday engine, retry, reversal, NOC, prenote, or full ACH return-code universe.
- No real central bank ledger, liquidity reservation, RTGS queue optimization, cancellation, amendment, or scheme connectivity.
- No cross-border runtime behavior.
- No AI rail recommendation endpoint, LLM integration, scoring engine, or orchestration engine.
- No automatic handoff from RTGS to existing FI correspondent payment runtime.

## Decisions

### Use hybrid rail-specific endpoints

Use:

- `POST /v1/ach-batches`
- `GET /v1/ach-batches/{batchId}`
- `POST /v1/rtgs-payments`
- `GET /v1/rtgs-payments/{paymentId}`

Keep the existing `POST /v1/domestic-payments` and `GET /v1/domestic-payments/{paymentId}` endpoints as the RTP baseline.

Alternative considered: a generic `/v1/payment-rails/{rail}/payments` endpoint. That would make the taxonomy explicit but would hide the fact that ACH is naturally a batch resource while RTP and RTGS are single-payment resources. The hybrid shape keeps each resource honest and avoids breaking existing RTP clients.

### Model ACH as JSON Direct Credit batches

ACH uses a JSON batch envelope with multiple credit entries. The simulator records batch-level lifecycle status and entry-level status summaries. Direct Debit remains future scope because it introduces authorization, debit return timing, recurring collection, and dispute semantics that are not needed to demonstrate batch clearing and deferred settlement.

Alternative considered: NACHA file simulation. That would be useful for a US ACH file-format feature, but it would move this change away from rail behavior and into fixed-width file parsing.

The MVP ACH simulator profile is USD-only. The request should carry `batchReference`, `originatorName`, `effectiveEntryDate`, `settlementAccount`, and `entries`. Each entry should carry `entryReference`, `receiverName`, `receiverAccount`, `amount`, and `purpose`. The service should derive entry count and total amount from accepted entries rather than trusting client-supplied totals.

### Model RTGS as JSON payments for corporate and FI clients

RTGS uses JSON for both `CORPORATE` and `FI` client segments. Corporate scenarios use debtor/creditor payment fields. FI scenarios use FI-oriented fields such as instructing and instructed agent BICs. Both map into the same RTGS lifecycle: settled, queued for liquidity, or rejected.

Alternative considered: accepting `pacs.009` for FI RTGS. That would connect to the existing FI ISO investment, but it risks confusing RTGS rail behavior with the existing FI correspondent arrangement and expands the current feature beyond classic rail comparison.

The MVP RTGS simulator profile is USD-only and does not enforce a minimum high-value threshold. Corporate RTGS requests should carry `paymentReference`, `clientSegment`, debtor and creditor account references, `amount`, `requestedSettlementDate`, `settlementPriority`, and `purpose`. FI RTGS requests should carry `paymentReference`, `clientSegment`, `instructingAgentBic`, `instructedAgentBic`, `amount`, `requestedSettlementDate`, `settlementPriority`, and `purpose`.

### Keep FI correspondent payment separate

Existing FI correspondent payment remains a separate capability based on correspondent account context and `nostro`/`vostro`/`loro` semantics. Documentation and Postman can compare FI RTGS with FI correspondent payment, but RTGS does not invoke or create FI correspondent payments.

This preserves the taxonomy:

- Rail: RTP, ACH, RTGS
- Participant: corporate, FI
- Arrangement/path: direct scheme, central bank gross settlement, correspondent account path
- Geography: domestic now, cross-border later

### Reuse established control patterns

ACH and RTGS creation require JWT Bearer authentication, rail-specific scopes, `Idempotency-Key`, correlation IDs, owner-only status queries, consistent JSON errors, and masked sensitive logging. New status repositories should stay separate from existing RTP and FI records while reusing the current idempotency pattern with operation/resource distinction.

### Treat simulator scenarios as local/test-only controls

ACH and RTGS use rail-specific `X-Mock-Scenario` values. ACH supports accepted, settlement pending, settled, partially returned, and rejected Direct Credit outcomes. RTGS supports settled, queued-for-liquidity, and rejected outcomes. The header remains simulator-only and must be documented as non-production behavior in OpenAPI, Postman, and developer support docs.

## Risks / Trade-offs

- ACH JSON may look less realistic than NACHA file submission -> Document that this feature models rail behavior, not ACH file-format processing.
- RTGS JSON may look less FI-native than ISO 20022 -> Preserve FI-oriented fields and document ISO mapping as future scope.
- FI RTGS and FI correspondent flows may be confused -> Keep separate endpoints, separate runtime records, and explicit docs comparing rail versus correspondent arrangement.
- Scenario names may drift across code, docs, OpenAPI, and Postman -> Add artifact validation tests and trace expected results to simulator mappings.
- Adding separate repositories and DTOs increases code surface -> Follow existing layered patterns and keep ACH/RTGS models focused on the MVP lifecycle.
- In-memory persistence remains unsuitable for multi-replica production deployment -> Keep README/GKE caveats aligned with existing in-memory MVP guidance.
