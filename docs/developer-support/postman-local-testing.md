# Postman Local Testing

This guide helps a developer run the Domestic RTP Payment Service API locally and exercise the main API flows from Postman.

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

## Mock Scenarios

`X-Mock-Scenario` is local/test-only behavior. It is not part of production payment business semantics.

Allowed values:

- `success`: downstream mock maps the payment to `COMPLETED`.
- `rejection`: downstream mock maps the payment to `REJECTED`.
- `timeout`: downstream mock maps the payment to `TIMEOUT`.
- `internal_failure`: downstream mock maps the payment to `FAILED`.

The payment creation endpoint returns `202 Accepted` after request acceptance. Run `Get Payment Status` after creation to inspect the final mock outcome.

## Suggested Manual Flow

1. Run `Create Payment - Success`.
2. Confirm the response is `202 Accepted` and Postman captures `paymentId`.
3. Run `Get Payment Status`.
4. Set `mockScenario` to `rejection`, `timeout`, or `internal_failure`, or run the dedicated mock scenario requests.
5. Run the error scenario requests for invalid request, authentication failure, authorization failure, and idempotency conflict.

For idempotency conflict, first create a payment using `conflictIdempotencyKey`, then run `Create Payment - Idempotency Conflict` with the same key and a different body.
