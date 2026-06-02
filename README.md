# CIB Payment API Simulation Suite

Spring Boot payment simulation API for domestic ISO payment initiation and FI correspondent payment workflows.

The suite currently exposes:

- `POST /v1/domestic-payments`
- `GET /v1/domestic-payments/{paymentId}`
- `POST /v1/ach-batches`
- `GET /v1/ach-batches/{batchId}`
- `POST /v1/rtgs-payments`
- `GET /v1/rtgs-payments/{paymentId}`
- `POST /v1/fi-payments`
- `GET /v1/fi-payments/{paymentId}`
- `POST /v1/fi-payments/{paymentId}/recall-requests`

The implementation follows these archived OpenSpec changes:

- `openspec/changes/archive/2026-05-11-add-domestic-rtp-payment-service-api`
- `openspec/changes/archive/2026-05-29-add-fi-correspondent-rfi-workflow`

## Capabilities

### Classic Payment Rail Simulation

- Uses the existing domestic payment API as the RTP baseline through `POST /v1/domestic-payments` and `GET /v1/domestic-payments/{paymentId}`.
- Adds ACH Direct Credit batch simulation through `POST /v1/ach-batches` and `GET /v1/ach-batches/{batchId}` with a JSON batch envelope, multiple entries, batch-level status, and entry-level status summary.
- Adds RTGS payment simulation through `POST /v1/rtgs-payments` and `GET /v1/rtgs-payments/{paymentId}` for corporate and FI client segments, including settlement finality and queued-for-liquidity scenarios.
- FI correspondent payment is a separate arrangement, not an RTP, ACH, or RTGS rail. The existing FI flow remains available through `/v1/fi-payments` for nostro, vostro, and loro correspondent-account-path demonstrations.
- Keeps cross-border and AI recommendation copilot concepts future-facing only. They describe possible product direction and no runtime behavior in this service.

Developer guidance is in `docs/developer-support/classic-payment-rail-simulation.md`.

### Domestic ISO Payment Simulation

- Accepts one ISO 20022 `pain.001.001.09` domestic HKD payment initiation per request.
- Returns ISO 20022 `pain.002.001.10` status reports.
- Supports deterministic local simulator scenarios for `ACSC`, `RJCT`, and `PDNG` outcomes.
- Exercises authentication, authorization, idempotency replay/conflict, correlation ID propagation, and JSON error envelopes.

### FI Correspondent Payment Simulation

- Accepts one supported USD `pacs.009.001.08` FI-to-FI payment initiation per request.
- Returns JSON FI payment acknowledgements and JSON FI payment status responses.
- Derives simulator-only correspondent settlement context, including `NOSTRO`, `VOSTRO`, or `LORO` account relationship role and a masked simulated account reference.
- Supports `camt.056.001.08` recall/investigation requests and returns deterministic `camt.029.001.09` resolution XML.
- Includes deterministic local simulator scenarios for accepted FI payment, unsupported correspondent rejection, correspondent review pending, recall accepted, recall rejected, and investigation pending.

This is a local simulation product. It does not provide real FPS/HKICL, SWIFT, CBPR+, correspondent banking, ledger, AML, sanctions, fraud, settlement, balance, or reconciliation connectivity.
ACH and RTGS simulation uses JSON runtime payloads only. It does not implement NACHA, ISO runtime for ACH/RTGS, ACH Direct Debit runtime, cross-border runtime, FX conversion, real ACH or RTGS connectivity, central bank ledger, liquidity management, or automatic RTGS-to-FI correspondent orchestration.

## Local Development

Requirements:

- JDK 21
- Maven 3.9+

Run tests:

```powershell
mvn test
```

Run locally:

```powershell
mvn spring-boot:run
```

Health endpoint:

```text
GET /actuator/health
```

OpenAPI rendering:

```text
GET /swagger-ui/index.html
GET /openapi/domestic-payment-api.yaml
```

Postman local debugging artifacts:

```text
postman/domestic-rtp-payment-api.postman_collection.json
postman/domestic-rtp-payment-api.local.postman_environment.json
```

Import both files into Postman, select the local environment, then set local JWT variables before calling secured endpoints. The collection contains domestic ISO XML scenarios, the `Classic Payment Rail Simulation` journey for RTP baseline, ACH Direct Credit, RTGS, and FI correspondent comparison, FI `pacs.009` scenarios, FI `camt.056` to `camt.029` recall/investigation scenarios, deterministic simulator outcomes, replay/conflict checks, auth/scope failure checks, and status queries. Detailed local testing guidance is in `docs/developer-support/postman-local-testing.md` and `docs/developer-support/classic-payment-rail-simulation.md`.

Generate a local domestic Postman token:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:create,payments:read 3600"
```

Generate a local FI Postman token:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=fi-client-a fi-payments:create,fi-payments:read,fi-payments:investigate 3600"
```

Generate local ACH and RTGS Postman tokens:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a ach-batches:create,ach-batches:read 3600"
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a rtgs-payments:create,rtgs-payments:read 3600"
```

## Product Process

The reusable OpenSpec and Superpowers workflow for future feature development is documented in `docs/product-process/openspec-superpowers-feature-playbook.md`.
The product direction for the payment simulation suite is documented in `docs/product-strategy/payment-simulation-suite-vision.md`.

## GKE Exposure

Kubernetes manifests are in `k8s/`:

- `deployment.yaml` deploys the Spring Boot API on container port `8080`.
- `service.yaml` exposes the workload as an internal `ClusterIP` service on port `80`.
- `gateway.yaml` uses GKE Gateway API resources to route API, OpenAPI, Swagger UI, and health paths to the service.
- `healthcheck-policy.yaml` configures the GKE load balancer health check to call `/actuator/health`.

The deployment uses `/actuator/health/readiness` and `/actuator/health/liveness` for Kubernetes probes. The MVP does not add a custom lightweight API Gateway service; gateway-like controls remain inside the Payment Service API. A future production rollout can replace or front this exposure with Apigee, Kong, durable persistence, real downstream payment connectivity, and production-grade key management.

The image value `domestic-rtp-payment-api:0.1.0-SNAPSHOT` is a local placeholder. Set it to the registry image built by the target CI/CD pipeline before applying to a shared GKE cluster.

### GKE Gateway API Configuration

The GKE external entry point is defined with Kubernetes Gateway API resources, not a custom gateway application. The working MVP path is:

1. Deploy the API workload and internal service:

```powershell
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

2. Attach the GKE load balancer health check policy to the service:

```powershell
kubectl apply -f k8s/healthcheck-policy.yaml
```

3. Create the managed external HTTP Gateway and route:

```powershell
kubectl apply -f k8s/gateway.yaml
```

The Gateway uses:

```yaml
gatewayClassName: gke-l7-global-external-managed
```

The `HTTPRoute` forwards these path prefixes to the `domestic-rtp-payment-api` service:

```text
/v1/domestic-payments
/v1/ach-batches
/v1/rtgs-payments
/v1/fi-payments
/swagger-ui
/swagger-ui.html
/openapi
/v3/api-docs
/actuator/health
```

4. Confirm Gateway provisioning and route attachment:

```powershell
kubectl get gateway domestic-rtp-payment-api
kubectl get httproute domestic-rtp-payment-api
```

Use the Gateway address as the Postman `baseUrl` for remote GKE testing.

For the in-memory MVP, keep the API at one replica:

```powershell
kubectl scale deployment domestic-rtp-payment-api --replicas=1
```

Multiple replicas require durable shared persistence for payment status and idempotency records.
