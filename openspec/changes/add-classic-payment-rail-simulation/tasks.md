## 1. Domain And Ports

- [x] 1.1 Add ACH domain records, identifiers, statuses, entry summaries, and reason value objects.
- [x] 1.2 Add RTGS domain records, identifiers, statuses, settlement finality, client segment, and reason value objects.
- [x] 1.3 Add application ports for ACH batch repository, RTGS payment repository, ACH simulator, and RTGS simulator.
- [x] 1.4 Add in-memory ACH and RTGS repositories separate from existing RTP and FI status stores.

## 2. ACH Direct Credit Implementation

- [x] 2.1 Add ACH API DTOs for batch creation, entry creation, batch acknowledgement, batch status, entry status, links, and reason responses.
- [x] 2.2 Implement ACH batch admission and validation for JSON Direct Credit batches, required batch fields, required entry fields, duplicate entry references, USD-only currency, derived totals, and unsupported Direct Debit semantics.
- [x] 2.3 Implement deterministic ACH simulator outcomes for accepted, settled, settlement pending, partially returned, and rejected scenarios.
- [x] 2.4 Implement ACH create service with authorization context, idempotency evaluation, simulator invocation, persistence, observability, and response mapping.
- [x] 2.5 Implement ACH status query service with owner-only lookup and response mapping.
- [x] 2.6 Add ACH controller routes for `POST /v1/ach-batches` and `GET /v1/ach-batches/{batchId}`.

## 3. RTGS Implementation

- [x] 3.1 Add RTGS API DTOs for payment creation, corporate fields, FI fields, acknowledgement, status, links, related capability guidance, and reason responses.
- [x] 3.2 Implement RTGS request validation for supported client segments, corporate required fields, FI required agent identifiers, USD-only currency, no hard high-value amount threshold, and unsupported runtime semantics.
- [x] 3.3 Implement deterministic RTGS simulator outcomes for settled, queued for liquidity, and rejected scenarios.
- [x] 3.4 Implement RTGS create service with authorization context, idempotency evaluation, simulator invocation, persistence, observability, settlement finality, and response mapping.
- [x] 3.5 Implement RTGS status query service with owner-only lookup and response mapping.
- [x] 3.6 Add RTGS controller routes for `POST /v1/rtgs-payments` and `GET /v1/rtgs-payments/{paymentId}`.

## 4. Shared Controls

- [ ] 4.1 Add JWT scope enforcement for `ach-batches:create`, `ach-batches:read`, `rtgs-payments:create`, and `rtgs-payments:read`.
- [ ] 4.2 Require `Idempotency-Key` for ACH and RTGS create operations and allow status queries without idempotency keys.
- [ ] 4.3 Implement ACH and RTGS idempotent replay and conflict behavior using operation/resource-specific fingerprints.
- [ ] 4.4 Propagate correlation IDs through ACH and RTGS responses, records, idempotency entries, simulator calls, logs, and errors.
- [ ] 4.5 Ensure ACH and RTGS validation, auth, not-found, and idempotency conflict errors use the consistent JSON error envelope.
- [ ] 4.6 Mask or omit ACH and RTGS account identifiers, sensitive request values, and bearer tokens at logging and diagnostics boundaries.

## 5. OpenAPI And Developer Artifacts

- [ ] 5.1 Add OpenAPI paths, schemas, security scopes, examples, and error responses for ACH Direct Credit batch APIs.
- [ ] 5.2 Add OpenAPI paths, schemas, security scopes, examples, settlement finality fields, and error responses for RTGS APIs.
- [ ] 5.3 Update the Postman collection with the Classic Payment Rail Simulation journey for RTP baseline, ACH Direct Credit, RTGS, and FI correspondent comparison scenarios.
- [ ] 5.4 Update the Postman local environment with ACH and RTGS variables, token guidance, idempotency keys, captured IDs, and mock scenario values.
- [ ] 5.5 Update the root README to describe the classic payment rail simulation product surface.
- [ ] 5.6 Add or update developer support documentation with the rail comparison table, scenario catalog, expected results, common setup mistakes, and future copilot hook.

## 6. Tests And Validation

- [ ] 6.1 Add ACH unit and integration tests for validation, deterministic outcomes, idempotency replay/conflict, owner-only status query, correlation propagation, and entry-level status summaries.
- [ ] 6.2 Add RTGS unit and integration tests for corporate settled, FI settled, FI queued for liquidity, rejected, validation, idempotency replay/conflict, owner-only status query, and settlement finality.
- [ ] 6.3 Add scope authorization tests for ACH and RTGS create/read operations.
- [ ] 6.4 Add sensitive logging tests or assertions for ACH and RTGS account identifiers and raw sensitive payload handling.
- [ ] 6.5 Extend OpenAPI contract tests to verify ACH and RTGS paths, schemas, scopes, examples, and error responses.
- [ ] 6.6 Extend Postman artifact validation tests to verify scenario requests, expected statuses, required headers, environment variables, and alignment with simulator mappings.
- [ ] 6.7 Run `npx.cmd openspec validate add-classic-payment-rail-simulation`.
- [ ] 6.8 Run focused Maven tests for ACH, RTGS, OpenAPI, Postman, auth, idempotency, and logging coverage.
- [ ] 6.9 Run the full `mvn test` suite before marking implementation complete.
