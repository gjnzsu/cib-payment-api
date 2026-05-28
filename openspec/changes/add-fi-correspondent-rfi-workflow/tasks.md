## 1. FI ISO Fixtures and Contract Baseline

- [x] 1.1 Add synthetic `pacs.009` XML fixtures for FI payment accepted, unsupported correspondent rejection, correspondent review pending, non-USD validation failure, missing required field, and unsafe XML cases.
- [x] 1.2 Add synthetic `camt.056` XML fixtures for recall accepted, recall rejected, investigation pending, wrong original payment reference, malformed XML, and unsafe XML cases.
- [x] 1.3 Add synthetic `camt.029` XML fixtures for accepted, rejected, and pending investigation resolutions.
- [x] 1.4 Add fixture validation tests that verify namespaces, synthetic identifiers, USD-only valid FI fixtures, and absence of real sensitive account or customer data.
- [x] 1.5 Define a supported USD correspondent route profile matrix that deterministically derives `NOSTRO`, `VOSTRO`, and `LORO` settlement account roles for test fixtures and simulator scenarios.

## 2. FI Domain Model and Repository Boundaries

- [x] 2.1 Define FI payment domain concepts for FI payment ID, FI payment record, FI payment status, FI parties, supported FI payment identifiers, and USD settlement amount.
- [x] 2.2 Define correspondent settlement context with instructing agent, instructed agent, optional correspondent/intermediary bank, account relationship role, and masked simulated account reference.
- [x] 2.3 Define recall/investigation domain concepts for recall request record, investigation status, investigation reason, and `camt.029` resolution summary.
- [x] 2.4 Add in-memory FI payment and recall/investigation repositories behind application ports, separate from domestic payment and idempotency storage.
- [x] 2.5 Add architecture tests that prevent controllers from directly accessing FI repositories and prevent FI domain code from depending on HTTP, Spring, or XML parser implementation details.

## 3. pacs.009 Admission and FI Payment Simulation

- [x] 3.1 Implement secure supported-profile `pacs.009` parser with external entity resolution disabled.
- [x] 3.2 Validate required FI payment fields, single-payment request shape, USD-only settlement currency, and correspondent route profiles that derive supported account relationship roles.
- [x] 3.3 Implement FI payment admission service that returns a normalized FI payment candidate only after request admissibility checks pass.
- [x] 3.4 Implement deterministic FI payment simulator outcomes for `fi_payment_accepted`, `fi_payment_rejected_unsupported_correspondent`, and `fi_payment_pending_correspondent_review`.
- [x] 3.5 Implement FI payment creation service that stores FI payment records with lifecycle status, correspondent context, owner client, idempotency link, and correlation ID.
- [x] 3.6 Add unit tests for parser behavior, admission validation, simulator outcomes, correspondent context capture, and FI payment record creation.

## 4. FI Payment Status Query

- [x] 4.1 Implement FI payment status query service with authenticated client ownership checks.
- [x] 4.2 Return JSON status responses containing payment ID, lifecycle status, FI parties, correspondent settlement context, latest recall/investigation summary when present, and correlation ID.
- [x] 4.3 Add not-found and unrelated-client tests that verify no unrelated FI payment data leaks.

## 5. camt.056 Recall Request and camt.029 Resolution

- [x] 5.1 Implement secure supported-profile `camt.056` parser with external entity resolution disabled.
- [x] 5.2 Validate recall request identifiers, supported recall reason, target FI payment ownership, reference to the original FI payment, and eligibility for `SETTLED` or `PROCESSING` FI payment status.
- [x] 5.3 Implement deterministic recall/investigation simulator outcomes for `recall_accepted`, `recall_rejected`, and `investigation_pending`.
- [x] 5.4 Implement recall/investigation service that stores at most one recall record per FI payment linked to correspondent settlement context.
- [x] 5.5 Implement deterministic `camt.029` renderer for accepted, rejected, and pending investigation resolutions.
- [x] 5.6 Add unit tests for `camt.056` parsing, recall validation, `SETTLED` and `PROCESSING` recall eligibility, `REJECTED` payment recall rejection, one-record-per-payment rule, simulator outcomes, `camt.029` rendering, record storage, and latest investigation summary updates.

## 6. Security, Idempotency, Correlation, and Masking

- [x] 6.1 Add FI-specific authorization enforcement for `fi-payments:create`, `fi-payments:read`, and `fi-payments:investigate`.
- [x] 6.2 Update local JWT token generation and test helpers to support FI-specific scopes.
- [x] 6.3 Require `Idempotency-Key` for FI payment creation and recall requests, while leaving FI status query idempotency-free.
- [x] 6.4 Implement normalized idempotency fingerprints for accepted `pacs.009` and `camt.056` business semantics.
- [x] 6.5 Replay duplicate FI payment and recall requests with the original accepted response, and reject semantic conflicts with `409 Conflict`.
- [x] 6.6 Propagate correlation ID through FI records, recall records, idempotency records, simulator calls, logs, response headers, JSON responses, and `camt.029` XML responses.
- [x] 6.7 Extend sensitive data masking for FI XML payloads, BIC-linked account references, and simulated correspondent account references.
- [x] 6.8 Add integration tests for FI auth failures, scope failures, idempotency replay/conflict, correlation propagation, ownership checks, and sensitive logging.

## 7. FI API Integration

- [x] 7.1 Add `POST /v1/fi-payments` controller path for supported `pacs.009` XML and JSON acknowledgement responses.
- [x] 7.2 Add `GET /v1/fi-payments/{paymentId}` controller path for JSON FI status responses.
- [x] 7.3 Add `POST /v1/fi-payments/{paymentId}/recall-requests` controller path for supported `camt.056` XML requests and `camt.029` XML responses.
- [x] 7.4 Ensure validation, authentication, authorization, idempotency, ownership, not-found, and XML parse failures return consistent JSON error envelopes with correlation ID.
- [x] 7.5 Add FI payment creation and status integration tests for accepted FI payment, rejected FI payment, pending FI payment, derived correspondent account context, and FI status query.
- [x] 7.6 Add recall and `camt.029` integration tests for recall accepted, recall rejected, investigation pending, wrong original payment reference, rejected payment recall rejection, and duplicate recall conflict.
- [x] 7.7 Add FI error, security, and ownership integration tests for invalid XML, missing idempotency key, scope failures, not-found lookup, and unrelated-client access.

## 8. Developer Support and Product Documentation

- [ ] 8.1 Update OpenAPI 3.0.3 documentation for FI payment endpoints, media types, scopes, idempotency, JSON status response, `camt.029` response examples, and simulator-only limitations.
- [ ] 8.2 Update Postman collection and local environment with FI payment creation, status query, recall accepted, recall rejected, investigation pending, idempotency replay, idempotency conflict, auth failure, scope failure, and validation failure scenarios.
- [ ] 8.3 Update README or developer support docs with FI-specific token generation, USD-only profile, correspondent account context, and no-real-ledger limitations.
- [ ] 8.4 Update product strategy or developer docs to note that `baas-api-sandbox` integration remains a future scenario-pack integration, not part of this runtime change.
- [ ] 8.5 Add artifact validation tests that verify OpenAPI, Postman, and fixtures align with the FI behavior specs.

## 9. Verification and OpenSpec Closure

- [x] 9.1 Run focused FI parser, renderer, service, simulator, security, and integration tests.
- [x] 9.2 Run architecture and sensitive logging tests.
- [x] 9.3 Run full Maven test suite.
- [x] 9.4 Run `npx.cmd openspec validate add-fi-correspondent-rfi-workflow`.
- [ ] 9.5 Review implementation against proposal, design, specs, and tasks for OpenSpec-to-Superpowers alignment before starting archive or branch completion.
