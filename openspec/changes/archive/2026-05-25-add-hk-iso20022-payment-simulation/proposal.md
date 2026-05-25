## Why

The current Domestic RTP Payment Service API validates JSON payment instructions and uses a simple downstream mock, but it does not exercise ISO 20022 message initiation, payment-engine lifecycle ownership, status reporting, or clearing and settlement behavior. This change creates a controlled HK domestic realtime payment experiment that demonstrates an ISO-native flow: external `pain.001` initiation, payment-engine record ownership, internal `pacs.008` mapping, HKICL/FPS-style clearing and settlement simulation, and `pain.002` customer payment status reporting.

## What Changes

- **BREAKING** Replace the external custom JSON payment initiation format with ISO 20022 `pain.001` XML on the domestic payment creation resource.
- Return ISO 20022 `pain.002` XML for successfully processed ISO initiation requests, including `ACSC`, `RJCT`, or `PDNG` transaction status depending on simulated payment outcome.
- Keep `pacs.008` internal to the payment engine and clearing simulator boundary; it is not exposed as an external API input in this change.
- Introduce a payment-engine capability that owns payment records, validates accepted payment candidates, maps customer credit transfer initiation into an internal interbank transfer model, produces internal `pacs.008` representation, and generates `pain.002` status reports.
- Introduce an HKICL/FPS-style HKD-only clearing and settlement simulator with deterministic settlement success, business rejection, suspicious proxy or account rejection, pending/downstream instability, timeout, and internal failure outcomes.
- Add pragmatic HK regulatory-adjacent admission validation for beneficiary account or FPS proxy, payment reference or client-supplied end-to-end identifier, optional purpose or category purpose, and sensitive data masking.
- Keep Edge API responsibilities limited to external channel controls: authentication, authorization, content negotiation, request admissibility, idempotency link records, correlation, and response mapping. The Edge API is not the payment record system of record.
- Use HTTP validation errors only for request admissibility failures, such as malformed XML, missing mandatory message fields, missing beneficiary account or FPS proxy, missing payment reference or client-supplied end-to-end identifier, unsupported country or currency profile, authentication/authorization failure, missing idempotency key, or idempotency conflict. These failures do not create a payment record and do not produce `pain.002`.
- Represent business or scheme-level outcomes for valid payment candidates as `pain.002`, including settlement success, rejection, and pending/downstream instability.
- Remove custom JSON payment initiation from the supported v1 experiment path; payment initiation clients must submit supported `pain.001` XML.

## Capabilities

### New Capabilities
- `iso20022-payment-initiation`: External ISO 20022 `pain.001` payment initiation contract, supported HK profile, XML validation behavior, and response/error expectations.
- `hk-payment-engine-mapping`: Payment-engine orchestration and internal mapping from accepted `pain.001` initiation into an internal `pacs.008` interbank transfer representation.
- `iso20022-payment-status-reporting`: ISO 20022 `pain.002` customer payment status report generation and external response behavior for `ACSC`, `RJCT`, and `PDNG` outcomes.
- `hk-clearing-settlement-simulation`: HKD-only HKICL/FPS-style behavioral simulation for participant routing, clearing, settlement, and deterministic simulator outcomes.
- `hk-payment-regulatory-profile`: Pragmatic HK payment profile rules for beneficiary account or FPS proxy, payment identifiers, optional purpose fields, suspicious proxy/account simulation, and sensitive data handling.

### Modified Capabilities
- `domestic-rtp-payments`: Domestic payment creation replaces the existing JSON business payload with supported `pain.001` XML, returns ISO `pain.002` for payment outcomes, and queries latest ISO status reports through the Edge API facade.
- `payment-authentication-and-idempotency`: Idempotency fingerprinting, correlation propagation, original response replay, engine payment ID linking, and sensitive logging requirements apply to XML payment initiation and HK regulatory-adjacent fields.
- `payment-developer-support`: OpenAPI, Postman, and developer examples include the HK ISO 20022 simulation scenarios.

## Impact

- API layer: content negotiation for `POST /v1/domestic-payments` and status query, XML request parsing, XML `pain.002` response mapping, OpenAPI examples, and consistent error mapping for request admissibility failures.
- Application layer: Edge idempotency link handling, payment-engine orchestration, ISO profile validation, normalized XML fingerprinting, engine payment record ownership, status report generation, and status query delegation to the engine.
- Domain layer: supported ISO initiation concepts, payment-engine records, internal interbank transfer representation, `pain.002` status report model, participant/proxy concepts, and payment outcome reasons.
- Infrastructure layer: secure XML parsing/rendering, HKICL/FPS-style simulator adapter, internal `pacs.008` representation generation, deterministic mock scenarios, observability, and masking.
- Developer support: Postman requests, sample `pain.001` and `pain.002` payloads, documentation, and focused contract/integration tests.
