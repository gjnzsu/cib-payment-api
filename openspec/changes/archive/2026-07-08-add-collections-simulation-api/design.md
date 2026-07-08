## Context

The repository currently simulates push-payment and FI payment capabilities: domestic RTP/HK ISO initiation, ACH Direct Credit batches, RTGS payments, FI correspondent payments, recall/investigation flows, and deterministic payment rail recommendations. It does not yet model pull-payment collection journeys where a creditor or merchant initiates a debit against a payer account under an existing authorization.

Collections are a product capability, not a single payment scheme. US ACH Direct Debit and Hong Kong FPS/eDDA-style direct debit both support pre-authorized collection use cases, but they differ in market profile, processing style, currency expectations, and authorization vocabulary. The simulator should make that distinction visible without building a production collections platform.

## Goals / Non-Goals

**Goals:**

- Add a collection creation and status query API that fits the existing layered architecture.
- Support `US_ACH_DIRECT_DEBIT_BATCH` and `HK_FPS_DIRECT_DEBIT` simulator profiles under one collection product surface.
- Require a mandate or direct debit authorization reference for every collection request.
- Reuse existing gateway-like controls: JWT, scope checks, idempotency, correlation, ownership isolation, JSON errors, and sensitive logging.
- Keep collection records separate from existing ACH Direct Credit, domestic RTP, RTGS, and FI records.
- Keep OpenAPI, Postman, README/developer documentation, and tests aligned with executable simulator behavior.

**Non-Goals:**

- No real ACH, NACHA, HK FPS, HKICL, eDDA, clearing, settlement, account validation, balance check, fraud, sanctions, or production decisioning integration.
- No mandate setup, amendment, cancellation, activation, expiry, or verification lifecycle.
- No recurring scheduler, retry orchestration, webhook/callback delivery, or generic multi-market production collections platform.
- No change to existing `/v1/ach-batches`, `/v1/domestic-payments`, `/v1/rtgs-payments`, or `/v1/fi-payments` behavior.

## Decisions

### Use `/v1/collections` instead of `/v1/ach-debit-batches`

The API will expose `POST /v1/collections` and `GET /v1/collections/{collectionId}`. A collection request includes `collectionProfile` to select `US_ACH_DIRECT_DEBIT_BATCH` or `HK_FPS_DIRECT_DEBIT`.

This keeps the product surface aligned with the business capability: collecting funds from a payer under a pre-authorization. It also avoids making the Hong Kong profile look like an ACH variant. The main alternative was `/v1/ach-debit-batches`, which is simpler for US ACH but too narrow once HK collections are in scope.

### Model mandate reference as evidence, not lifecycle

The request will require `mandateReference` or an equivalent direct debit authorization reference. The simulator treats the reference as evidence that authorization already exists. It does not store or validate a mandate resource.

This keeps the first slice focused on collection execution and status behavior. A future change can add `/v1/mandates` or profile-specific authorization setup flows if product discovery shows that mandate lifecycle simulation is valuable.

### Use profile-specific deterministic simulator scenarios

The simulator will support profile-relevant `X-Mock-Scenario` values while sharing a common collection status model.

US ACH Direct Debit scenarios cover collected, settlement pending, partially returned, rejected authorization, rejected insufficient funds, timeout, and internal failure. HK FPS Direct Debit scenarios cover completed, pending authorization, rejected invalid authorization, rejected insufficient funds, timeout, and internal failure.

### Store collections separately

Collections will use collection-specific domain models and an in-memory collection repository. Existing ACH Direct Credit batch records remain separate because credit and debit flows have different product semantics, statuses, and examples.

### Reuse existing architectural patterns

Controllers should handle HTTP routing, headers, validation, and response mapping only. Application services should own idempotency, profile admission, simulator invocation, record persistence, and status query orchestration. Domain models should avoid HTTP, JWT, OpenAPI, and infrastructure dependencies. Infrastructure should implement the deterministic simulator and repository.

## Risks / Trade-offs

- Generic endpoint could imply production-grade multi-scheme collections support -> Mitigate through explicit simulator-only naming, profile enums, documentation boundaries, and OpenAPI descriptions.
- Mandate reference without mandate lifecycle could confuse users -> Mitigate by documenting that the MVP assumes an existing authorization and does not create or verify mandates.
- Shared collection statuses could flatten market-specific differences -> Mitigate by preserving profile, reason codes, entry summaries for ACH batch collections, and HK-specific authorization outcome reasons.
- Adding two profiles in one change increases scope -> Mitigate by keeping both profiles JSON-only, deterministic, in-memory, and limited to creation/status query.
- Sensitive collection data could leak in logs -> Mitigate by extending existing sensitive logging tests and masking account/authorization references at logging boundaries.
