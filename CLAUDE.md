# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.5.3 MVP for a domestic real-time payment service API. The service exposes two main endpoints:
- `POST /v1/domestic-payments` - Create a domestic payment
- `GET /v1/domestic-payments/{paymentId}` - Get payment status

The implementation uses Java 21, Maven, Spring Security with OAuth2 JWT, and follows a ports-and-adapters architecture with in-memory persistence for the MVP.

## Development Commands

### Build and Test
```powershell
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=CreateDomesticPaymentIntegrationTest

# Run a single test method
mvn test -Dtest=CreateDomesticPaymentIntegrationTest#shouldAcceptValidPaymentRequest
```

### Run Locally
```powershell
# Start the Spring Boot application
mvn spring-boot:run

# Health check
curl http://localhost:8080/actuator/health

# OpenAPI documentation
# Browser: http://localhost:8080/swagger-ui/index.html
# YAML: http://localhost:8080/openapi/domestic-payment-api.yaml
```

### Generate Local JWT Tokens
```powershell
# Full permissions (create + read)
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:create,payments:read 3600"

# Read-only permissions
mvn -q -DskipTests exec:java "-Dexec.mainClass=com.cib.payment.api.infrastructure.security.LocalJwtTokenGenerator" "-Dexec.args=client-a payments:read 3600"
```

### OpenSpec Workflow
```powershell
# Validate OpenSpec artifacts
npx.cmd openspec validate

# Validate a specific change
npx.cmd openspec validate <change-name>

# Archive a completed change after merge
npx.cmd openspec archive <change-name>
```

## Architecture

### Layered Structure

The codebase follows a ports-and-adapters (hexagonal) architecture with clear separation of concerns:

**API Layer** (`com.cib.payment.api.api`)
- Controllers handle HTTP requests/responses
- DTOs define request/response contracts
- Custom validators for domain-specific validation (e.g., `@DecimalString`)
- `GlobalExceptionHandler` maps application exceptions to HTTP error responses
- `CorrelationIdFilter` ensures every request has a correlation ID for tracing

**Application Layer** (`com.cib.payment.api.application`)
- Services orchestrate business logic and coordinate between ports
- `CreateDomesticPaymentService`: handles payment creation with idempotency
- `GetDomesticPaymentStatusService`: retrieves payment status
- `AuthorizationContextService`: extracts client identity and correlation from JWT
- `RequestFingerprintService`: generates request fingerprints for idempotency conflict detection
- Exceptions define application-level failures
- Ports define interfaces for infrastructure adapters

**Domain Layer** (`com.cib.payment.api.domain.model`)
- Pure domain models with no framework dependencies
- Value objects: `PaymentId`, `Money`, `AccountReference`, `CorrelationId`
- Entities: `PaymentRecord`, `IdempotencyRecord`, `PaymentInstruction`
- Enums: `PaymentStatus`, `PaymentReason`

**Infrastructure Layer** (`com.cib.payment.api.infrastructure`)
- Adapters implement application ports
- `InMemoryPaymentStatusRepository` and `InMemoryIdempotencyRepository`: in-memory persistence (MVP only)
- `MockDownstreamPaymentProcessor`: simulates downstream payment processing with configurable scenarios
- `LocalJwtDecoderConfig` and `LocalJwtKeyMaterial`: local JWT validation (MVP only)
- `MicrometerPaymentObservability`: metrics and logging
- `SecurityConfig`: Spring Security configuration with OAuth2 resource server

### Key Architectural Patterns

**Idempotency**: Every payment creation requires an `Idempotency-Key` header. The service stores a fingerprint of the request body and returns the same response for duplicate keys with matching bodies, or throws `IdempotencyConflictException` for mismatched bodies.

**Correlation**: Every request gets a correlation ID (from `X-Correlation-ID` header or auto-generated) that flows through logs, metrics, and responses for distributed tracing.

**Mock Scenarios**: The `X-Mock-Scenario` header (local/test only) controls downstream mock behavior: `success`, `rejection`, `timeout`, `internal_failure`.

**Security**: OAuth2 JWT bearer tokens with scope-based authorization:
- `payments:create` scope required for POST
- `payments:read` scope required for GET

**Observability**: Micrometer metrics track API requests, payment lifecycle events, auth failures, and idempotency behavior.

## Testing Strategy

Tests are organized by layer and concern:

- **Integration tests** (`*IntegrationTest.java`): Full Spring context, test complete request/response flows
- **Service tests** (`*ServiceTest.java`): Unit tests for service logic with mocked dependencies
- **Contract tests** (`OpenApiContractTest.java`): Validate OpenAPI spec matches implementation
- **Validation tests** (`*ValidationTest.java`): Test request validation rules
- **Infrastructure tests**: Test repository implementations, security config, K8s manifests

The test suite includes concurrency tests for idempotency and lifecycle tests for payment status transitions.

## OpenSpec and Superpowers Workflow

This project uses a structured product development process documented in `docs/product-process/openspec-superpowers-feature-playbook.md`:

1. **OpenSpec owns scope**: Product intent, behavior, design decisions, and task scope live in `openspec/`
2. **Superpowers owns method**: Engineering discipline for planning, TDD, debugging, and verification

When implementing features:
- Read OpenSpec artifacts first: `proposal.md`, `design.md`, `spec.md`, `tasks.md`
- Create implementation plans that map to OpenSpec tasks
- Use `superpowers:test-driven-development` for behavior changes
- Use `superpowers:verification-before-completion` before committing
- Archive OpenSpec changes after merge

## Postman Testing

Postman collection and environment are in `postman/`:
- `domestic-rtp-payment-api.postman_collection.json`
- `domestic-rtp-payment-api.local.postman_environment.json`

Import both, select the local environment, generate a JWT token, and set it in the `jwtToken` variable. See `docs/developer-support/postman-local-testing.md` for detailed guidance.

## GKE Deployment

Kubernetes manifests are in `k8s/`:
- `deployment.yaml`: Spring Boot API on port 8080
- `service.yaml`: ClusterIP service on port 80
- `gateway.yaml`: GKE Gateway API resources for external HTTP access
- `healthcheck-policy.yaml`: Load balancer health check configuration

The MVP uses in-memory storage, so keep replicas at 1:
```powershell
kubectl scale deployment domestic-rtp-payment-api --replicas=1
```

## Configuration

Key application properties in `src/main/resources/application.yml`:
- `payment-api.domestic-currency`: Supported currency (MYR)
- `payment-api.jwt.issuer`: Expected JWT issuer
- `payment-api.jwt.audience`: Expected JWT audience
- `payment-api.idempotency.retention-hours`: Idempotency record retention (24h)

## Important Constraints

**MVP Limitations**:
- In-memory persistence (no database)
- Single replica only (no shared state)
- Local JWT key material (not production-ready)
- Mock downstream processor (no real payment connectivity)

**Production Path**: Future production rollout requires durable persistence, real downstream payment connectivity, production key management, and API gateway (Apigee/Kong).
