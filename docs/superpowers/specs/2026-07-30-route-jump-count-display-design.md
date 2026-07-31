# Route Jump Count Display

## Goal

Describe routes in jumps rather than systems and number system rows from zero.

## Behavior

- A route containing `n` real system rows displays `Route: n - 1 jumps`.
- Real system rows are numbered `0` through `n - 1`.
- Synthetic rows and destination body/station detail rows remain unnumbered.
- Empty-route and error messages remain unchanged.
- Partial paste/import suffixes such as `(added 3 of 5)` remain unchanged.
- The behavior applies consistently to journal routes, restored custom routes,
  pasted routes, and Spansh imports.

## Design

Add one route-heading formatter based on the count of real, non-synthetic,
non-body system rows. Route loading and mutation paths use that formatter
instead of constructing `systems` labels independently.

Change the existing display-index traversal to start at zero. It continues to
skip synthetic and body rows.

## Verification

Unit tests cover a three-system route producing `Route: 2 jumps`, zero-based
row indexes, and unchanged blank indexes on body rows.
