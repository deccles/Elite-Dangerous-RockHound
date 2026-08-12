# Transport Sourced-From Design

## Scope

Correct the Transport tab's origin display for self-sourced commodity missions and allow the commander to record where the commodity will be purchased. Provide live nearby-market suggestions through the existing Ardent API integration, with manual entry always available.

This work does not change route plotting or navigation targets. The already-approved Custom Route Loop toggle remains a separate implementation item delivered in the same development pass.

## Mission Classification

- Treat journal mission names beginning with `Mission_Sourced` as self-sourced commodity missions.
- Do not use the mission destination or mission-acceptance station as the source for these missions.
- Continue using the acceptance/pickup location for provided-cargo delivery missions.
- Other commodity mission types retain their existing behavior.

## Transport Display

- A self-sourced mission without a saved purchase source displays `From: —` rather than duplicating its destination.
- Add a compact `Sourced from?` button beside the From line for self-sourced missions.
- Keep the button available after a source is selected so the commander can correct it.
- A saved source replaces the From line and becomes the From-side clipboard value.
- The To line, mission destination, route state, and navigation behavior remain unchanged.

## Source Selection Dialog

The dialog contains:

- The commodity name and remaining or required quantity.
- An editable `Near system` field initialized from the commander's current system.
- A Search/Recalculate action.
- A results table of nearby stations selling the commodity.
- Manual System and Station fields.
- Save and Cancel actions.

Selecting a market result copies its system and station into the manual fields. Save requires both fields after trimming whitespace and stores them on the individual mission. Manual entry works whether or not a live search has succeeded.

## Ardent Lookup

- Use the existing `ArdentClient.getNearbyExports` endpoint because an exporter sells the requested commodity to the commander.
- Run the request off the Swing event-dispatch thread and marshal UI updates back to it.
- Search from the editable Near system value, initially the current system.
- Request at least the mission's remaining required quantity when that value is positive.
- Exclude fleet carriers by default to favor stable station results.
- Sort results by proximity using the distance fields returned by Ardent.
- Display station, system, system distance, station distance, buy price, supply, and market-data age when present.
- Limit the visible result set to a practical number while preserving the closest results.
- If the lookup fails, returns no results, or the commodity is not market-purchasable, show a concise status and leave manual entry fully usable.

Inara's public commodity-search page may remain a user reference, but the application will not scrape it. Inara's official API is not a general market-search API, while Ardent provides the required public market-data endpoint.

## Persistence

- Add optional sourced-from system and station fields to `MissionRecord` and its session representation.
- Round-trip both fields through mission persistence.
- Journal refreshes merge mission data without discarding a user-selected source.
- Removing or completing the mission removes the source with the mission record naturally.

## Components

- `MissionRecord` owns the optional selected source identity and the self-sourced classification helper.
- `MissionDestinationResolver` returns the saved source for self-sourced missions and never substitutes the delivery destination.
- `MissionTracker` and mission session mapping preserve the user-selected fields.
- `MissionsTabPanel` renders the button, opens the dialog, saves the selection, refreshes rows, and triggers session persistence.
- A focused market-search adapter parses Ardent responses into typed station choices so network/JSON details remain separate from Swing layout.

## Error Handling

- Search validates that the Near system and commodity are nonblank.
- Save validates both manual source fields.
- Network and JSON failures are shown inside the dialog without closing it.
- Stale asynchronous responses must not replace results from a newer recalculation.
- Closing the dialog while a lookup is running must not update disposed UI.

## Testing

- Verify self-sourced mission classification without changing provided-delivery classification.
- Verify self-sourced missions with no saved choice produce an empty From destination, never the To destination.
- Verify a saved source becomes the From display and copy value only.
- Verify source fields round-trip through session persistence and survive mission merges.
- Verify Ardent response parsing, proximity ordering, missing optional fields, empty results, and malformed responses.
- Verify the source button is shown only for self-sourced missions and remains available after selection.
- Verify dialog validation and that saving requests a session-state update.
- Verify the existing Transport behavior for courier, passenger, and provided commodity missions remains unchanged.
