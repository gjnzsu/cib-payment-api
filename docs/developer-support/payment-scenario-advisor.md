# Payment Scenario Advisor Developer Guide

The Payment Scenario Advisor is a minimal business-facing loop that connects curated payment use cases to deterministic rail recommendation guidance and existing simulator plans.

## Runtime Surface

- UI: `/payment-scenario-advisor/`
- Catalog API: `GET /v1/payment-scenario-advisor/scenarios`
- Detail API: `GET /v1/payment-scenario-advisor/scenarios/{scenarioId}`

Advisor APIs accept `X-Correlation-ID` and generate one when absent. They do not require bearer authentication or `Idempotency-Key` because they create no payment, recommendation, simulation, report, or idempotency record.

## MVP Scenarios

| Scenario ID | Business scenario | Recommended path | Simulator plan |
| --- | --- | --- | --- |
| `urgent-supplier-payment` | Immediate low-value supplier invoice | RTP / `DOMESTIC_REAL_TIME_CLEARING` | `POST /v1/domestic-payments`, mock scenario `success`, expected `ACSC` |
| `vendor-batch-payment` | Multiple non-urgent vendor payments | ACH / `BATCH_CLEARING_NET_SETTLEMENT` | `POST /v1/ach-batches`, mock scenario `ach_direct_credit_settled`, expected `SETTLED` |
| `high-value-treasury-transfer` | High-value domestic treasury transfer | RTGS / `DOMESTIC_INTERBANK_GROSS_SETTLEMENT` | `POST /v1/rtgs-payments`, mock scenario `rtgs_settled`, expected `SETTLED` |
| `fi-correspondent-settlement` | FI-to-FI correspondent account path | FI correspondent / `CORRESPONDENT_ACCOUNT_PATH` | `POST /v1/fi-payments`, mock scenario `fi_payment_accepted`, expected `ACCEPTED` |

## Manual Testing Loop

1. Start the API locally.
2. Open `/payment-scenario-advisor/`.
3. Select each MVP scenario.
4. Confirm the UI renders recommendation, simulator plan, and feedback result sections.
5. Use the simulator plan to run the corresponding existing API flow with the required scopes and idempotency behavior.
6. Compare the simulator status with the advisor expected outcome.

## Boundaries

This feature is simulator-only guidance. It does not execute simulator payments, create production routing decisions, persist advisor sessions, or perform real AI, LLM, pricing, liquidity, sanctions, fraud, FX, compliance, settlement, or rail availability decisions.

Mock scenario metadata is scoped to simulator guidance only and must not be treated as production payment semantics.
