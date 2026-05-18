# Agent Briefing

Execution contract for contributors and agents working in SkateSim.

## Required Read Order

1. `QWEN.md`
2. `docs/DOCS_INDEX.md`
3. `docs/DEVELOPMENT_GUARDRAILS.md`
4. Task-specific subsystem docs

## Non-Negotiable Execution Contract

1. Preserve architecture contracts from ADR/ECS docs.
2. Use event-driven editor mutation flow (no direct UI mutation).
3. Keep engine/editor layering clean (`engine/**` does not import `editor/**`).
4. Use command semantics correctly (`UNDOABLE` / `EXECUTE_ONLY` / `ASYNC`).
5. Keep user-facing strings localized.
6. Planning contract: roadmap defines what is next; TODO defines how to execute.

## Canonical References

- Documentation navigation: `docs/DOCS_INDEX.md`
- Development guardrails: `docs/DEVELOPMENT_GUARDRAILS.md`
- Core contracts ADR: `docs/ADR-ARCH-002-core-contracts.md`
- ECS architecture: `docs/ECS_ARCHITECTURE.md`
