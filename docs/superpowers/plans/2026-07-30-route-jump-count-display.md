# Route Jump Count Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display true route jump counts and number route systems from zero.

**Architecture:** Centralize heading text in `RouteGeometry`/`RouteTabPanel` and reuse it across every successful route-loading path. Keep display indexes as derived route-layout data and preserve blank indexes for synthetic/detail rows.

**Tech Stack:** Java 21, Swing, JUnit Jupiter, Maven

## Global Constraints

- Count real system-to-system legs, not visible table rows.
- Keep body/station and synthetic rows unnumbered.
- Preserve current empty/error messages and partial-add suffixes.

---

### Task 1: Zero-Based Route Presentation

**Files:**
- Modify: `src/test/java/org/dce/ed/route/RouteGeometryTest.java`
- Modify: `src/test/java/org/dce/ed/RouteTabPanelHelperTest.java`
- Modify: `src/main/java/org/dce/ed/route/RouteGeometry.java`
- Modify: `src/main/java/org/dce/ed/RouteTabPanel.java`

**Interfaces:**
- Produces: `RouteGeometry.realSystemCount(List<RouteEntry>)`
- Produces: `RouteTabPanel.routeJumpHeader(List<RouteEntry>)`

- [ ] Add failing tests asserting indexes `0, 1, 2`, a blank body-row index, and `Route: 2 jumps`.
- [ ] Run `mvn '-Dtest=RouteGeometryTest,RouteTabPanelHelperTest' test` and confirm the old one-based/system-count behavior fails.
- [ ] Start `RouteGeometry.renumberDisplayIndexes` at zero and add a real-system counter.
- [ ] Add the shared header formatter and replace every successful `Route: x systems` construction.
- [ ] Run the focused tests and confirm they pass.
- [ ] Run `mvn test` and review `git diff --check`.
