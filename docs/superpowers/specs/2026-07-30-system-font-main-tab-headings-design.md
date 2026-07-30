# System Font for Main Tab Headings

## Goal

Use the operating system's button-font family for the main overlay navigation
headings so they remain readable when the overlay content font is decorative.

## Scope

The change applies only to the top-level navigation buttons:

- Route
- System
- ExoBio
- Mining
- Missions
- Combat
- Fleet Carrier
- Engineering
- Control Panel

Section headings, table headings, table content, dialogs, and other overlay text
continue using their existing fonts.

## Design

Capture the native Swing button font before overlay font customization can replace
it. `EliteOverlayTabbedPane` will derive the existing bold 10-point tab style from
that captured system font instead of deriving it from each newly created button.

Tab colors, borders, padding, selection state, hover behavior, drag behavior, and
dynamic size calculation remain unchanged. The existing size calculation will
measure the resulting system font, so labels continue to fit without truncation.

## Preference Updates

Changing the overlay content font must not change the main-tab font family. Existing
font-refresh paths continue updating tab contents only.

## Verification

Add a focused regression test that constructs the navigation with a customized
overlay content font and verifies that each main-tab button retains the captured
system button-font family. Run the focused test and the relevant project test suite.
