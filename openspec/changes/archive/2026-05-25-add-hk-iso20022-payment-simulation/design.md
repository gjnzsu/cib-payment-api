## Context

The service currently exposes a custom JSON-only domestic RTP API with JWT authentication, scope validation, idempotency, correlation IDs, in-memory payment status storage, and a simple downstream mock that maps local scenarios to terminal payment statuses. In the current MVP, the Edge API effectively creates and stores payment status records itself. For the HK ISO 20022 experiment, that boundary changes: the API becomes ISO-native for payment initiation, the Edge API remains the external channel facade, and the Payment Engine owns payment records and status truth.

This change expands scope for an HK domestic realtime payment experiment. The experiment uses ISO 20022 `pain.001` as the external customer credit transfer initiation format, maps admitted payment candidates to an internal `pacs.008` representation, sends that representation through an HKICL/FPS-style behavioral simulator, and returns ISO 20022 `pain.002` customer payment status reports as the ISO-native response payload. The design remains a simulator: it does not connect to HKICL/FPS, does not implement proprietary rulebooks, and does not provide production settlement, AML, sanctions, or fraud screening.

## Goals / Non-Goals

**Goals:**
- Accept supported HKD `pain.001` XML payment initiation through the domestic payment creation resource.
- Return `pain.002` XML for ISO-created payment outcomes: `ACSC` for settled, `RJCT` for business or scheme rejection, and `PDNG` for pending or unstable downstream outcomes.
- Remove support for custom JSON payment initiation in this experiment and make ISO `pain.001` the external initiation contract.
- Add a Payment Engine boundary that owns payment records, creates records only after Edge admission succeeds, maps `pain.001` candidates to internal `pacs.008`, calls the simulator, and generates latest `pain.002` reports.
- Add an HKD-only HKICL/FPS-style clearing and settlement simulator with deterministic success, business rejection, suspicious proxy/account rejection, processing/pending, timeout, and internal failure outcomes.
- Preserve authentication, authorization, idempotency, correlation propagation, JSON error responses for request admissibility failures, status lifecycle, and sensitive logging controls across XML and simulator flows.

**Non-Goals:**
- Real HKICL/FPS connectivity, real settlement, production scheme certification, or proprietary rulebook implementation.
- External `pacs.008` submission.
- Custom JSON payment initiation as a supported external payment creation format.
- Full ISO 20022 XSD and market-practice coverage.
- Real Scameter integration, AML, sanctions, fraud screening, balance checks, FX, cancellation, amendment, callbacks, batch, recurring, or cross-border payments.
- Treating the Edge API as the payment record system of record.
- Durable persistence or multi-replica correctness beyond the current MVP architecture.

## Decisions

1. **Make domestic payment initiation ISO-native.**
   - Decision: keep `POST /v1/domestic-payments` for payment creation, but require supported `pain.001` XML as the request body. The ISO path uses `Content-Type` and `Accept` to exchange `pain.001` and `pain.002` XML. Status query remains through the Edge API facade and returns the latest `pain.002` XML.
   - Rationale: the experiment is meant to teach payment-engine and ISO message lifecycle, so retaining the custom JSON initiation path would split the model and dilute the learning goal.
   - Alternative considered: supporting both JSON and XML through content negotiation. That preserves backward compatibility but forces the Edge API to keep two initiation contracts and complicates the engine boundary.

2. **Expose `pain.001` only; keep `pacs.008` internal.**
   - Decision: external clients submit customer credit transfer initiation using `pain.001`; the engine creates the internal interbank transfer representation.
   - Rationale: this preserves the role boundary between external corporate/client initiation and FI-to-FI clearing messages.
   - Alternative considered: allowing direct external `pacs.008`. That is useful for low-level simulator testing but conflates external client and participant roles, so it is out of v1.

3. **Keep request admission outside the Payment Engine.**
   - Decision: Edge/API admission validates XML well-formedness, secure parser constraints, supported `pain.001` message type, mandatory message fields, HKD-only currency, supported country/local profile values, beneficiary account or FPS proxy, and client-supplied payment reference or end-to-end identifier before the request becomes a payment candidate.
   - Rationale: malformed requests and unsupported profile requests are not payment outcomes. They fail with HTTP `400` or `422`, create no payment record, and produce no `pain.002`.
   - Alternative considered: converting most validation failures into `pain.002 RJCT`. That would blur the line between request admissibility and payment processing outcome.

4. **Make the Payment Engine the payment record system of record.**
   - Decision: after Edge admission succeeds, the Edge calls an in-process Payment Engine port. The Payment Engine creates the payment record, owns lifecycle state, owns latest `pain.002`, and serves status truth through a query port used by the Edge.
   - Rationale: the Edge API should not become a payment ledger or record system. It owns external channel concerns and idempotency links; the engine owns payment lifecycle.
   - Alternative considered: Edge creates the payment status record as the current MVP does. That is acceptable for the simple JSON mock but wrong for learning a payment-engine-centric flow.

5. **Introduce a payment-engine application component before the simulator.**
   - Decision: route admitted XML payment candidates through a payment-engine orchestration component rather than expanding the existing downstream mock directly.
   - Rationale: profile-admitted candidate handling, payment record creation, ISO mapping, clearing simulation, status mapping, and `pain.002` generation are separate responsibilities. The engine becomes the boundary between channel admission and internal payment-processing behavior.
   - Alternative considered: replace `MockDownstreamPaymentProcessor` with a larger mock. That would be faster but would blur ISO mapping and clearing semantics.

6. **Model HKICL/FPS behavior as a deterministic simulator.**
   - Decision: implement participant routing, clearing acceptance, and settlement outcome behavior using configured test participants and scenarios.
   - Rationale: v1 needs repeatable integration tests and educational end-to-end behavior, not full scheme fidelity.
   - Alternative considered: message-accurate or ledger-accurate simulation first. Both are valuable later but would expand v1 beyond a vertical slice.

7. **Use a pragmatic HK regulatory profile.**
   - Decision: require beneficiary account or FPS proxy, require a client-supplied payment reference or end-to-end identifier, support optional purpose/category purpose, and add suspicious proxy/account as a deterministic simulator scenario.
   - Rationale: these controls reflect important HK FPS-adjacent concerns without claiming full regulatory or bank-specific compliance.
   - Alternative considered: making purpose/category purpose mandatory. Public information does not justify treating it as universally mandatory for this experiment.

8. **Normalize XML before idempotency fingerprinting.**
   - Decision: compute idempotency fingerprints from normalized accepted business meaning, including relevant ISO identifiers and simulator scenario context, not raw XML bytes.
   - Rationale: equivalent XML formatting should not create duplicate payments, while changed payment semantics must still conflict.
   - Alternative considered: raw body hashing. It is simple but fragile for XML whitespace and namespace formatting differences.

9. **Use `pain.002` as the ISO-native response and query payload.**
   - Decision: successful ISO request processing returns HTTP `200` with `pain.002` XML. Settled payments return `ACSC`; business or scheme rejections return `RJCT`; pending/downstream instability returns `PDNG`. API/admission failures return HTTP errors and no `pain.002`.
   - Rationale: this teaches the ISO request/report lifecycle and separates transport/admission failure from payment outcome reporting.
   - Alternative considered: returning JSON `PaymentResponse` plus an `isoStatus` field. That is easier to integrate with the current API but hides the ISO status reporting concept the experiment is meant to explore.

10. **Fix ISO message versions for v1.**
   - Decision: v1 supports `pain.001.001.09` for customer credit transfer initiation fixtures and `pain.002.001.10` for customer payment status report fixtures and responses.
   - Rationale: fixed versions keep parsing, examples, OpenAPI documentation, Postman artifacts, and tests deterministic while avoiding multi-version support in the first simulation.
   - Alternative considered: version negotiation or multiple ISO versions. That would be useful for a broader gateway but would distract from the payment-engine lifecycle learning goal.

11. **Map one ISO status to multiple internal lifecycle meanings where needed.**
   - Decision: internal `COMPLETED` maps to `ACSC`, internal `REJECTED` maps to `RJCT`, and both internal `PROCESSING` and `TIMEOUT` can map to `PDNG` with different reason codes.
   - Rationale: `PDNG` is an external ISO status, while the engine still needs operational meaning. `PROCESSING` represents normal pending windows or beneficiary-bank processing; `TIMEOUT` represents unstable downstream/network conditions that may need operational intervention.
   - Alternative considered: mapping timeout to HTTP `504` or `FAILED`. That would lose the payment lifecycle distinction between API failure and pending payment outcome.

## Flow

```text
Client
  |
  | POST /v1/domestic-payments
  | Content-Type: pain.001 XML
  | Accept: pain.002 XML
  v
Edge API / Admission
  - auth and scope
  - idempotency key presence
  - secure XML parse
  - supported pain.001
  - mandatory fields
  - HKD/HK profile
  - beneficiary account or FPS proxy
  - client EndToEndId or payment reference
  |
  | failures: HTTP 400/422/401/403/409, no payment record, no pain.002
  v
Payment Engine Port
  |
  v
Payment Engine
  - create engine payment record
  - map pain.001 candidate to internal pacs.008
  - call HK simulator
  - generate latest pain.002
  |
  +-- settled -----------------> 200 + pain.002 TxSts=ACSC
  +-- business/scheme reject --> 200 + pain.002 TxSts=RJCT
  +-- pending/timeout ---------> 200 + pain.002 TxSts=PDNG + reason
```

For status query, the Edge API remains the external facade, but it queries the Payment Engine for the latest `pain.002` report rather than serving payment truth from Edge-owned records.

## Risks / Trade-offs

- **Risk: Simulator behavior may be mistaken for real HKICL/FPS compliance.** -> Mitigation: document simulator-only scope in OpenAPI, Postman, README, and class naming.
- **Risk: XML parsing increases security exposure.** -> Mitigation: disable external entity processing, reject unsupported payloads early, avoid logging raw XML, and keep XML parsing in infrastructure/API adapters.
- **Risk: ISO field coverage can grow without limit.** -> Mitigation: define a narrow supported HK `pain.001` profile and reject unsupported structures with stable validation errors.
- **Risk: Content negotiation can complicate controller logic.** -> Mitigation: keep controllers limited to routing, parsing, and response mapping; delegate business orchestration to application services.
- **Risk: Edge and engine responsibilities drift back together.** -> Mitigation: introduce `PaymentEngineInitiationPort` and `PaymentEngineStatusQueryPort`; keep payment record persistence and latest `pain.002` storage private to the engine implementation; allow Edge persistence only for idempotency link records; add architecture tests that prevent controllers or Edge services from directly accessing engine repositories.
- **Risk: Business rejection and API validation errors become inconsistent.** -> Mitigation: use HTTP errors only before payment candidate creation and `pain.002` only after engine processing begins.
- **Risk: In-memory storage limits settlement simulation realism.** -> Mitigation: keep v1 single-node and deterministic, matching existing MVP deployment constraints.

## Migration Plan

- Replace custom JSON payment initiation with supported `pain.001` XML for this experiment.
- Add in-process Payment Engine ports before moving payment record ownership behind the engine boundary.
- Add new tests and developer artifacts before broadening simulator scenarios.
- Update or remove existing JSON-focused tests, OpenAPI examples, and Postman requests so the experiment's payment initiation contract is ISO-native.
- Rollback is restoring the previous custom JSON Domestic RTP API contract and removing the ISO-native initiation/status reporting path.

## Open Questions

- None.
