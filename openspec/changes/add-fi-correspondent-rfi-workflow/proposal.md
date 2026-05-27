## Why

The current payment simulator demonstrates ISO-native domestic payment initiation and status reporting, but it does not yet model FI client payment journeys, correspondent banking context, or investigation workflows. This change adds a narrow USD FI/correspondent payment slice so the product can validate `pacs.009` initiation, simulated nostro/vostro/loro settlement context, and a deterministic `camt.056` to `camt.029` recall/investigation lifecycle.

## What Changes

- Add a new FI payment API surface under `/v1/fi-payments`.
- Accept a supported minimal `pacs.009` XML profile for USD-only FI payment initiation.
- Create FI payment records owned by the authenticated FI client, separate from domestic payment records.
- Add FI-specific authorization scopes:
  - `fi-payments:create`
  - `fi-payments:read`
  - `fi-payments:investigate`
- Capture simulated correspondent settlement context for FI payments, including instructing agent, instructed agent, optional correspondent/intermediary bank, settlement currency, account relationship role, and masked simulated account reference.
- Support account relationship roles `NOSTRO`, `VOSTRO`, and `LORO` as routing and investigation context only.
- Add FI payment status query with latest payment status and latest recall/investigation summary.
- Add a recall/investigation endpoint that accepts a supported `camt.056` XML request for an existing FI payment.
- Return deterministic `camt.029` XML resolution responses for recall accepted, recall rejected, and investigation pending outcomes.
- Require idempotency keys for FI payment initiation and recall/investigation requests.
- Preserve correlation IDs across FI payment records, recall records, responses, logs, and simulator calls.
- Keep `baas-api-sandbox` integration out of this repository change, while documenting that these FI scenarios can later become a sandbox scenario pack.
- Do not add real SWIFT connectivity, real CBPR+ certification, real correspondent ledger accounting, sanctions/AML/fraud decisions, webhooks, batch payments, or full investigation case management.

## Capabilities

### New Capabilities

- `fi-correspondent-payments`: FI payment initiation and status behavior for a supported USD `pacs.009` profile, including FI client ownership and correspondent routing context.
- `fi-correspondent-account-context`: Simulated nostro/vostro/loro account relationship context used for routing and investigation explanation, without real ledger accounting.
- `fi-payment-investigation-workflow`: Supported `camt.056` recall/investigation request handling and deterministic `camt.029` resolution response behavior.

### Modified Capabilities

- `payment-authentication-and-idempotency`: Add FI-specific authorization scopes, idempotency behavior for FI payment and recall requests, client ownership checks, and correlation propagation for FI records.
- `payment-developer-support`: Add OpenAPI, Postman, fixtures, and documentation for FI payment and recall/investigation scenarios.

## Impact

- API layer: new FI payment and recall endpoints, XML content negotiation, JSON status response mapping, and consistent error handling.
- Application layer: FI payment orchestration, recall/investigation orchestration, idempotency handling, ownership checks, request fingerprinting, and correlation propagation.
- Domain layer: FI payment record, correspondent settlement context, account relationship role, recall/investigation record, and investigation outcome concepts.
- Infrastructure layer: supported `pacs.009`, `camt.056`, and `camt.029` XML parsing/rendering, deterministic FI/correspondent simulator behavior, in-memory repositories, observability, and sensitive data masking.
- Security: new FI-specific scopes and tests for unauthorized create/read/investigate attempts.
- Developer support: OpenAPI 3.0.3 updates, Postman scenarios, synthetic XML fixtures, and documentation that clearly marks all FI/correspondent behavior as simulator-only.
