## 1. Contracts and Fixtures

- [x] 1.1 Define ISO-only media type handling for `pain.001.001.09` payment creation requests and `pain.002.001.10` responses/status queries.
- [x] 1.2 Add synthetic HKD `pain.001.001.09` fixtures for success, rejection, suspicious proxy or account, pending, timeout, internal failure, and validation failure.
- [x] 1.3 Add synthetic `pain.002.001.10` fixtures for `ACSC`, `RJCT`, and `PDNG` outcomes.
- [x] 1.4 Define internal domain records for admitted ISO payment candidates, beneficiary account or FPS proxy, supported purpose fields, internal interbank transfer representation, engine payment record, and latest `pain.002` report.
- [x] 1.5 Define payment reason codes for ISO validation, HK profile rejection, suspicious proxy or account, pending processing, simulator timeout, and simulator internal failure.

## 2. XML Parsing and HK Profile Validation

- [x] 2.1 Implement secure XML parser configuration for ISO 20022 request bodies with external entity resolution disabled.
- [x] 2.2 Implement supported `pain.001.001.09` detection and reject non-`pain.001.001.09` XML with consistent validation errors.
- [x] 2.3 Extract debtor, creditor, amount, currency, client-supplied reference/end-to-end identifier, remittance, and optional purpose/category purpose fields from supported `pain.001.001.09`.
- [x] 2.4 Enforce HKD-only domestic realtime profile validation, including beneficiary account or FPS proxy and payment reference or end-to-end identifier.
- [x] 2.5 Reject custom JSON payment initiation as unsupported for this experiment.
- [x] 2.6 Add unit tests for valid extraction, malformed XML, unsupported XML/message version, custom JSON initiation, missing beneficiary identifier, missing traceability identifier, unsupported currency/country profile, and unsafe XML content.

## 3. Payment Engine and Internal Mapping

- [x] 3.1 Introduce `PaymentEngineInitiationPort` for admitted ISO payment candidates and `PaymentEngineStatusQueryPort` for latest status reports.
- [x] 3.2 Implement an in-process Payment Engine component that receives admitted initiation, authorization context, correlation ID, idempotency context, and scenario context.
- [x] 3.3 Move ISO-created payment record ownership behind the Payment Engine boundary, including lifecycle status and latest `pain.002` report storage.
- [x] 3.4 Map admitted `pain.001.001.09` initiation to an internal `pacs.008` interbank transfer representation without exposing `pacs.008` externally.
- [x] 3.5 Preserve traceability between engine payment ID, supported ISO identifiers, internal message reference, idempotency link record, correlation ID, and latest `pain.002`.
- [x] 3.6 Map simulator outcomes to internal `COMPLETED`, `REJECTED`, `PROCESSING`, `TIMEOUT`, and `FAILED` statuses with reason details.
- [x] 3.7 Add architecture tests preventing controllers and Edge services from directly accessing engine payment repositories.
- [x] 3.8 Add unit tests for engine record ownership, mapping, traceability preservation, status query port behavior, and outcome-to-status mapping.

## 4. HK Clearing and Settlement Simulator

- [x] 4.1 Implement an HKD-only clearing and settlement simulator behind an application port.
- [x] 4.2 Add configured test participants for payer and payee routing validation.
- [x] 4.3 Implement deterministic simulator scenarios for success, rejection, suspicious proxy or account, pending processing, timeout, and internal failure.
- [x] 4.4 Ensure simulator scenario controls remain local/test-only and are not represented as production payment semantics.
- [x] 4.5 Add simulator unit tests for known participants, unknown participants, non-HKD rejection, pending processing, timeout, and all deterministic scenarios.

## 5. ISO Status Reporting

- [x] 5.1 Implement `pain.002.001.10` generation for settled `ACSC`, rejected `RJCT`, and pending `PDNG` outcomes.
- [x] 5.2 Include supported original `pain.001` identifiers and engine payment ID linkage in generated status reports.
- [x] 5.3 Include reason details for `RJCT` and `PDNG`, including distinct reasons for normal processing and timeout/operational intervention.
- [x] 5.4 Ensure admission failures do not generate `pain.002`.
- [x] 5.5 Add unit tests for `pain.002` XML generation, status mapping, original identifier traceability, and reason details.

## 6. API Integration and Idempotency

- [x] 6.1 Update `POST /v1/domestic-payments` to require supported `pain.001.001.09` XML and return `pain.002.001.10` XML for processed outcomes.
- [x] 6.2 Update `GET /v1/domestic-payments/{paymentId}` to query the Payment Engine status port and return latest `pain.002.001.10` XML for ISO-created payments.
- [x] 6.3 Reuse existing authentication, scope validation, idempotency key requirement, correlation ID handling, and JSON error response shape for request admission failures.
- [x] 6.4 Compute Edge idempotency fingerprints from normalized XML payment semantics and behaviorally relevant scenario context.
- [x] 6.5 Store Edge idempotency link records with client ID, idempotency key, fingerprint, original `pain.002` response, engine payment ID, correlation ID, and timestamps.
- [x] 6.6 Treat semantically equivalent XML formatting differences as idempotent replay and changed payment semantics as idempotency conflict.
- [x] 6.7 Add integration tests for `ACSC`, `RJCT`, `PDNG`, replay, conflict, validation errors, authentication, authorization, correlation propagation, and status query.

## 7. Observability and Sensitive Data Handling

- [x] 7.1 Extend masking utilities to cover FPS proxy values, mobile/email proxy identifiers, HKID-like identifiers, and XML-derived account identifiers.
- [x] 7.2 Ensure logs and error diagnostics do not include raw sensitive XML payloads, bearer tokens, full account numbers, full proxy values, or HKID-like identifiers.
- [x] 7.3 Add observability tests for XML initiation, engine mapping, simulator outcomes, generated `pain.002`, and masked sensitive data.

## 8. Developer Support

- [x] 8.1 Update OpenAPI 3.0.3 documentation with `pain.001.001.09` request body, `pain.002.001.10` responses/status reports, HKD-only profile constraints, simulator scenarios, and error responses.
- [x] 8.2 Update Postman collection and local environment with XML `pain.001` requests, `pain.002` response examples, status query, and deterministic HK simulator scenarios.
- [x] 8.3 Remove or replace JSON payment initiation examples from OpenAPI, Postman, fixtures, and developer documentation.
- [x] 8.4 Update developer documentation to describe ISO-native initiation, simulator-only scope, no real HKICL/FPS connectivity, and `pacs.008` as internal-only mapping.
- [x] 8.5 Add contract tests to verify OpenAPI examples and Postman artifacts align with behavior specs.

## 9. Verification

- [x] 9.1 Run focused unit and integration tests for ISO parsing, HK profile validation, payment engine mapping, `pain.002` generation, simulator behavior, idempotency, and API flows.
- [x] 9.2 Run architecture tests for Edge/Engine boundary enforcement.
- [x] 9.3 Run the full Maven test suite.
- [x] 9.4 Run `npx.cmd openspec validate add-hk-iso20022-payment-simulation`.
- [x] 9.5 Review the change for architecture constraints, sensitive data leakage, accidental JSON initiation support, and accidental exposure of internal `pacs.008`.
