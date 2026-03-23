# 🛹 SkateSim Engine - TODO & Roadmap

## Current Focus: ECS Architecture Complete

The ECS architecture is now complete through v0.41 with PhysicsSystem integration.
All major systems now follow proper ECS patterns with components for state and systems for logic.

See [CHANGELOG.md](CHANGELOG.md) for complete history and [ECS_ARCHITECTURE.md](ECS_ARCHITECTURE.md) for architecture
documentation.

---

## 🔵 Future: Future Enhancements (Planned)

### Potential Future Work

- [ ] **Code Quality & Technical Debt**
  - Audit and replace remaining `!!` operators with safe calls
  - Review resource management for potential memory leaks
  - Optimize animation blending timing
  - Reduce object allocation in hot loops
  - Increase test coverage for complex systems

- [ ] **ImGui Refactor Cleanup**
  - Consolidate system UI patterns
  - Review dockable window registry for dead code

---

## End of TODO
