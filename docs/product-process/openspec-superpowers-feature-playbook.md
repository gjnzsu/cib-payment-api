# OpenSpec And Superpowers Feature Playbook

This playbook captures the standard process for exploring, specifying, planning, implementing, and closing a new feature using OpenSpec and Superpowers together.

The core rule is:

```text
OpenSpec owns scope.
Superpowers owns method.
```

OpenSpec is the source of truth for product intent, behavior, design decisions, and task scope. Superpowers provides the engineering discipline for planning, isolated execution, TDD, debugging, verification, and branch completion.

## Standard Feature Journey

1. **Explore product intent**
   - Use `openspec-explore` when the idea, product goal, user workflow, system behavior, or integration behavior is still being shaped.
   - Clarify the problem, users, success criteria, MVP boundaries, out-of-scope items, risks, and open questions.
   - Capture mature decisions in OpenSpec artifacts rather than leaving them only in conversation.

2. **Create OpenSpec artifacts**
   - Use `openspec-propose` once the change is ready to formalize.
   - Keep artifact responsibilities clear:
     - `proposal.md`: why, what, success criteria, scope.
     - `design.md`: high-level architecture, major design choices, tradeoffs.
     - `spec.md`: required behavior, scenarios, acceptance criteria.
     - `tasks.md`: implementation checklist and verification work.

3. **Run neutral artifact quality gate**
   - Before implementation planning, review the OpenSpec artifacts from a neutral third-party stance.
   - Do not defend the current design; look for unresolved product decisions, hidden scope, contradictions, vague scenarios, and tasks that would force implementers to invent behavior.
   - Capture findings as confirmed decisions, product-owner questions, artifact updates, and future ideas that should not enter the current scope.
   - Update OpenSpec artifacts and re-run validation before moving forward.

4. **Refine behavior contract before coding**
   - Resolve behavior-level contract questions before implementation, regardless of feature type.
   - Define the user or system entry points, inputs, outputs, state changes, permissions, data ownership, validation behavior, error behavior, observability, and supported failure modes in OpenSpec.
   - For API or integration features, include endpoints, headers, required request fields, response shapes, idempotency behavior, auth behavior, and sandbox/mock behavior where relevant.
   - For UI, workflow, CLI, data, or platform features, include user flows, screens or commands, state transitions, access rules, migration behavior, configuration, and operator/developer experience where relevant.
   - Keep exact machine-readable schema or rendering details in the right implementation artifact, such as OpenAPI, UI designs, config schemas, database migrations, or CLI help text, but do not leave product decisions unresolved for implementation.

5. **Create the Superpowers implementation plan**
   - Use `superpowers:writing-plans` after OpenSpec tasks exist.
   - Treat OpenSpec `tasks.md` as the master checklist.
   - The plan may decompose tasks into implementation slices, but it must not introduce new product behavior.
   - Add an OpenSpec traceability matrix before coding.

6. **Check OpenSpec to Superpowers alignment**
   - Confirm every Superpowers plan task maps to one or more OpenSpec tasks.
   - Confirm every OpenSpec task is covered by implementation work or explicitly deferred.
   - If the plan discovers missing behavior, update OpenSpec first, then revise the plan.

7. **Prepare an isolated workspace**
   - Use `superpowers:using-git-worktrees` for implementation branches that should not disturb `main`.
   - Verify baseline setup and tests before starting if practical.

8. **Implement with OpenSpec apply**
   - Use `openspec-apply-change` as the task tracker.
   - Read the proposal, design, specs, and tasks before implementation.
   - Mark OpenSpec tasks complete only after the related code, artifacts, and tests exist.

9. **Use Superpowers engineering discipline**
   - Use `superpowers:test-driven-development` for behavior changes.
   - Use `superpowers:systematic-debugging` when tests, manual testing, or integration behavior is surprising.
   - Use `superpowers:verification-before-completion` before claiming success, committing, pushing, or opening a PR.

10. **Run experience validation**
   - Validate the experience that proves the feature can be understood, exercised, and supported by its intended audience.
   - For API features, validate local docs, OpenAPI rendering, Postman collections, local tokens, mock scenarios, and common error flows.
   - For UI, workflow, CLI, data, or platform features, validate the equivalent user, developer, or operator experience: UI flows, CLI usage, configuration, docs, sample data, migration notes, and observable failure states.
   - Treat broken or confusing support artifacts as implementation defects.

11. **Commit, push, review, and merge**
    - Commit only after fresh verification evidence.
    - Push the feature branch and review the PR.
    - Merge after implementation, tests, docs, and product artifacts agree.

12. **Archive OpenSpec after merge**
    - Pull merged `main`.
    - Archive the OpenSpec change.
    - Validate OpenSpec and run the test suite.
    - Commit and push the archive result.

## Neutral Artifact Quality Gate

Run this quality gate after OpenSpec artifacts exist and before creating the Superpowers implementation plan. The reviewer should temporarily step out of proposal-author mode and inspect the artifacts as a skeptical third party.

The goal is not to defend the current design. The goal is to surface unresolved product decisions, hidden scope, contradictions, vague scenarios, task gaps, and implementation-time ambiguity while changes are still cheap.

Review the artifacts in this order:

1. **Review `design.md`**
   - Are product and architecture boundaries clear?
   - Are major decisions explicit enough to guide implementation?
   - Are alternatives and tradeoffs captured where they matter?
   - Are non-goals strong enough to prevent scope expansion?
   - Are any "Open Questions: None" claims premature?

2. **Review `specs/**/*.md`**
   - Is every requirement testable?
   - Are scenarios deterministic?
   - Are state transitions and outcome mappings explicit?
   - Are validation failures distinguished from accepted business outcomes?
   - Are ownership, authorization, idempotency, correlation, error, and observability behaviors covered when relevant?
   - Are mock-only or local-only controls clearly separated from production semantics?

3. **Review `tasks.md`**
   - Does every important spec behavior map to an implementation task?
   - Are tasks small enough to execute and verify?
   - Are test, documentation, artifact, migration, and verification tasks explicit?
   - Are large integration tasks split into manageable slices?
   - Could an implementer complete the tasks without inventing product behavior?

Capture review findings as:

- **Confirmed decisions**: decisions that are clear and need no change.
- **Product-owner questions**: decisions that need explicit input before implementation.
- **Artifact updates**: proposal, design, spec, or task edits needed before planning.
- **Future ideas**: valuable ideas that should be parked for later changes instead of expanding the active scope.

After applying artifact updates, re-run:

```powershell
npx.cmd openspec validate <change-name>
```

Do not create the Superpowers implementation plan until this quality gate passes.

## Neutral Review Checklist

- [ ] Design decisions are explicit enough that implementation will not invent product behavior.
- [ ] Product, technical, data, ownership, and integration boundaries are clear.
- [ ] Specs distinguish invalid inputs, invalid actions, or invalid states from accepted business outcomes.
- [ ] State mappings, lifecycle transitions, and simulator or workflow outcomes are deterministic.
- [ ] Ownership, authorization, idempotency, correlation, error handling, and observability are covered where relevant.
- [ ] Mock-only, local-only, or simulator-only controls are documented as non-production behavior.
- [ ] Non-goals are strong enough to prevent scope expansion.
- [ ] Tasks cover every important spec behavior.
- [ ] Tasks include tests, documentation, developer/user artifacts, and verification work.
- [ ] Ambiguous items were resolved with product-owner input or recorded as open questions.
- [ ] Future ideas were parked instead of silently expanding the active change.

## Alignment Review Checkpoints

Run an OpenSpec to Superpowers alignment review at three points:

1. **Pre-implementation**
   - Ensure the Superpowers plan is a translation of OpenSpec tasks, not a second source of feature scope.

2. **Mid-implementation**
   - When new work appears, classify it:
     - implementation detail: keep it in the Superpowers plan or task notes.
     - requirement/design change: update OpenSpec first.
     - future idea: park it for a later OpenSpec change.

3. **Pre-archive**
   - Confirm implementation, OpenSpec tasks, and Superpowers plan still tell the same story.
   - Archive only after this review passes.

## Alignment Review Checklist

- [ ] Every Superpowers plan task maps to one or more OpenSpec tasks.
- [ ] Every OpenSpec task is covered by implementation work or consciously deferred.
- [ ] No Superpowers task introduces new product behavior without an OpenSpec update.
- [ ] Any implementation-discovered behavior is captured back into OpenSpec before completion.
- [ ] Manual testing discoveries are fixed under existing tasks or added as new OpenSpec tasks.
- [ ] Final verification commands and results are recorded before commit or PR.
- [ ] Naming differences, artifact path changes, or scope adjustments are documented.
- [ ] OpenSpec archive happens only after the alignment review passes.

## Drift Control Rules

- If Superpowers adds behavior not present in OpenSpec, pause and update OpenSpec first.
- If OpenSpec tasks are too vague for implementation, refine OpenSpec before coding.
- If an implementation detail changes file names, artifact paths, or task grouping without changing behavior, document it as an implementation adjustment.
- If manual testing reveals missing product behavior, add or update OpenSpec tasks before marking the work complete.
- If a future enhancement appears during delivery, create a future OpenSpec change instead of expanding the active scope silently.

## Recommended Commands

Use feature-specific validation commands, but for this repository the default verification set is:

```powershell
mvn test
npx.cmd openspec validate <change-name>
git status -sb
```

For archive close-out after a merged feature:

```powershell
npx.cmd openspec archive <change-name>
npx.cmd openspec validate
mvn test
git status -sb
```

## Journey Lessons

The first Domestic RTP Payment Service API journey confirmed these practices. They are written from an API example, but the underlying lessons apply to application features more broadly:

- Behavior contract refinement before coding prevented ambiguity around headers, idempotency, mock scenarios, and response shape.
- OpenSpec task tracking kept implementation anchored to the agreed scope.
- Superpowers TDD and verification prevented unproven completion claims.
- Systematic debugging isolated a Postman inheritance issue from API behavior.
- Manual experience testing was valuable enough to add hardening tasks and regression tests.
- The traceability matrix made it clear that implementation refinements did not become uncontrolled product drift.
- Neutral artifact review before implementation planning exposed hidden product decisions while they were still cheap to fix, including message semantics, lifecycle eligibility, simulator outcome mapping, derived account context, and task granularity.
