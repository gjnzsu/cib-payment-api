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
   - Use `openspec-explore` when the idea, product goal, or API behavior is still being shaped.
   - Clarify the problem, users, success criteria, MVP boundaries, out-of-scope items, risks, and open questions.
   - Capture mature decisions in OpenSpec artifacts rather than leaving them only in conversation.

2. **Create OpenSpec artifacts**
   - Use `openspec-propose` once the change is ready to formalize.
   - Keep artifact responsibilities clear:
     - `proposal.md`: why, what, success criteria, scope.
     - `design.md`: high-level architecture, major design choices, tradeoffs.
     - `spec.md`: required behavior, scenarios, acceptance criteria.
     - `tasks.md`: implementation checklist and verification work.

3. **Refine contract before coding**
   - For API features, resolve behavior-level contract questions before implementation.
   - Define endpoints, headers, required request fields, response shapes, error behavior, idempotency behavior, auth behavior, and sandbox/mock behavior in OpenSpec.
   - Keep exact machine-readable schema details in OpenAPI, but do not leave product decisions unresolved for implementation.

4. **Create the Superpowers implementation plan**
   - Use `superpowers:writing-plans` after OpenSpec tasks exist.
   - Treat OpenSpec `tasks.md` as the master checklist.
   - The plan may decompose tasks into implementation slices, but it must not introduce new product behavior.
   - Add an OpenSpec traceability matrix before coding.

5. **Check OpenSpec to Superpowers alignment**
   - Confirm every Superpowers plan task maps to one or more OpenSpec tasks.
   - Confirm every OpenSpec task is covered by implementation work or explicitly deferred.
   - If the plan discovers missing behavior, update OpenSpec first, then revise the plan.

6. **Prepare an isolated workspace**
   - Use `superpowers:using-git-worktrees` for implementation branches that should not disturb `main`.
   - Verify baseline setup and tests before starting if practical.

7. **Implement with OpenSpec apply**
   - Use `openspec-apply-change` as the task tracker.
   - Read the proposal, design, specs, and tasks before implementation.
   - Mark OpenSpec tasks complete only after the related code, artifacts, and tests exist.

8. **Use Superpowers engineering discipline**
   - Use `superpowers:test-driven-development` for behavior changes.
   - Use `superpowers:systematic-debugging` when tests, manual testing, or integration behavior is surprising.
   - Use `superpowers:verification-before-completion` before claiming success, committing, pushing, or opening a PR.

9. **Run developer experience validation**
   - For API features, validate local docs, OpenAPI rendering, Postman collections, local tokens, mock scenarios, and common error flows.
   - Treat broken or confusing developer support artifacts as implementation defects.

10. **Commit, push, review, and merge**
    - Commit only after fresh verification evidence.
    - Push the feature branch and review the PR.
    - Merge after implementation, tests, docs, and product artifacts agree.

11. **Archive OpenSpec after merge**
    - Pull merged `main`.
    - Archive the OpenSpec change.
    - Validate OpenSpec and run the test suite.
    - Commit and push the archive result.

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

## Payment API Journey Lessons

The first Domestic RTP Payment Service API journey confirmed these practices:

- Contract refinement before coding prevented ambiguity around headers, idempotency, mock scenarios, and response shape.
- OpenSpec task tracking kept implementation anchored to the agreed scope.
- Superpowers TDD and verification prevented unproven completion claims.
- Systematic debugging isolated a Postman inheritance issue from API behavior.
- Manual Postman testing was valuable enough to add hardening tasks and regression tests.
- The traceability matrix made it clear that implementation refinements did not become uncontrolled product drift.
