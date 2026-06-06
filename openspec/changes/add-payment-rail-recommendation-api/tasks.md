## 1. Domain And Application Model

- [x] 1.1 Add recommendation domain records and enums for recommendation ID, status, rail, arrangement, client segment, urgency, cost sensitivity, confidence level, matched factor, warning, reason code, next API guidance, and tradeoff dimensions.
- [x] 1.2 Add amount summary and debtor account profile value objects without storing full account identifiers.
- [x] 1.3 Add application service interfaces or ports needed to keep deterministic rule evaluation separate from HTTP controllers.
- [x] 1.4 Add unit tests for recommendation model validation, enum coverage, and response-local recommendation ID behavior.

## 2. API DTOs And Validation

- [x] 2.1 Add request DTOs for `POST /v1/payment-rail-recommendations` with required rail-neutral intent fields and optional preference fields.
- [x] 2.2 Add response DTOs for recommended option, confidence level, matched factors, warnings, alternatives, tradeoffs, intent fit, and next API guidance.
- [x] 2.3 Validate required fields, positive `paymentCount`, positive amount summary values, supported enum values, and default `debtorAccountProfile.count` to one when omitted.
- [ ] 2.4 Reject malformed or missing required fields with consistent JSON validation errors and field-level details.
- [x] 2.5 Add DTO validation tests for valid single-payment, valid batch, missing field, invalid enum, invalid amount, and invalid payment count cases.

## 3. Deterministic Recommendation Rules

- [x] 3.1 Implement unsupported guardrails for non-domestic and non-USD valid intents returning recommendation status `UNSUPPORTED`.
- [x] 3.2 Implement FI correspondent preference rule for FI client intents with `CORRESPONDENT_ACCOUNT_PATH`.
- [x] 3.3 Implement ACH batch rule for `paymentCount > 1`, `batchPreferred=true`, or high cost sensitivity.
- [x] 3.4 Implement RTGS rule for finality-required or high-value single-payment intents using the `100000 USD` simulator threshold.
- [x] 3.5 Implement RTP rule for immediate low-value single-payment intents without finality requirement.
- [x] 3.6 Implement deterministic rule precedence and fallback behavior for default domestic single-payment intents.
- [x] 3.7 Return warnings and RTGS alternatives for ACH batch recommendations with high maximum single amount or finality conflicts.
- [x] 3.8 Return missing `maxSingleAmount` warning for multiple-payment intents that omit it.
- [x] 3.9 Add unit tests for each recommendation rule, precedence path, warning path, unsupported path, confidence level, matched factors, and no numeric score.

## 4. Recommendation API Flow

- [ ] 4.1 Add application service to orchestrate authorization context, correlation ID, validation result, rule evaluation, response mapping, and observability.
- [ ] 4.2 Add controller route for `POST /v1/payment-rail-recommendations`.
- [ ] 4.3 Ensure recommendation requests do not create domestic payment, ACH batch, RTGS payment, FI payment, recall/investigation, or idempotency records.
- [ ] 4.4 Return next API guidance for RTP, ACH, RTGS, and FI correspondent supported recommendations.
- [ ] 4.5 Add integration tests for RTP, ACH, ACH warning, ACH conflict, RTGS corporate, RTGS FI, FI correspondent, cross-border unsupported, and non-USD unsupported scenarios.

## 5. Security, Correlation, And Logging

- [ ] 5.1 Add `payment-rail-recommendations:create` scope enforcement.
- [ ] 5.2 Ensure recommendation requests require JWT Bearer authentication and reject missing or insufficient scopes consistently.
- [ ] 5.3 Ensure `Idempotency-Key` is not required and does not create or evaluate idempotency records when supplied.
- [ ] 5.4 Propagate `X-Correlation-ID` through response headers, response body, logs, and rule evaluation context.
- [ ] 5.5 Mask or omit bearer tokens, raw request payloads, unexpected sensitive fields, and full sensitive values at logging boundaries.
- [ ] 5.6 Add security, no-idempotency, correlation, and sensitive logging tests for recommendation requests.

## 6. OpenAPI And Developer Artifacts

- [ ] 6.1 Update OpenAPI with recommendation path, request schemas, response schemas, security scope, examples, unsupported responses, validation errors, and simulator-only descriptions.
- [ ] 6.2 Update Postman collection with recommendation scenarios for RTP, ACH batch, ACH missing max single amount warning, ACH conflict with RTGS alternative, RTGS corporate, RTGS FI, FI correspondent, cross-border unsupported, non-USD unsupported, and auth failure.
- [ ] 6.3 Update Postman local environment and token guidance for `payment-rail-recommendations:create`.
- [ ] 6.4 Update README and developer support documentation to describe recommendation API behavior, rule precedence, thresholds, rail and arrangement output, no-idempotency behavior, and non-goals.
- [ ] 6.5 Add or extend artifact validation tests for OpenAPI, Postman, README, and developer documentation alignment.

## 7. Verification

- [ ] 7.1 Run `npx.cmd openspec validate add-payment-rail-recommendation-api`.
- [ ] 7.2 Run focused Maven tests for recommendation domain, DTO validation, rule service, API integration, security, logging, OpenAPI, and Postman artifacts.
- [ ] 7.3 Run full `mvn test`.
- [ ] 7.4 Inspect git status and confirm only intended files changed.
