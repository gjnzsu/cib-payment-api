# Postman Local Testing

This guide helps a developer run the Domestic RTP Payment Service API locally and exercise the ISO-native HK payment simulation flows from Postman.

## Run The API

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

JWTs must match the API's configured issuer, audience, signature validation, and required claims. For local testing, generate Postman-compatible tokens with the local-only helper:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:create,payments:read 3600"
```

For the `readOnlyJwtToken` variable used by the authorization failure request:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:read 3600"
```

The helper prints the JWT to standard output. Paste the first token into `jwtToken` and the second token into `readOnlyJwtToken`.

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
- `internal_failure`: simulator returns `PDNG` with internal failure reason details.

The payment creation endpoint returns `200 OK` with `pain.002.001.10` after request processing. Run `Get Payment Status` after creation to retrieve the latest engine-owned `pain.002` report.

When testing the GKE MVP deployment, run a single API replica while the service uses in-memory payment status storage. With multiple replicas, a create request can be handled by one pod and a status request by another pod, causing `PAYMENT_NOT_FOUND` for a valid payment ID. If the live deployment image was set separately from the placeholder manifest image, scale the existing deployment instead of reapplying the manifest:

```powershell
kubectl scale deployment domestic-rtp-payment-api --replicas=1
```

Durable shared persistence is the future production path for multi-replica deployments.

The dedicated mock scenario requests assert ISO status values in the returned XML. Some requests also use `pm.sendRequest` for lightweight local helper checks.

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
