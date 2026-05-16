# Domestic RTP Payment Service API

Spring Boot MVP for a domestic real-time payment service API.

The service will expose:

- `POST /v1/domestic-payments`
- `GET /v1/domestic-payments/{paymentId}`

The implementation follows the OpenSpec change in `openspec/changes/archive/2026-05-11-add-domestic-rtp-payment-service-api`.

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

## Product Process

The reusable OpenSpec and Superpowers workflow for future feature development is documented in `docs/product-process/openspec-superpowers-feature-playbook.md`.

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
