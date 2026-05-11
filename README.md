# Domestic RTP Payment Service API

Spring Boot MVP for a domestic real-time payment service API.

The service will expose:

- `POST /v1/domestic-payments`
- `GET /v1/domestic-payments/{paymentId}`

The implementation follows the OpenSpec change in `openspec/changes/add-domestic-rtp-payment-service-api`.

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

Import both files into Postman, select the local environment, then set `jwtToken` to a locally valid JWT before calling secured payment endpoints. Detailed local testing guidance is in `docs/developer-support/postman-local-testing.md`.

Generate a local Postman token:

```powershell
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:create,payments:read 3600"
```

## GKE Exposure

Kubernetes manifests are in `k8s/`:

- `deployment.yaml` deploys the Spring Boot API on container port `8080`.
- `service.yaml` exposes the workload as an internal `ClusterIP` service on port `80`.
- `gateway.yaml` uses GKE Gateway API resources to route API, OpenAPI, Swagger UI, and health paths to the service.

The deployment uses `/actuator/health/readiness` and `/actuator/health/liveness` for Kubernetes probes. The MVP does not add a custom lightweight API Gateway service; gateway-like controls remain inside the Payment Service API. A future production rollout can replace or front this exposure with Apigee, Kong, durable persistence, real downstream payment connectivity, and production-grade key management.

The image value `domestic-rtp-payment-api:0.1.0-SNAPSHOT` is a local placeholder. Set it to the registry image built by the target CI/CD pipeline before applying to a shared GKE cluster.
