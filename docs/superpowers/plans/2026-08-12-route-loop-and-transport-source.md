# Route Loop and Transport Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the remembered Custom Route Loop toggle and correct self-sourced Transport mission origins with Ardent-backed station selection.

**Architecture:** Route looping is a hidden global preference consumed by `RouteTabPanel` and an explicit wrap transition in `RouteSession`. Transport source choices live on each `MissionRecord`; a focused Ardent parser/search service isolates network JSON from a Swing dialog owned by `MissionsTabPanel`.

**Tech Stack:** Java 17, Swing, Gson, Java Preferences, JUnit 5, Maven, existing Ardent HTTP client.

## Global Constraints

- Preserve the existing uncommitted body-chevron edit in `RouteTabPanel.java`.
- Loop is global remembered state with no Preferences-dialog control.
- Loop appears left of Clear and affects custom routes only.
- Transport source selection corrects only the From display/copy value and never changes navigation.
- Use Ardent nearby exports; do not scrape Inara.
- All network work runs off the Swing event-dispatch thread.

---

### Task 1: Custom Route Loop Model and Preference

**Files:**
- Modify: `src/main/java/org/dce/ed/OverlayPreferences.java`
- Modify: `src/main/java/org/dce/ed/route/RouteSession.java`
- Modify: `src/main/java/org/dce/ed/RouteTabPanel.java`
- Test: `src/test/java/org/dce/ed/NextRouteDestinationTest.java`
- Test: `src/test/java/org/dce/ed/route/RouteSessionTest.java`

**Interfaces:**
- Produces: `OverlayPreferences.isCustomRouteLoopEnabled()`, `setCustomRouteLoopEnabled(boolean)`.
- Produces: `RouteSession.applyKnownCurrentSystem(String,long,double[],boolean)` where the Boolean enables end-to-start wrap.
- Produces: `RouteTabPanel.nextRouteDestinationSystemName(RouteSession, boolean customRouteActive, boolean loopEnabled)`.

- [ ] **Step 1: Write failing route-loop tests**

```java
assertEquals("Alpha", RouteTabPanel.nextRouteDestinationSystemName(session, true, true));
session.applyKnownCurrentSystem("Alpha", 1L, null, true);
assertEquals(0, session.getCurrentBaseIndex());
assertNull(RouteTabPanel.nextRouteDestinationSystemName(session, true, false));
```

Cover empty and one-system routes, and verify a first-system arrival does not wrap before the cursor reaches the final hop.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -Dtest=NextRouteDestinationTest,RouteSessionTest test`

Expected: compilation failure because the new overloads do not exist.

- [ ] **Step 3: Implement minimal route and preference behavior**

```java
public static boolean isCustomRouteLoopEnabled() {
    return PREFS.getBoolean(KEY_CUSTOM_ROUTE_LOOP, false);
}

public static void setCustomRouteLoopEnabled(boolean enabled) {
    PREFS.putBoolean(KEY_CUSTOM_ROUTE_LOOP, enabled);
}
```

Only return hop zero as next destination when custom route mode is active, looping is enabled, the cursor is at the final hop, and the route contains at least two systems. In the arrival overload, reset to zero only when those same completion conditions held before matching the first hop.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn -Dtest=NextRouteDestinationTest,RouteSessionTest test`

Expected: PASS.

- [ ] **Step 5: Commit the model behavior**

```text
feat: add custom route loop behavior
```

### Task 2: Custom Route Loop Toggle UI

**Files:**
- Modify: `src/main/java/org/dce/ed/RouteTabPanel.java`
- Create: `src/main/java/org/dce/ed/ui/CircularArrowIcon.java`
- Test: `src/test/java/org/dce/ed/RouteTabPanelHelperTest.java`

**Interfaces:**
- Consumes: Loop preference and route helpers from Task 1.
- Produces: a compact `JToggleButton` accessible to package tests through a narrow test accessor.

- [ ] **Step 1: Write failing panel tests**

```java
assertEquals("Loop", panel.loopButtonForTests().getToolTipText());
assertSame(panel.loopButtonForTests(), strip.getComponent(1));
assertSame(panel.clearButtonForTests(), strip.getComponent(2));
panel.loopButtonForTests().doClick();
assertTrue(OverlayPreferences.isCustomRouteLoopEnabled());
```

Save and restore the live preference in test setup/cleanup.

- [ ] **Step 2: Run and verify RED**

Run: `mvn -Dtest=RouteTabPanelHelperTest test`

Expected: compilation failure because the toggle/accessors do not exist.

- [ ] **Step 3: Implement the compact toggle and vector icon**

```java
loopCustomRouteButton = new JToggleButton(new CircularArrowIcon(14));
loopCustomRouteButton.setToolTipText("Loop");
loopCustomRouteButton.setSelected(OverlayPreferences.isCustomRouteLoopEnabled());
loopCustomRouteButton.addActionListener(e -> {
    OverlayPreferences.setCustomRouteLoopEnabled(loopCustomRouteButton.isSelected());
    refreshLoopButtonStyle();
    notifySessionStateChanged();
});
```

Add it to `customRouteWarningStrip` immediately before Clear. Paint the icon with `Arc2D` plus an arrowhead, and refresh the existing outline/selected style whenever state or UI font changes.

- [ ] **Step 4: Run and verify GREEN**

Run: `mvn -Dtest=RouteTabPanelHelperTest,NextRouteDestinationTest,RouteSessionTest test`

Expected: PASS.

- [ ] **Step 5: Commit the UI**

```text
feat: add custom route loop toggle
```

### Task 3: Self-Sourced Mission State and Persistence

**Files:**
- Modify: `src/main/java/org/dce/ed/mission/MissionRecord.java`
- Modify: `src/main/java/org/dce/ed/mission/MissionDestinationResolver.java`
- Modify: `src/main/java/org/dce/ed/mission/MissionTracker.java`
- Modify: `src/main/java/org/dce/ed/session/MissionSessionData.java`
- Test: `src/test/java/org/dce/ed/mission/MissionDestinationResolverTest.java`
- Test: `src/test/java/org/dce/ed/mission/MissionTrackerTest.java`

**Interfaces:**
- Produces: `MissionRecord.isSelfSourcedCommodityMission()`, `get/setSourcedFromSystem`, and `get/setSourcedFromStation`.
- Produces: `MissionTracker.setSourcedFrom(long missionId, String system, String station)` returning whether a mission changed.

- [ ] **Step 1: Write failing classification, resolver, merge, and persistence tests**

```java
r.setName("Mission_Sourced_Boom");
assertTrue(r.isSelfSourcedCommodityMission());
assertTrue(MissionDestinationResolver.originFor(r).isEmpty());
r.setSourcedFromSystem("Sol");
r.setSourcedFromStation("Galileo");
assertEquals("Sol — Galileo", MissionDestinationResolver.originFor(r).displayLine());
```

Round-trip the two source fields through `EdoSessionState`, and verify a later journal merge does not erase them.

- [ ] **Step 2: Run and verify RED**

Run: `mvn -Dtest=MissionDestinationResolverTest,MissionTrackerTest test`

Expected: compilation failure for missing sourced-from APIs.

- [ ] **Step 3: Implement state, mapping, and resolver behavior**

```java
public boolean isSelfSourcedCommodityMission() {
    return name != null && name.toLowerCase(Locale.ROOT).startsWith("mission_sourced");
}
```

For self-sourced missions, `originFor` returns only the explicit sourced-from fields; it must not fall back to origin or destination. Map the fields in both persistence directions and mutate them only through the tracker method.

- [ ] **Step 4: Run and verify GREEN**

Run: `mvn -Dtest=MissionDestinationResolverTest,MissionTrackerTest test`

Expected: PASS.

- [ ] **Step 5: Commit mission state**

```text
fix: distinguish sourced mission purchase origins
```

### Task 4: Ardent Nearby Source Search

**Files:**
- Create: `src/main/java/org/dce/ed/mission/CommoditySourceSearch.java`
- Create: `src/main/java/org/dce/ed/mission/CommoditySourceChoice.java`
- Test: `src/test/java/org/dce/ed/mission/CommoditySourceSearchTest.java`

**Interfaces:**
- Consumes: `ArdentClient.getNearbyExports(String,String,ArdentQueryParams)`.
- Produces: `List<CommoditySourceChoice> search(String nearSystem, String commodity, int minSupply)`.
- `CommoditySourceChoice` fields: system, station, system distance, arrival distance, price, supply, updated timestamp.

- [ ] **Step 1: Write failing parser and ordering tests with literal JSON**

```java
String json = "[{\"systemName\":\"Near\",\"stationName\":\"A\",\"distance\":2.5,"
        + "\"distanceToArrival\":450,\"buyPrice\":9234,\"stock\":500,"
        + "\"updatedAt\":\"2026-08-12T12:00:00Z\"}]";
assertEquals("A", CommoditySourceSearch.parse(json).get(0).station());
```

Cover an object-wrapped `data` array, direct arrays, missing optional values, malformed JSON, and ascending distance order.

- [ ] **Step 2: Run and verify RED**

Run: `mvn -Dtest=CommoditySourceSearchTest test`

Expected: compilation failure because the search types do not exist.

- [ ] **Step 3: Implement parser and client adapter**

```java
ArdentQueryParams params = new ArdentQueryParams()
        .minVolume(Math.max(1, minSupply))
        .maxDaysAgo(7)
        .fleetCarriers(Boolean.FALSE);
return parse(client.getNearbyExports(nearSystem, commodity, params));
```

Accept both known Ardent response envelopes, discard rows missing system/station, sort null-safe by distance, and cap results at 25.

- [ ] **Step 4: Run and verify GREEN**

Run: `mvn -Dtest=CommoditySourceSearchTest test`

Expected: PASS.

- [ ] **Step 5: Commit search adapter**

```text
feat: search nearby commodity sources with Ardent
```

### Task 5: Transport Source Dialog and From-Line Action

**Files:**
- Create: `src/main/java/org/dce/ed/ui/CommoditySourceDialog.java`
- Modify: `src/main/java/org/dce/ed/MissionsTabPanel.java`
- Test: `src/test/java/org/dce/ed/MissionsTabPanelTest.java`
- Test: `src/test/java/org/dce/ed/mission/CommoditySourceDialogModelTest.java`

**Interfaces:**
- Consumes: mission state from Task 3 and `CommoditySourceSearch` from Task 4.
- Produces: a dialog result containing trimmed system/station or cancellation.

- [ ] **Step 1: Write failing UI-model and panel behavior tests**

```java
assertTrue(panel.rowForMission(id).showsSourcedFromAction());
assertEquals("—", panel.rowForMission(id).fromDisplayLine());
assertEquals("Galileo", model.validateAndBuild(" Sol ", " Galileo ").station());
```

Verify provided delivery missions do not show the action, saved sources remain editable, blank system/station validation fails, and successful save triggers row refresh plus session callback.

- [ ] **Step 2: Run and verify RED**

Run: `mvn -Dtest=MissionsTabPanelTest,CommoditySourceDialogModelTest test`

Expected: compilation failure for missing action/model APIs.

- [ ] **Step 3: Implement dialog and row action**

Build the dialog with editable Near system, Search, result table, manual System/Station fields, status label, Save, and Cancel. Use `SwingWorker<List<CommoditySourceChoice>,Void>` with a monotonically increasing request id so stale results are ignored. Initialize Near system from `currentSystemSupplier`; selecting a row fills the manual fields.

Render `Sourced from?` adjacent to From for self-sourced rows through the existing Places renderer/editor pattern. On save call `tracker.setSourcedFrom`, rebuild rows, and invoke the session-state callback. Do not touch any route or target API.

- [ ] **Step 4: Run focused and regression tests**

Run: `mvn -Dtest=MissionsTabPanelTest,CommoditySourceDialogModelTest,MissionDestinationResolverTest,MissionTrackerTest,CommoditySourceSearchTest test`

Expected: PASS.

Run: `mvn test`

Expected: PASS.

- [ ] **Step 5: Commit Transport UI**

```text
feat: choose sources for sourced transport missions
```

### Task 6: Visual and Runtime Verification

**Files:**
- Modify only if a verified defect is found in files already covered above.

**Interfaces:**
- Consumes: completed Loop and Transport features.
- Produces: verified application behavior without changing unrelated code.

- [ ] **Step 1: Build the application**

Run: `mvn -DskipTests package`

Expected: BUILD SUCCESS.

- [ ] **Step 2: Launch and inspect the two features**

Verify Loop is left of Clear, icon paint is crisp, tooltip is `Loop`, highlight follows selection, and selection survives restart. Verify a sourced Gold mission shows `From: —`, opens the dialog, recalculates from an edited system, accepts an Ardent row or manual station, and updates only From.

- [ ] **Step 3: Re-run affected tests after any visual adjustment**

Run: `mvn -Dtest=RouteTabPanelHelperTest,NextRouteDestinationTest,RouteSessionTest,MissionsTabPanelTest,CommoditySourceDialogModelTest,MissionDestinationResolverTest,MissionTrackerTest,CommoditySourceSearchTest test`

Expected: PASS.

- [ ] **Step 4: Review the final diff**

Run: `git diff --check` and `git status --short`.

Confirm the pre-existing body-chevron edit remains preserved and separately identifiable.
