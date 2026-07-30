# System Font for Main Tab Headings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every top-level overlay navigation button use the operating system Look & Feel button-font family, independently of the user-selected overlay content font.

**Architecture:** Resolve the native button font from `UIManager.getLookAndFeelDefaults()`, which is separate from the application overrides installed through `UIManager.put`. Centralize the lookup in `EliteOverlayTabbedPane` and continue deriving the existing bold 10-point tab style from that native font.

**Tech Stack:** Java Swing, JUnit Jupiter, Maven Surefire

## Global Constraints

- Change only Route, System, ExoBio, Mining, Missions, Combat, Fleet Carrier, Engineering, and Control Panel navigation buttons.
- Preserve tab colors, borders, padding, selection, hover, drag behavior, and dynamic size calculation.
- Section headings, table headings, table content, dialogs, and other overlay text remain unchanged.
- Changing the overlay content font must not change the main-tab font family.

---

### Task 1: Native Main-Tab Font

**Files:**
- Modify: `src/test/java/org/dce/ed/OverlayTabButtonSizeTest.java`
- Modify: `src/main/java/org/dce/ed/EliteOverlayTabbedPane.java:1186-1192`

**Interfaces:**
- Consumes: `UIManager.getLookAndFeelDefaults().getFont("Button.font")`
- Produces: `private static Font systemTabButtonFont()` returning a non-null native button font with a `Dialog` fallback

- [ ] **Step 1: Write the failing regression test**

Add a test that saves `UIManager.get("Button.font")`, installs a deliberately different application override through `UIManager.put("Button.font", new Font("Monospaced", Font.PLAIN, 19))`, constructs `EliteOverlayTabbedPane` on the EDT, collects all nine named navigation buttons, and asserts:

```java
Font nativeButtonFont = UIManager.getLookAndFeelDefaults().getFont("Button.font");
assertEquals(nativeButtonFont.getFamily(), button.getFont().getFamily());
assertEquals(Font.BOLD, button.getFont().getStyle());
assertEquals(10, button.getFont().getSize());
```

Restore the saved UIManager value in a `finally` block. Use the existing recursive `collectTabButtons` helper and expand `TAB_LABELS` to contain all nine main tabs.

- [ ] **Step 2: Run the focused test and verify the new assertion fails**

Run:

```powershell
mvn -Dtest=OverlayTabButtonSizeTest test
```

Expected: FAIL because `createTabButton` currently derives from the overridden `JButton` font (`Monospaced`) instead of the native Look & Feel font.

- [ ] **Step 3: Implement the native-font lookup**

In `EliteOverlayTabbedPane`, add:

```java
private static Font systemTabButtonFont() {
    Font systemFont = UIManager.getLookAndFeelDefaults().getFont("Button.font");
    if (systemFont == null) {
        systemFont = new Font(Font.DIALOG, Font.PLAIN, 10);
    }
    return systemFont.deriveFont(Font.BOLD, 10f);
}
```

Change `createTabButton` from:

```java
button.setFont(button.getFont().deriveFont(Font.BOLD, 10f));
```

to:

```java
button.setFont(systemTabButtonFont());
```

- [ ] **Step 4: Run the focused tests**

Run:

```powershell
mvn -Dtest=OverlayTabButtonSizeTest test
```

Expected: PASS, including both font isolation and preferred-size coverage.

- [ ] **Step 5: Run related UI tests**

Run:

```powershell
mvn -Dtest=OverlayTabButtonSizeTest,EdoLookAndFeelTest test
```

Expected: PASS with no failures or errors.

- [ ] **Step 6: Review the diff**

Run:

```powershell
git diff --check
git diff -- src/main/java/org/dce/ed/EliteOverlayTabbedPane.java src/test/java/org/dce/ed/OverlayTabButtonSizeTest.java
```

Confirm that no unrelated user changes are modified or staged.
