# Engineering Auto-Trade Focus Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Engineering auto-trades reliably recognize asynchronous Elite Dangerous activation and publish the fix as RockHound 1.4.58.

**Architecture:** Add a small bounded condition-polling primitive to `EliteWindowFocus` and use it after every Win32 activation attempt. Release the modal confirmation dialog's focus/topmost state before invoking the existing executor, while retaining its visible progress UI and manual-focus fallback.

**Tech Stack:** Java 21, Swing, JNA Win32, JUnit 5, Maven, GitHub Actions.

## Global Constraints

- Do not change trade calculations, grid navigation, key mappings, material data, or the manual-focus fallback.
- Never send keys until `EliteWindowFocus.isEliteForeground()` is true.
- Preserve the user's unrelated `EngineeringGoalDialog.java` working-tree change.
- Release version is `1.4.58`.

---

### Task 1: Bounded asynchronous focus recognition

**Files:**
- Modify: `src/main/java/org/dce/ed/util/EliteWindowFocus.java`
- Create: `src/test/java/org/dce/ed/util/EliteWindowFocusPollingTest.java`

**Interfaces:**
- Produces: package-private `static boolean waitForCondition(BooleanSupplier condition, long timeoutMs, long pollIntervalMs)`
- Consumes: existing `isEliteForeground()` and activation techniques.

- [ ] **Step 1: Write the failing polling tests**

```java
@Test
void acceptsConditionThatBecomesTrueDuringBoundedWait() {
    AtomicInteger checks = new AtomicInteger();
    assertTrue(EliteWindowFocus.waitForCondition(
            () -> checks.incrementAndGet() >= 3, 500L, 1L));
}

@Test
void returnsFalseWhenConditionNeverBecomesTrue() {
    assertFalse(EliteWindowFocus.waitForCondition(() -> false, 15L, 1L));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -Dtest=EliteWindowFocusPollingTest test`

Expected: test compilation fails because `waitForCondition` does not exist.

- [ ] **Step 3: Implement bounded polling and apply it after activation calls**

Add `BooleanSupplier`, a 750 ms activation timeout, and a 20 ms poll interval.
`waitForCondition` checks immediately, sleeps between checks, preserves the
interrupt flag, and returns the final condition value. Replace immediate
foreground checks after `AttachThreadInput`, `SetForegroundWindow`,
`SwitchToThisWindow`, and the Ctrl-key fallback with bounded polling.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `mvn -Dtest=EliteWindowFocusPollingTest test`

Expected: both tests pass.

### Task 2: Prevent the confirmation dialog from competing for focus

**Files:**
- Modify: `src/main/java/org/dce/ed/MaterialTradeConfirmDialog.java`
- Create: `src/test/java/org/dce/ed/MaterialTradeConfirmDialogFocusTest.java`

**Interfaces:**
- Produces: package-private `void releaseFocusForTrade()` that sets the dialog
  non-always-on-top and non-focusable.
- Consumes: the existing OK action before starting the worker thread.

- [ ] **Step 1: Write the failing dialog-state test**

Create the dialog on the EDT, invoke `releaseFocusForTrade()`, and assert:

```java
assertFalse(dialog.isAlwaysOnTop());
assertFalse(dialog.getFocusableWindowState());
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -Dtest=MaterialTradeConfirmDialogFocusTest test`

Expected: test compilation fails because `releaseFocusForTrade` is absent.

- [ ] **Step 3: Implement focus release**

Add `releaseFocusForTrade()` and invoke it synchronously in the OK listener
before the `edo-material-trade` worker starts. Keep the dialog visible so status
and progress remain available.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `mvn -Dtest=MaterialTradeConfirmDialogFocusTest test`

Expected: test passes when a graphical environment is available; otherwise the
test uses JUnit assumptions to skip only the Swing-specific assertion.

### Task 3: Verify, version, and deploy

**Files:**
- Modify: `reactor/pom.xml`
- Modify: `pom.xml`

**Interfaces:**
- Produces: Maven project version `1.4.58`; pushing the `pom.xml` change to
  `main` triggers `.github/workflows/create-release-on-pom-version.yml`.

- [ ] **Step 1: Run focused Engineering/focus tests**

Run:

```powershell
mvn '-Dtest=EliteWindowFocusPollingTest,MaterialTradeConfirmDialogFocusTest,MaterialTraderAutoTradeTest' test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run the complete test suite**

Run: `mvn -f reactor/pom.xml test`

Expected: BUILD SUCCESS with no test failures.

- [ ] **Step 3: Bump both project versions**

Change the parent/project version from `1.4.57` to `1.4.58` in
`reactor/pom.xml` and `pom.xml`.

- [ ] **Step 4: Build release artifacts locally**

Run: `mvn -f reactor/pom.xml -DskipTests package`

Expected: `target/EDO-Overlay.jar` and `target/src.zip` exist.

- [ ] **Step 5: Commit only the focus fix, tests, plan, and version files**

```powershell
git add -- docs/superpowers/plans/2026-07-30-engineering-auto-trade-focus.md src/main/java/org/dce/ed/util/EliteWindowFocus.java src/main/java/org/dce/ed/MaterialTradeConfirmDialog.java src/test/java/org/dce/ed/util/EliteWindowFocusPollingTest.java src/test/java/org/dce/ed/MaterialTradeConfirmDialogFocusTest.java reactor/pom.xml pom.xml
git commit -m "fix: make Engineering trade focus reliable (1.4.58)"
```

Do not stage `src/main/java/org/dce/ed/EngineeringGoalDialog.java`.

- [ ] **Step 6: Push main and monitor release**

Run: `git push origin main`, then inspect the `Release on POM version` workflow
until it completes. Confirm GitHub release `v1.4.58` contains the JAR, source
ZIP, and MSI.
