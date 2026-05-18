# Feature & Code Audit Plan

## Goal

Assess every implemented feature and its associated code to ensure:
1. The feature works as intended.
2. The code quality meets project architecture and maintainability standards.
3. Internal behavior is documented for future development and future agents.

This plan is **planning-only** and intended for review before implementation starts.

## Agent Roles

- **project-manager (lead):** orchestration, sequencing, ownership, phase gates.
- **qa-engineer:** feature verification matrix, reproduction quality, regression validation.
- **reviewer:** high-signal code quality audit and final quality gates.
- **documentation-engineer:** system maps, behavior contracts, audit docs.
- **software-engineer:** implementation owner for fixes identified by the audit.

## Preconditions

- Baseline planning docs read: `QWEN.md`, `docs/AGENT_BRIEFING.md`, `docs/TODO.md`, `docs/roadmap.md`, `docs/DOCS_INDEX.md`, `docs/DEVELOPMENT_GUARDRAILS.md`.
- Note: `docs/RECOVERY_PLAN.md` and `docs/REGRESSION_LOG.md` are currently missing and should be treated as part of this audit documentation gap.

## Workstreams

1. Feature inventory and subsystem ownership mapping.
2. Risk-based prioritization (critical paths first).
3. Feature verification (expected vs observed).
4. Code quality review (architecture + correctness + maintainability).
5. Regression triage, fix validation flow, and retest protocol.
6. Documentation package for future execution.

## Phases

| Phase | Objective | Primary owner | Exit criteria |
|---|---|---|---|
| P0 Kickoff | Approve templates, severity model, and evidence standard | project-manager + reviewer | Audit charter approved |
| P1 Inventory | Build complete list of implemented features and owners | project-manager | 100% features inventoried and assigned |
| P2 Prioritization | Rank by user impact/risk and define critical-path set | project-manager + reviewer | Prioritized queue approved |
| P3 Critical Verification | Verify high-risk/core flows first (incl. screenshot) | qa-engineer | Critical-path matrix complete |
| P4 Broad Verification | Verify remaining implemented features | qa-engineer | Full matrix complete |
| P5 Code Quality Audit | Evaluate architecture contracts and logic quality | reviewer | Findings list with severity/owners |
| P6 Triage & Re-Verification | Assign fixes and define retest requirements | project-manager + qa-engineer + reviewer | Triage board stabilized |
| P7 Documentation Closure | Publish internal system/behavior docs and audit outcomes | documentation-engineer | Documentation package approved |
| P8 Final Gate | Decide clean/verified status and next execution tasks | reviewer + project-manager | Go/No-Go + roadmap/todo updates |

## Feature Inventory Approach

For each feature, capture:
- Feature name and subsystem area.
- Entrypoint(s) (UI, command, event, system).
- Expected behavior contract.
- Dependencies (systems/services/assets/persistence).
- Risk tier (P0/P1/P2/P3).
- Verification method and evidence type.
- Owner.

Source triangulation for inventory:
1. `docs/CHANGELOG.md` (implemented history),
2. DI/wiring and registrations (Koin, windows, systems, handlers),
3. code topology under `src/main/kotlin`.

## Risk-Based Prioritization

Prioritize using impact + likelihood + detectability:
- **P0 Critical:** crash/data loss/core workflow break.
- **P1 High:** major feature wrong behavior/no reliable workaround.
- **P2 Medium:** degraded behavior with workaround.
- **P3 Low:** minor UX/non-blocking issues.

Critical path baseline:
- Boot/load flow
- Project open/save/switch
- Scene open/save/load
- Viewport render/update
- Input and play/edit transitions
- Undo/redo mutation integrity
- Filesystem/project browser actions
- Screenshot capture/export

## QA Verification Framework

Each feature gets an **Expected vs Observed** record:
- Preconditions
- Repro steps
- Expected result
- Actual result
- Repro rate
- Severity
- Owner
- Artifacts (logs/screens/video)
- Status (Pass/Fail/Blocked/Partial)

Matrix dimensions:
- Mode: Edit/Play
- Lifecycle: create/open/modify/save/reload/close
- Inputs: keyboard/mouse/gamepad/drag-drop
- Persistence: scene/project/settings/assets
- Event/command flow: UI -> Event -> Handler -> Command -> Undo/Redo

## Screenshot Feature Targeted Checklist

1. Trigger path works once per action (UI/keybind).
2. Correct framebuffer and dimensions are used.
3. Pixel readback succeeds (no GL errors).
4. Output image orientation/content is valid.
5. Output file creation, naming, and path handling are correct.
6. Rapid repeated captures do not race/corrupt output.
7. Behavior remains correct across resize and play/edit transitions.
8. Failure paths show actionable feedback.

## Reviewer Code Quality Framework

High-signal checks only:
1. Architecture boundary violations (especially engine/editor layering).
2. Mutation pipeline violations (direct state mutation bypassing command flow).
3. DI hygiene issues (manual singleton/service-locator anti-patterns).
4. Undo/redo symmetry and command lifecycle risks.
5. Dead code/duplicate paths left from refactors.
6. Null-safety and logic correctness risks.
7. Performance risks in update/render hot paths.

## Triage Protocol

For every finding:
1. Repro + expected/actual + severity.
2. Assign owner and target scope.
3. Define verification checklist (targeted + adjacent regression checks).
4. Fix is not closed until QA verification passes.
5. Reviewer confirms no architectural regression introduced by fix.

## Documentation Deliverables

1. Feature inventory + ownership map.
2. Behavior contract catalog (expected behavior by feature).
3. QA verification matrix with evidence links.
4. Active regression log (new doc to track findings lifecycle).
5. Final audit summary with residual risk register.

## Checkpoints & Cadence

- Daily: triage/finding count, blockers, owner progress.
- Phase gates: end of P1, P3, P5, P7.
- Final gate: P8 go/no-go with explicit unresolved risk list (if any).

## Acceptance Criteria for Audit Completion

Audit is complete only when:
1. All implemented features are inventoried and owned.
2. All features have expected-vs-observed verification records.
3. Critical path is verified first, including screenshot flow.
4. Code quality findings are severity-tagged and assigned.
5. Regression lifecycle process is active and reproducible.
6. Documentation package is published for future agents.
7. `docs/roadmap.md` and `docs/TODO.md` reflect post-audit priorities.
