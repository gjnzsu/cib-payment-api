# Postman Local Testing

This guide helps a developer run the Domestic RTP Payment Service API locally and exercise the ISO-native HK payment simulation flows from Postman.

## Run the API

Start the Spring Boot service:

```powershell
mvn spring-boot:run
```

The local base URL is:

```text
http://localhost:8080
```

Health and documentation endpoints:

```text
GET /actuator/health
GET /swagger-ui/index.html
GET /openapi/domestic-payment-api.yaml
```

## Import Postman Files

Import these files into the Postman desktop client:

```text
postman/domestic-rtp-payment-api.postman_collection.json
postman/domestic-rtp-payment-api.local.postman_environment.json
```

Select the `Domestic RTP Payment API - Local` environment before sending requests.

## Configure Variables

Set these environment variables before running secured API requests:

- `baseUrl`: local API base URL, normally `http://localhost:8080`.
- `jwtToken`: JWT bearer token with `payments:create payments:read`.
- `readOnlyJwtToken`: JWT bearer token with `payments:read` only, used for authorization failure testing.
- `correlationId`: request tracing value sent through `X-Correlation-ID`.
- `idempotencyKey`: client idempotency key for normal creation calls. The collection generates one when empty.
- `conflictIdempotencyKey`: fixed key used when exercising idempotency conflict behavior.
- `paymentId`: captured from a successful creation response and used by status query.
- `mockScenario`: local/test-only downstream scenario selector.
- `fiJwtToken`: JWT bearer token with `fi-payments:create fi-payments:read fi-payments:investigate`.
- `fiReadOnlyJwtToken`: JWT bearer token with `fi-payments:read` only, used for FI scope failure testing.
- `fiInvestigateToken`: JWT bearer token with `fi-payments:investigate`, used for recall/investigation requests.
- `fiIdempotencyKey`: idempotency key for normal FI payment creation calls.
- `fiRecallIdempotencyKey`: idempotency key for FI `camt.056` recall/investigation calls.
- `fiConflictIdempotencyKey`: fixed key used for FI idempotency conflict testing.
- `fiPaymentId`: captured from a successful FI payment acknowledgement and used by FI status and recall requests.
- `fiMockScenario`: local/test-only FI payment simulator scenario selector, normally `fi_payment_accepted`.
- `fiRecallScenario`: local/test-only FI recall/investigation simulator selector, normally `recall_accepted`.
- `recommendationJwtToken`: JWT bearer token with `payment-rail-recommendations:create`.
- `recommendationScopeFailureToken`: JWT bearer token without `payment-rail-recommendations:create`, used by `Recommend Rail - Scope Failure`.

JWTs must match the API's configured issuer, audience, signature validation, and required claims. For local testing, generate Postman-compatible tokens with the local-only helper:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:create,payments:read 3600"
```

For the `readOnlyJwtToken` variable used by the authorization failure request:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:read 3600"
```

The helper prints the JWT to standard output. Paste the first token into `jwtToken` and the second token into `readOnlyJwtToken`.

For FI correspondent payment flows, generate local tokens with FI-specific scopes:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=fi-client-a fi-payments:create,fi-payments:read,fi-payments:investigate 3600"
```

For the FI scope failure request, generate a read-only token:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=fi-client-a fi-payments:read 3600"
```

For recall/investigation-only testing, generate an investigation token:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=fi-client-a fi-payments:investigate 3600"
```

Paste these values into `fiJwtToken`, `fiReadOnlyJwtToken`, and `fiInvestigateToken` respectively. FI tokens still use the local `domestic-payment-api` audience; the scopes distinguish FI create, FI read, and FI investigation entitlements.

For payment rail recommendation scenarios, generate a recommendation token:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payment-rail-recommendations:create 3600"
```

For `Recommend Rail - Scope Failure`, generate a token without the recommendation scope:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:create 3600"
```

Paste these values into `recommendationJwtToken` and `recommendationScopeFailureToken`.

Generated local tokens use:

- `iss`: `bank-auth-server`
- `aud`: `domestic-payment-api`
- `sub`: a non-empty client identifier
- `scope`: `payments:create payments:read` for normal flows
- `exp`: a future expiry time

The helper and default runtime decoder share local MVP key material. This is local development behavior for Postman and contract testing, not production key management.

## ISO-Native Payment Flow

`POST /v1/domestic-payments` accepts supported `pain.001.001.09` XML. A successfully admitted request returns HTTP `200` with a `pain.002.001.10` XML status report:

- `ACSC`: settled in the simulator.
- `RJCT`: rejected by the simulator or business/scheme behavior.
- `PDNG`: still processing, timed out, or operationally pending.

Malformed XML, unsupported message versions, missing mandatory fields, unsupported content type, or missing `Idempotency-Key` return JSON `400` errors and do not create a payment record. HKD-only profile failures, such as non-HKD currency, return JSON `422` errors and do not generate `pain.002`.

This is simulator-only behavior with no real HKICL/FPS connectivity. The Payment Engine maps admitted `pain.001` candidates to an internal interbank transfer representation; pacs.008 is internal-only and is not accepted or returned by the external API.

## Mock Scenarios

`X-Mock-Scenario` is local/test-only behavior. It is not part of production payment business semantics.

Allowed values:

- `success`: simulator returns `ACSC`.
- `rejection`: simulator returns `RJCT`.
- `suspicious_proxy_or_account`: simulator returns `RJCT` for a suspicious beneficiary account or FPS proxy scenario.
- `pending`: simulator returns `PDNG` for normal processing.
- `timeout`: simulator returns `PDNG` with timeout/operational intervention reason details.
- `internal_failure`: simulator returns `RJCT` with internal failure reason details.

The payment creation endpoint returns `200 OK` with `pain.002.001.10` after request processing. Run `Get Payment Status` after creation to retrieve the latest engine-owned `pain.002` report.

When testing the GKE MVP deployment, run a single API replica while the service uses in-memory payment status storage. With multiple replicas, a create request can be handled by one pod and a status request by another pod, causing `PAYMENT_NOT_FOUND` for a valid payment ID. If the live deployment image was set separately from the placeholder manifest image, scale the existing deployment instead of reapplying the manifest:

```powershell
kubectl scale deployment domestic-rtp-payment-api --replicas=1
```

Durable shared persistence is the future production path for multi-replica deployments.

The dedicated mock scenario requests assert ISO status values in the returned XML. Some requests also use `pm.sendRequest` for lightweight local helper checks.

## FI Correspondent Payment Flow

The FI Postman folder exercises the simulator-only correspondent payment API:

- `POST /v1/fi-payments` accepts supported `application/pacs.009+xml` XML and returns a JSON acknowledgement.
- `GET /v1/fi-payments/{paymentId}` returns JSON status and does not require `Idempotency-Key`.
- `POST /v1/fi-payments/{paymentId}/recall-requests` accepts supported `application/camt.056+xml` XML and returns `application/camt.029+xml`.

The supported FI payment profile is USD-only. The simulator derives correspondent account context from the route profile and returns `NOSTRO`, `VOSTRO`, or `LORO` with a masked simulated account reference. These account roles are routing and investigation context only: there is no real ledger, no real settlement, no balance check, no debit or credit posting, no reconciliation, and no SWIFT or CBPR+ connectivity.

Allowed FI payment `X-Mock-Scenario` values:

- `fi_payment_accepted`: simulator records `SETTLED`.
- `fi_payment_rejected_unsupported_correspondent`: simulator records `REJECTED`.
- `fi_payment_pending_correspondent_review`: simulator records `PROCESSING`.

Allowed FI recall/investigation `X-Mock-Scenario` values:

- `recall_accepted`: simulator returns accepted `camt.029`.
- `recall_rejected`: simulator returns rejected `camt.029`.
- `investigation_pending`: simulator returns pending investigation `camt.029`.

The MVP stores one recall or investigation record per FI payment. Each 202 recall outcome scenario therefore needs a fresh FI payment. Do not run the accepted, rejected, and pending recall requests sequentially against the same fiPaymentId; create a new FI payment and use its captured `fiPaymentId` before each recall outcome scenario.

Suggested FI flow:

1. Run `Create FI Payment - Accepted`.
2. Confirm the response is `202 Accepted`, JSON, and contains `SETTLED`, `correlationId`, and `correspondentSettlementContext`.
3. Run `Get FI Payment Status`.
4. Run one FI recall outcome request, such as `Create FI Recall - Accepted`.
5. For `Create FI Recall - Rejected` or `Create FI Recall - Investigation Pending`, first run `Create FI Payment - Accepted` again with a new `fiIdempotencyKey`, confirm Postman captured the fresh `fiPaymentId`, and then run exactly one recall outcome request for that payment.
6. Run FI replay, FI idempotency conflict, FI authentication failure, FI scope failure, and FI validation failure scenarios.

## Payment Rail Recommendation Copilot Flow

`POST /v1/payment-rail-recommendations` accepts JSON intent summaries and returns deterministic simulator guidance. It requires `payment-rail-recommendations:create`, accepts `X-Correlation-ID`, and does not require `Idempotency-Key`.

Use these Postman requests:

| Postman request | Expected result |
| --- | --- |
| `Recommend Rail - RTP Immediate Low Value` | HTTP `200`, `recommendationStatus=RECOMMENDED`, rail `RTP`, arrangement `DOMESTIC_REAL_TIME_CLEARING`. |
| `Recommend Rail - ACH Vendor Batch` | HTTP `200`, rail `ACH`, arrangement `BATCH_CLEARING_NET_SETTLEMENT`. |
| `Recommend Rail - ACH Missing Max Single Amount Warning` | HTTP `200`, rail `ACH`, warning `MAX_SINGLE_AMOUNT_NOT_PROVIDED`. |
| `Recommend Rail - ACH Finality Conflict` | HTTP `200`, rail `ACH`, warning `BATCH_HIGH_VALUE_ENTRY_REVIEW`, and RTGS alternative. |
| `Recommend Rail - RTGS Corporate Finality` | HTTP `200`, rail `RTGS`, arrangement `DOMESTIC_INTERBANK_GROSS_SETTLEMENT`. |
| `Recommend Rail - RTGS FI Gross Settlement` | HTTP `200`, rail `RTGS`, client segment `FI`. |
| `Recommend Rail - FI Correspondent Arrangement` | HTTP `200`, rail `FI_CORRESPONDENT`, arrangement `CORRESPONDENT_ACCOUNT_PATH`. |
| `Recommend Rail - Cross Border Unsupported` | HTTP `200`, `recommendationStatus=UNSUPPORTED`, warning `CROSS_BORDER_NOT_SUPPORTED`. |
| `Recommend Rail - Non USD Unsupported` | HTTP `200`, `recommendationStatus=UNSUPPORTED`, warning `NON_USD_NOT_SUPPORTED`. |
| `Recommend Rail - Scope Failure` | HTTP `403`, JSON body `code=FORBIDDEN`. |

The `100000 USD` threshold is a deterministic simulator threshold, not a real scheme limit, bank policy, or production decision threshold. The endpoint returns `intentFit` as simulator rule explanation only, and next API guidance identifies candidate endpoint, scopes, headers, and payload format without generating a complete payment creation payload.

## Domestic Postman Expected Results

Use this table as the manual testing checklist for the domestic ISO payment workflow. The simulator scenario requests must include known HK clearing participants in the XML: `DbtrAgt` uses `CIBBHKHH` and `CdtrAgt` uses `SUPPHKHH`.

| Postman request | Preconditions and variables | Expected result |
| --- | --- | --- |
| `Create Payment - Success` | `jwtToken` has `payments:create`. Use a fresh `idempotencyKey` when you want a new payment. `mockScenario=success` or omit the mock scenario header. | HTTP `200`, `application/pain.002+xml`, and XML contains `<TxSts>ACSC</TxSts>`. Postman stores `AcctSvcrRef` into `paymentId`. |
| `Create Payment - Rejection` | Request uses `X-Mock-Scenario=rejection`. | HTTP `200`, `application/pain.002+xml`, and XML contains `<TxSts>RJCT</TxSts>` with clearing rejection reason details. |
| `Create Payment - Suspicious Proxy Or Account` | Request uses `X-Mock-Scenario=suspicious_proxy_or_account`. | HTTP `200`, `application/pain.002+xml`, and XML contains `<TxSts>RJCT</TxSts>` with suspicious beneficiary reason details. |
| `Create Payment - Pending` | Request uses `X-Mock-Scenario=pending`. | HTTP `200`, `application/pain.002+xml`, and XML contains `<TxSts>PDNG</TxSts>`. |
| `Create Payment - Timeout` | Request uses `X-Mock-Scenario=timeout`. | HTTP `200`, `application/pain.002+xml`, and XML contains `<TxSts>PDNG</TxSts>` with timeout/operational intervention reason details. |
| `Create Payment - Internal Failure` | Request uses `X-Mock-Scenario=internal_failure`. | HTTP `200`, `application/pain.002+xml`, and XML contains `<TxSts>RJCT</TxSts>` with reason code `MS03` and internal failure reason details. |
| `Create Payment - Non-HKD Profile Failure` | Request sends a non-HKD `InstdAmt` currency. | HTTP `422`, JSON error body, `code=SEMANTIC_PAYMENT_ERROR`, and no `pain.002` is generated. |
| `Create Payment - Malformed XML` | Request sends intentionally invalid XML. | HTTP `400`, JSON error body, `code=VALIDATION_ERROR`, and no payment is created. |
| `Create Payment - Authentication Failure` | Request sends an intentionally invalid bearer token. | HTTP `401`, JSON error body, `code=UNAUTHORIZED`, and `correlationId`. |
| `Create Payment - Authorization Failure` | `readOnlyJwtToken` has `payments:read` only and does not include `payments:create`. | HTTP `403`, JSON error body, `code=FORBIDDEN`, and `correlationId`. |
| `Create Payment - Replay` | Run after `Create Payment - Success` with the same `idempotencyKey`, same normalized `pain.001` business payload, and same mock scenario. | HTTP `200`; response body replays the original `pain.002`. |
| `Create Payment - Idempotency Conflict Setup` then `Create Payment - Idempotency Conflict` | Run setup first. The conflict request reuses `conflictIdempotencyKey` with different domestic payment business semantics. | Setup returns HTTP `200`. Conflict returns HTTP `409`, JSON error body, `code=IDEMPOTENCY_CONFLICT`, and the current `correlationId`. |

If a dedicated domestic scenario such as pending returns `<TxSts>RJCT</TxSts>` with `HK_UNKNOWN_PARTICIPANT`, re-import the latest collection and reopen the request from the collection tree. Older open Postman tabs can keep stale XML bodies that do not include `DbtrAgt` or `CdtrAgt`.

## FI Postman Expected Results

Use this table as the manual testing checklist for the FI correspondent workflow. If a response differs from the expected result, first check that the environment variables match the preconditions in the table.

| Postman request | Preconditions and variables | Expected result |
| --- | --- | --- |
| `Create FI Payment - Accepted` | `fiJwtToken` has `fi-payments:create`. Use a fresh `fiIdempotencyKey` when you want a new `fiPaymentId`. `fiMockScenario=fi_payment_accepted` or omit the mock scenario header. | HTTP `202`, JSON body, `status=SETTLED`, non-empty `paymentId`, `correlationId`, `statusLink=/v1/fi-payments/{paymentId}`, and `correspondentSettlementContext.accountRelationshipRole=NOSTRO`. Postman stores `paymentId` into `fiPaymentId`. |
| `Get FI Payment Status` | Run after a successful FI create. `fiJwtToken` or another token has `fi-payments:read`. `fiPaymentId` points to the created payment. | HTTP `200`, JSON body, same `paymentId`, lifecycle `status`, FI identifiers, FI parties, USD settlement currency, correspondent context, and `links.self=/v1/fi-payments/{paymentId}`. |
| `Create FI Recall - Accepted` | Run against a fresh `fiPaymentId` with no previous recall. `fiInvestigateToken` has `fi-payments:investigate`. Use a fresh `fiRecallIdempotencyKey`. `fiRecallScenario=recall_accepted` or omit the mock scenario header. | HTTP `202`, `application/camt.029+xml`, and XML contains `<Conf>CNCL</Conf>`. In this simulator profile, `CNCL` means the recall/cancellation request was accepted and the cancellation is confirmed. XML also contains `CorrelationId` and `FiPaymentId`. |
| `Create FI Recall - Rejected` | First create a new FI payment with a new `fiIdempotencyKey`. Use that new `fiPaymentId`, a fresh `fiRecallIdempotencyKey`, and `fiRecallScenario=recall_rejected`. | HTTP `202`, `application/camt.029+xml`, and XML contains `<Conf>RJCR</Conf>`. `RJCR` means the recall/cancellation request was rejected by the correspondent simulator. |
| `Create FI Recall - Investigation Pending` | First create a new FI payment with a new `fiIdempotencyKey`. Use that new `fiPaymentId`, a fresh `fiRecallIdempotencyKey`, and `fiRecallScenario=investigation_pending`. | HTTP `202`, `application/camt.029+xml`, and XML contains `<Conf>PDCR</Conf>`. `PDCR` means the recall/investigation remains pending correspondent response. |
| `Create FI Payment - Replay` | Run after `Create FI Payment - Accepted` with the same `fiIdempotencyKey`, same normalized `pacs.009` business payload, and same FI mock scenario. | HTTP `202`; response body replays the original FI acknowledgement, including the original `paymentId` and body `correlationId`. The response header uses the current request correlation ID. |
| `Create FI Payment - Idempotency Conflict Setup` then `Create FI Payment - Idempotency Conflict` | Run setup first. The conflict request reuses `fiConflictIdempotencyKey` with different FI payment business semantics. | Setup returns HTTP `202`. Conflict returns HTTP `409`, JSON error body, `code=IDEMPOTENCY_CONFLICT`, and the current `correlationId`. |
| `Create FI Payment - Authentication Failure` | Request sends an intentionally invalid bearer token. | HTTP `401`, JSON error body, `code=UNAUTHORIZED`, and `correlationId`. |
| `Create FI Payment - Scope Failure` | `fiReadOnlyJwtToken` has `fi-payments:read` only and does not include `fi-payments:create`. | HTTP `403`, JSON error body, `code=FORBIDDEN`, and `correlationId`. |
| `Create FI Payment - Validation Failure` | Request sends unsupported or incomplete `pacs.009` XML. | HTTP `400`, JSON error body, `code=VALIDATION_ERROR`, and no FI payment is created. |

For FI recall tests, `Idempotency-Key` and `Correlation-ID` have different purposes. Change `fiIdempotencyKey` to create a new FI payment, and change `fiRecallIdempotencyKey` to create a new recall request. Reusing the same `correlationId` is allowed because it is only a tracing value, but using a unique value per scenario makes logs easier to inspect.

`baas-api-sandbox` integration remains a future scenario-pack integration, not part of this runtime change. This repository owns the payment capability simulator and local developer artifacts; a later sandbox scenario pack can call or describe these FI flows after the contract stabilizes.

## Suggested Manual Flow

1. Run `Create Payment - Success`.
2. Confirm the response is `200 OK` and the XML contains `pain.002.001.10` plus `ACSC`.
3. Run `Get Payment Status`.
4. Set `mockScenario` to `rejection`, `suspicious_proxy_or_account`, `pending`, `timeout`, or `internal_failure`, or run the dedicated simulator scenario requests.
5. Run the error scenario requests for malformed XML, non-HKD profile failure, authentication failure, authorization failure, replay, and idempotency conflict.

For idempotency conflict, run `Create Payment - Idempotency Conflict Setup` first, then run `Create Payment - Idempotency Conflict`. The setup request generates `conflictIdempotencyKey` and creates the original payment; the conflict request reuses the same key with a different body.

The authentication failure request sends an intentionally invalid bearer token. The authorization failure request sends `readOnlyJwtToken` explicitly, so make sure that variable contains a token generated with only `payments:read`.

The collection intentionally does not use collection-level Authorization. Each request defines its own `Authorization` header so failure scenarios cannot accidentally inherit the normal `jwtToken`.

After re-importing the collection, close any older open request tabs and reopen the requests from the collection tree. Postman keeps already-open tabs in memory, so old tabs can continue using stale inherited authorization settings.

## Test Remote GKE Endpoint

Use the same Postman collection for the remote GKE deployment by changing only the environment values.

1. Confirm the GKE deployment, service, health check policy, Gateway, and HTTPRoute are applied:

```powershell
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/healthcheck-policy.yaml
kubectl apply -f k8s/gateway.yaml
```

2. Confirm the Gateway has an address and the route is attached:

```powershell
kubectl get gateway domestic-rtp-payment-api
kubectl get httproute domestic-rtp-payment-api
```

3. Set Postman `baseUrl` to the Gateway HTTP address, for example:

```text
http://<gateway-address>
```

4. Generate a token with both create and read scopes and paste it into Postman `jwtToken`:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:create,payments:read 3600"
```

5. Keep the GKE MVP deployment at one replica while payment status and idempotency storage are in memory:

```powershell
kubectl scale deployment domestic-rtp-payment-api --replicas=1
```

6. Run `Create Payment - Success`, then run `Get Payment Status` with the newly captured `paymentId`.

If `GET /v1/domestic-payments/{paymentId}` returns `PAYMENT_NOT_FOUND` after a successful create, first verify that Postman is using the new captured `paymentId` and that the remote deployment is still running one API replica.
