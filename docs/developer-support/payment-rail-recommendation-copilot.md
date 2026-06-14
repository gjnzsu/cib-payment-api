# Payment Rail Recommendation Copilot

This guide describes the deterministic simulator endpoint for future payment rail recommendation copilot work.

## Runtime API

`POST /v1/payment-rail-recommendations` accepts one JSON payment intent and returns rail and arrangement guidance. The endpoint requires JWT scope `payment-rail-recommendations:create`, accepts `X-Correlation-ID`, and does not require `Idempotency-Key`.

The request is rail-neutral. It uses intent fields such as `clientSegment`, `paymentCount`, `amountSummary.currency`, `amountSummary.totalAmount`, optional `amountSummary.maxSingleAmount`, `debtorCountry`, `creditorCountry`, `urgency`, `requiresFinality`, `batchPreferred`, `costSensitivity`, `fiToFi`, optional `arrangementPreference`, and optional `debtorAccountProfile.count`. It does not require debtor account number, creditor account number, settlement account number, receiver account number, or correspondent account reference.

## Rail And Arrangement Output

The response uses rail and arrangement together:

| Rail | Arrangement | Typical intent |
| --- | --- | --- |
| `RTP` | `DOMESTIC_REAL_TIME_CLEARING` | Immediate low-value domestic USD single payment |
| `ACH` | `BATCH_CLEARING_NET_SETTLEMENT` | Multiple-payment, batch-preferred, or cost-sensitive domestic USD intent |
| `RTGS` | `DOMESTIC_INTERBANK_GROSS_SETTLEMENT` | High-value or finality-required domestic USD single payment |
| `FI_CORRESPONDENT` | `CORRESPONDENT_ACCOUNT_PATH` | FI client intent that explicitly prefers correspondent account path |

Supported recommendations return `recommendationStatus=RECOMMENDED`, `confidenceLevel`, matched factors, warnings, alternatives, tradeoffs, `intentFit`, `intentFitReason`, and next API guidance. The next API guidance identifies candidate endpoint, method, scopes, headers, and payload format; it does not generate a complete payment creation payload.

Valid but unsupported intents return `recommendationStatus=UNSUPPORTED`. Cross-border intent returns `CROSS_BORDER_NOT_SUPPORTED`; non-USD intent returns `NON_USD_NOT_SUPPORTED`.

## Rule Precedence

The deterministic simulator rule order is:

1. Unsupported guardrails for non-domestic or non-USD valid intents.
2. FI correspondent preference for `clientSegment=FI` and `arrangementPreference=CORRESPONDENT_ACCOUNT_PATH`.
3. ACH for `paymentCount > 1`, `batchPreferred=true`, or high cost sensitivity.
4. RTGS for finality-required or high-value single payments.
5. RTP for immediate low-value domestic USD single payments.
6. Default domestic single-payment recommendation.

The simulator high-value threshold is `100000 USD`. This is a deterministic simulator threshold, not a real scheme limit, bank policy, or production decision threshold.

When a batch intent also contains finality or high-value signals, ACH remains the primary recommendation and the response includes RTGS warning or alternative guidance. When `paymentCount > 1` and `maxSingleAmount` is omitted, the response includes `MAX_SINGLE_AMOUNT_NOT_PROVIDED`.

## Postman

Use the `Payment Rail Recommendation Copilot` folder:

- `Recommend Rail - RTP Immediate Low Value`
- `Recommend Rail - ACH Vendor Batch`
- `Recommend Rail - ACH Missing Max Single Amount Warning`
- `Recommend Rail - ACH Finality Conflict`
- `Recommend Rail - RTGS Corporate Finality`
- `Recommend Rail - RTGS FI Gross Settlement`
- `Recommend Rail - FI Correspondent Arrangement`
- `Recommend Rail - Cross Border Unsupported`
- `Recommend Rail - Non USD Unsupported`
- `Recommend Rail - Scope Failure`

Set `recommendationJwtToken` to a token containing `payment-rail-recommendations:create`. Set `recommendationScopeFailureToken` to a token without that scope for the scope failure request.

Generate a local token:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payment-rail-recommendations:create 3600"
```

## Boundaries

This MVP provides deterministic simulator guidance only. It has no real AI, LLM, payment execution, recommendation persistence, cross-border support, FX, sanctions, fraud, pricing, liquidity, compliance decisions, or real rail availability decisions.
