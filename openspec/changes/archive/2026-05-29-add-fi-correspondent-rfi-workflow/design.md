## Context

The service currently supports ISO-native domestic payment simulation through `POST /v1/domestic-payments` using `pain.001.001.09`, internal payment-engine ownership, internal `pacs.008` mapping, deterministic HK clearing and settlement simulation, and `pain.002.001.10` status reporting.

The next product slice targets FI and correspondent banking product validation. The first executable scope is intentionally narrow: a USD-only FI payment resource that accepts a supported `pacs.009` profile for an FI-to-FI customer credit transfer cover/interbank leg, records simulated correspondent settlement context between banks, and supports a deterministic recall/cancellation investigation workflow using `camt.056` requests and `camt.029` resolution responses.

This change supports the broader payment simulation suite vision while keeping `baas-api-sandbox` out of runtime scope. The FI scenarios can later be exposed through the sandbox as onboarding and UAT scenario packs.

## Goals / Non-Goals

**Goals:**

- Add a distinct FI payment API surface under `/v1/fi-payments`.
- Accept a supported minimal `pacs.009` XML profile for one USD FI-to-FI cover/interbank-leg payment per request.
- Keep FI payment records separate from domestic payment records.
- Capture FI parties and simulated correspondent settlement context, including `NOSTRO`, `VOSTRO`, or `LORO` account relationship role.
- Require FI-specific scopes for create, read, and investigation operations.
- Add FI payment status query with latest payment and investigation state.
- Add a supported `camt.056` recall/investigation request path for an existing FI payment.
- Return deterministic `camt.029` XML resolution responses for recall accepted, recall rejected, and investigation pending outcomes.
- Preserve idempotency, ownership checks, correlation propagation, JSON error envelopes, observability, and sensitive data masking.
- Provide OpenAPI, Postman, synthetic fixtures, and developer documentation for the supported FI scenarios.

**Non-Goals:**

- Full ISO 20022, CBPR+, or SWIFT validation.
- Real SWIFT connectivity or correspondent banking network integration.
- Real nostro/vostro/loro ledger accounting, balance checks, debit/credit postings, or reconciliation statements.
- Non-USD, multi-currency, FX, or multi-market behavior.
- Sanctions, AML, fraud, compliance decisioning, or operational approval queues.
- Batch payments, full cover payment chain orchestration, cancellation of domestic payments, webhooks, callbacks, or full investigation case management.
- `camt.110`, `camt.111`, MT199 fallback, or multi-message RFI conversations.
- Runtime integration with `baas-api-sandbox`.

## Decisions

1. **Create a separate FI payment resource namespace.**
   - Decision: add `/v1/fi-payments`, `/v1/fi-payments/{paymentId}`, and `/v1/fi-payments/{paymentId}/recall-requests`.
   - Rationale: FI and correspondent banking behavior has different actors, scopes, ISO messages, and lifecycle semantics from domestic customer payment initiation.
   - Alternative considered: attach recall requests to `/v1/domestic-payments/{paymentId}`. That would be faster but would blur product boundaries and imply that `camt.056` investigation is a domestic customer payment extension.

2. **Use `pacs.009` XML first, with a narrow supported profile.**
   - Decision: FI payment creation accepts supported `pacs.009` XML for one USD FI-to-FI customer credit transfer cover/interbank leg and simulated correspondent settlement between banks.
   - Rationale: this anchors the feature in FI-to-FI/correspondent banking product language, demonstrates the interbank leg behind a customer credit transfer, and aligns with the intended product interview narrative.
   - Alternative considered: JSON-first FI payment creation. That would be easier to implement but would weaken the ISO 20022 and FI payment product signal.

3. **Return JSON for FI payment creation and status, but XML for recall resolution.**
   - Decision: `POST /v1/fi-payments` and `GET /v1/fi-payments/{paymentId}` return JSON; `POST /v1/fi-payments/{paymentId}/recall-requests` returns supported `camt.029` XML.
   - Rationale: the first FI slice should highlight the `camt.056` to `camt.029` workflow without introducing another FI ISO status report response. JSON status also makes settlement context and investigation summary easy to inspect in Postman.
   - Alternative considered: return ISO XML for all FI responses. That is more message-native but increases first-slice complexity.

4. **Model nostro/vostro/loro as settlement context, not ledger.**
   - Decision: FI payment records store account relationship role and masked simulated account reference for routing and investigation explanation only. The client does not submit `NOSTRO`, `VOSTRO`, or `LORO` as a business field; the simulator derives the account relationship role from the supported correspondent route profile.
   - Rationale: the product needs to demonstrate correspondent banking reasoning without pretending to perform production accounting.
   - Alternative considered: allow simulator-only role hints in XML or headers. That would make fixtures easier to control but would pollute the FI payment contract with non-standard role inputs. Another alternative was to introduce a ledger-like account store, which would expand scope into balances, postings, reconciliation, and operational controls.

5. **Keep recall/investigation attached to an existing FI payment.**
   - Decision: `camt.056` requests must reference the original FI payment and be submitted through the payment-specific recall resource. The MVP allows one recall/cancellation investigation record per FI payment; idempotent duplicate requests replay the original response, while different recall semantics for the same payment are rejected. Recall requests are allowed for `SETTLED` and `PROCESSING` FI payments and rejected for `REJECTED` FI payments.
   - Rationale: this keeps the workflow traceable and avoids creating a standalone case management product before the payment lifecycle exists.
   - Alternative considered: a generic `/v1/payment-investigations` resource. That may be useful later, but is too abstract for the first executable slice.

6. **Use FI-specific authorization scopes.**
   - Decision: require `fi-payments:create`, `fi-payments:read`, and `fi-payments:investigate`.
   - Rationale: FI payment initiation and investigation are distinct entitlements from domestic payment create/read behavior.
   - Alternative considered: reuse `payments:create` and `payments:read`. That would reduce security changes but would hide FI-specific product governance.

7. **Use deterministic simulator outcomes controlled by local/test-only scenarios.**
   - Decision: support simulator outcomes for accepted, unsupported correspondent rejection, correspondent review pending, recall accepted, recall rejected, and investigation pending.
   - Rationale: deterministic scenarios support repeatable product validation, automated tests, OpenAPI examples, and Postman demos.
   - Alternative considered: infer outcomes only from payment contents. That is less explicit for UAT and harder to exercise reliably.

8. **Keep `baas-api-sandbox` integration as a future scenario pack.**
   - Decision: this change updates only `cib-payment-api` artifacts and code.
   - Rationale: the payment capability simulator should mature its FI behavior first; sandbox orchestration can follow after the contract stabilizes.
   - Alternative considered: update both repositories together. That would mix payment capability design with client onboarding UX changes.

## Risks / Trade-offs

- **Risk: The supported `pacs.009`, `camt.056`, and `camt.029` profiles may be mistaken for full ISO/SWIFT compliance.** -> Mitigation: use explicit "supported profile" and "simulator-only" wording in OpenAPI, Postman, docs, fixtures, and class names.
- **Risk: Introducing FI endpoints duplicates domestic payment patterns.** -> Mitigation: reuse application patterns for auth, idempotency, correlation, error mapping, and observability while keeping FI domain records separate.
- **Risk: Nostro/vostro/loro terminology can imply real accounting.** -> Mitigation: store only masked simulated account references and document that no balances, postings, or reconciliation are performed.
- **Risk: XML parsing expands attack surface.** -> Mitigation: use secure parser settings, reject unsafe XML, avoid raw XML logging, and keep parsing in infrastructure adapters.
- **Risk: Recall/cancellation investigation scope can grow into case management.** -> Mitigation: restrict the first slice to one active `camt.056` recall/cancellation investigation per FI payment and one deterministic `camt.029` resolution per accepted recall request, with no case worker UI or multi-message thread.
- **Risk: New FI scopes may complicate local token generation and tests.** -> Mitigation: update local token generator docs, Postman environment guidance, and security tests in the same change.

## Migration Plan

- Add FI endpoints and behavior alongside existing domestic payment endpoints.
- Do not change the domestic payment API contract.
- Keep all FI records in in-memory repositories for the MVP.
- Update local JWT generation and Postman examples to include FI-specific scopes.
- Rollback is removal of the FI endpoint surface, FI repositories, FI fixtures, and FI developer artifacts without affecting domestic payment behavior.

## Open Questions

- None.
