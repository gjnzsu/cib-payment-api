# Payment Simulation Suite Vision

## Purpose

This document captures the long-term product direction for turning `cib-payment-api` into a reusable payment simulation capability and positioning it alongside `baas-api-sandbox` as part of a broader Payment API product validation suite.

The goal is not to build a production payment rail. The goal is to provide a structured, executable environment where product owners, business analysts, solution architects, engineers, and client-facing teams can validate payment API product decisions before committing to full delivery.

## Problem

Payment API product teams often lack a safe product validation platform. Teams can usually write API endpoints and mock responses, but they struggle to validate whether the product design is complete enough for real client and market needs.

Common gaps include:

- unclear scope between MVP and later phases
- weak coverage of exception paths
- inconsistent idempotency, status, and error semantics
- limited visibility into payment lifecycle states
- insufficient FI, correspondent banking, and settlement context
- fragmented OpenAPI, Postman, UAT, and product requirements artifacts
- lack of reusable scenarios for new markets, rails, and client segments

This suite should help teams test product thinking, not just HTTP behavior.

## Product Asset Map

The long-term product should be composed of three complementary assets.

```text
baas-api-sandbox
  Partner developer onboarding, UAT runbook, portal, scenario execution,
  client context, mock OAuth, and lightweight evidence capture.

cib-payment-api
  Payment capability simulator, ISO 20022 message handling, payment engine,
  clearing and settlement simulation, FI/correspondent flows, and lifecycle
  status/reporting behavior.

payment-simulation-agentic-skill
  AI-assisted workflows that turn payment product ideas into scenarios,
  API contracts, acceptance criteria, OpenSpec proposals, Postman collections,
  and validation reports.
```

## Repository Responsibilities

### `baas-api-sandbox`

`baas-api-sandbox` should represent the client-facing enablement layer.

It owns:

- partner developer onboarding
- UAT runbook experience
- scenario catalog presentation
- mock OAuth and requester context
- BaaS-style on-behalf-of customer context
- lightweight evidence capture
- developer journey and portal UX
- simple partner-facing API examples

It should answer:

- Can a partner understand how to integrate?
- Can a partner run required UAT scenarios?
- Can client-facing teams explain the API behavior clearly?
- Can the product team show positive, negative, and boundary scenarios?

### `cib-payment-api`

`cib-payment-api` should represent the payment capability layer.

It owns:

- payment initiation and status APIs
- authentication, authorization, idempotency, and correlation controls
- ISO 20022 payment initiation and reporting behavior
- payment engine ownership of lifecycle state
- domestic realtime payment simulation
- FI and correspondent payment simulation
- clearing and settlement simulator behavior
- sensitive data masking and observability patterns
- OpenAPI, Postman, and contract-aligned developer artifacts

It should answer:

- Is the payment product behavior coherent?
- Are lifecycle states, idempotency, and status reports clear?
- Can the team model market-specific payment constraints?
- Can the team simulate FI/correspondent banking flows safely?
- Can exception paths be explained and tested before real rail integration?

### Future Agentic Skill

The future skill should represent the assisted product validation layer.

It owns:

- scenario generation from product intent
- API scope challenge and MVP slicing
- payment lifecycle modelling
- ISO 20022 message family guidance
- exception and investigation checklist generation
- OpenAPI and Postman artifact suggestions
- acceptance criteria and user story drafting
- validation reports for missing paths and unclear requirements

It should answer:

- What scenarios should this payment API support?
- What is the smallest useful MVP?
- What product risks or missing cases should be discussed?
- Which artifacts should be produced for engineering and client onboarding?

## Recommended Collaboration Model

The recommended model is:

```text
Sandbox as client, Payment API as capability provider.
```

```text
Partner / Fintech Developer
        |
        v
baas-api-sandbox
  - portal
  - scenario catalog
  - mock OAuth and partner context
  - UAT evidence
  - partner-facing API workflow
        |
        | calls or simulates calls to
        v
cib-payment-api
  - payment admission
  - idempotency
  - ISO mapping
  - payment engine
  - domestic / FI / correspondent simulation
  - status and reporting
```

This keeps the two repositories from competing with each other. The sandbox focuses on the client journey. The payment API focuses on product capability depth.

## Integration Options

### Option A: Loose Product Alignment

This is the recommended short-term approach.

The two repositories do not need direct runtime integration. Instead, they share product language, scenario names, roadmap intent, and documentation.

Examples:

- `baas-api-sandbox` includes a future scenario pack for payment capability onboarding.
- `cib-payment-api` documents simulator scenarios that can be referenced by the sandbox.
- Both repositories describe how partner onboarding maps to payment capability validation.

This is fast, low-risk, and strong enough for product interviews and roadmap discussion.

For the FI correspondent RFI workflow, `baas-api-sandbox` remains a future scenario-pack integration, not part of this runtime change. The current runtime change stays inside `cib-payment-api`: FI payment endpoints, simulator outcomes, OpenAPI, Postman, and developer support artifacts.

### Option B: Sandbox Proxy / Orchestration

This is a useful medium-term technical demonstration.

`baas-api-sandbox` can accept a partner-facing JSON request or portal action, transform it into the relevant `cib-payment-api` request, call the payment simulator, and store the evidence in the sandbox portal.

Example:

```text
POST /api/v1/payments
  partner-facing JSON
      -> sandbox maps requester context
      -> sandbox calls cib-payment-api
      -> sandbox records status, events, and evidence
```

This gives a stronger end-to-end demo but introduces cross-service configuration, token mapping, contract versioning, and error translation.

### Option C: Shared Product Platform

This is a long-term option only.

The suite could later extract shared scenario definitions, market profiles, identity mocks, evidence schemas, and reusable contract templates. This should wait until repeated use proves which abstractions are stable.

## FI and Correspondent Banking Direction

The next meaningful product enhancement for `cib-payment-api` should focus on FI and correspondent banking payment simulation rather than adding more domestic payment variants.

The product scope should cover:

- FI client profile and entitlements
- correspondent bank routing
- debtor FI, creditor FI, intermediary FI, and beneficiary bank roles
- nostro, vostro, and loro account concepts
- FI-to-FI payment lifecycle states
- RFI, recall, and investigation lifecycle
- ISO 20022 message mapping at product level
- tracking and reporting expectations

Useful account vocabulary:

- Nostro account: our account held with another bank.
- Vostro account: another bank's account held with us.
- Loro account: a third bank's account held with another bank, viewed from the observing bank's perspective.

The product point is that FI payment APIs need more than a payment instruction. They need a clear model for where funds sit, which correspondent relationship is used, who owns the investigation, and how lifecycle status is communicated.

## RFI, Recall, and Investigation Lifecycle

The RFI and investigation capability should start as a product lifecycle model before becoming a full case management implementation.

A simplified lifecycle:

```text
FI payment initiated
  |
  | pacs.009 or pacs.008
  v
Payment processing
  |
  +-- success -> credited / settled
  |
  +-- exception -> RFI required / pending investigation
  |
  +-- recall needed
        |
        | camt.056
        v
     cancellation or recall request
        |
        | camt.029
        v
     resolution: accepted / rejected / pending
```

For the first product slice:

- `camt.056` should be treated as a structured FI-to-FI cancellation or recall request.
- `camt.029` should be treated as the resolution response to that investigation or cancellation request.
- The simulator should model accepted, rejected, and pending investigation outcomes.
- The API should avoid claiming real SWIFT, CBPR+, scheme, or regulatory certification.

Out of scope for the first slice:

- real SWIFT connectivity
- full CBPR+ certification
- real investigation case orchestration
- real sanctions, AML, fraud, or compliance decisions
- full `camt.110` and `camt.111` case management
- real nostro ledger or settlement accounting
- FX conversion
- webhook delivery

## Agentic Skill Vision

The future agentic skill should package this suite as a repeatable payment product validation workflow.

Input examples:

- "Design an HK FPS payment API scenario pack."
- "Create a correspondent banking payment simulation for FI clients."
- "Validate whether our API scope covers RFI and recall handling."
- "Generate OpenAPI and Postman scenarios for marketplace settlement."
- "Compare domestic RTP, FI payment, and wallet top-up lifecycle states."

Expected outputs:

- payment journey map
- actor and account model
- scenario catalog
- API capability map
- lifecycle state machine
- ISO message touchpoint summary
- exception path checklist
- acceptance criteria
- OpenSpec proposal outline
- Postman scenario recommendations
- validation report with missing product decisions

The skill should assist product validation. It should not replace payment SMEs, compliance review, architecture governance, or final product ownership.

## Roadmap

### Now

- Keep `cib-payment-api` focused on payment simulation depth.
- Keep `baas-api-sandbox` focused on partner onboarding and UAT experience.
- Document the relationship between the two repositories.
- Use the existing HK ISO 20022 simulation as the first payment capability proof point.

### Next

- Define FI/correspondent payment enhancement scope for `cib-payment-api`.
- Model nostro, vostro, and loro account roles.
- Add a product-level RFI and recall lifecycle using `camt.056` and `camt.029`.
- Improve external-facing API error diagnostics so partner platform developers can distinguish XML format failures, supported-profile validation failures, semantic payment constraints, authorization failures, idempotency conflicts, and recall eligibility issues without needing internal clarification.
- Define scenario packs that can later be surfaced in `baas-api-sandbox`.

The error diagnostics enhancement should preserve stable JSON error envelopes and avoid exposing raw XML, account identifiers, bearer tokens, or sensitive payloads. The product goal is to make negative-path integration self-explanatory through safe `details` entries, aligned OpenAPI examples, Postman expected results, and artifact validation tests.

### Later

- Add sandbox-to-payment-simulator runtime orchestration.
- Introduce reusable market profiles and rail profiles.
- Add client-facing scenario packs for FI, PSP, and corporate payment clients.
- Generate validation reports from scenario coverage.

### Long Term

- Package the suite as an AI-assisted Payment API Product Validation Suite.
- Make it reusable for other banks, rails, markets, and client segments.
- Support repeatable product discovery, scope validation, scenario simulation, and developer onboarding artifacts.

## Interview Narrative

A concise way to explain this product direction:

> I separated the payment API experiment into two complementary product assets. `baas-api-sandbox` represents the partner onboarding and UAT experience, while `cib-payment-api` represents the payment capability simulator with ISO 20022, payment lifecycle, and FI/correspondent banking depth. The long-term vision is to turn these into an AI-assisted Payment API Product Validation Suite, so product teams can validate scenarios, API contracts, exception handling, and client onboarding journeys before investing in full production delivery.

This frames the work as API product management, not only backend implementation.
