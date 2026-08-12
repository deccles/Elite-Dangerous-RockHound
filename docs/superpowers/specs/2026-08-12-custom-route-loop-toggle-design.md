# Custom Route Loop Toggle Design

## Scope

Add a remembered Loop toggle to the ship Custom Route controls. The control applies only to custom-route navigation behavior and does not add an option to the Preferences interface.

## User Interface

- Show a compact Loop toggle in the Custom Route warning strip, immediately to the left of Clear.
- Paint a circular-arrow icon so its appearance does not depend on a particular symbol font.
- Set the tooltip text to `Loop`.
- Use the normal outline-button appearance while disabled and the application's established selected/highlight appearance while enabled.
- Show and hide the toggle with the existing Custom Route controls.

## Remembered State

- Store Loop as an internal global application preference.
- Restore the preference when the application starts and use it for every custom route.
- Toggling Loop updates the preference immediately.
- Clearing, replacing, importing, or restoring a custom route does not change the global Loop preference.
- No Loop control is added to the Preferences window.

## Route Behavior

- With Loop disabled, retain the existing route cursor and next-destination behavior.
- With Loop enabled, reaching the final custom-route system makes the route next-destination value resolve to the first system in the custom route.
- When an arrival event reports that first system after the route has reached its end, reset the custom route's current base index to the beginning.
- After the reset, subsequent next-destination values and displayed progress advance from the beginning normally.
- Do not wrap early when the same system name appears elsewhere in the route; wrap only from the completed end state to the first route entry.
- Apply looping only while a custom route is active. Plotted game routes and fleet-carrier routes retain their existing behavior.

## Components and Data Flow

- `RouteTabPanel` owns the toggle, synchronizes its highlighted state with the remembered preference, and supplies whether custom-route looping is enabled when determining route completion behavior.
- `OverlayPreferences` stores and retrieves the hidden global Boolean preference.
- `RouteSession` owns the cursor transition that wraps from the completed final hop to hop zero after arrival at the first hop.
- The next-destination helper returns the first custom-route system at the end only when both custom-route mode and Loop are enabled.
- Existing jump-complete execution triggers receive the wrapped next destination through the same path they use today.

## Edge Cases

- An empty route has no next destination.
- A one-system custom route does not create a spurious next destination to itself.
- A first-system arrival does not reset progress unless the route cursor has reached the final hop and Loop is enabled.
- Missing or corrupt stored preference data falls back to Loop disabled.

## Testing

- Verify the hidden global preference defaults off and round-trips enabled and disabled states.
- Verify the toggle is positioned before Clear, has tooltip `Loop`, and reflects its selected state.
- Verify disabled routes retain the existing blank next destination at route end.
- Verify enabled multi-system custom routes return the first system as the next destination at route end.
- Verify arrival at the first system after route completion resets the current base index to zero.
- Verify an earlier duplicate does not cause a premature reset.
- Verify empty and single-system routes do not loop to themselves.
- Run the focused route, preferences, persistence, and panel helper tests, followed by the project's standard test suite if focused tests pass.
