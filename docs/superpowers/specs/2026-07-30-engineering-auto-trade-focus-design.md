# Engineering Auto-Trade Focus Reliability

## Problem

Engineering material trades wait for Elite Dangerous to become the foreground
application before sending input. Windows foreground activation is asynchronous
and can also be delayed or denied by foreground-lock rules. The current focus
helper checks the foreground window immediately after each activation API call,
so it can report failure while Elite is still completing the activation.

The always-on-top material-trade confirmation dialog also remains eligible to
compete for focus while the background trade worker attempts to activate Elite.
Manual selection of Elite succeeds and allows the existing trade sequence to run,
confirming that trade planning and key dispatch are not the failing components.

## Design

### Activation polling

After each activation technique in `EliteWindowFocus`, poll for a bounded period
for `isEliteForeground()` to become true. Successful detection must retain the
existing stuck-modifier cleanup and diagnostic logging. The overall sequence
continues to try the next technique only when that bounded wait expires.

Polling must be short enough to keep the confirmation flow responsive and long
enough to cover normal asynchronous Windows activation. It must not wait
indefinitely.

### Confirmation dialog

When the user presses OK, the material-trade confirmation dialog will stop being
always-on-top and stop accepting focus before the worker attempts to activate
Elite. It remains visible as a progress indicator but cannot reclaim keyboard
focus during the trade.

If execution finishes, the dialog follows its existing result/disposal path.

### Fallback and errors

The existing manual-focus fallback remains unchanged. If Windows genuinely
denies automatic activation after all techniques and bounded waits, the user is
still prompted to click Elite. No keys are sent until Elite is positively
identified as foreground.

## Testing

- Add a focused unit test for bounded condition polling: delayed success is
  accepted, and an unmet condition times out.
- Add a focused Swing test proving that starting a trade releases the dialog's
  always-on-top/focusable state before invoking the trade action.
- Run the focused tests and the existing Engineering auto-trade tests.
- Run the broader Maven test suite before completion.

## Scope

This change does not alter trade calculations, grid navigation, key mappings,
material data, or the existing manual-focus fallback.
