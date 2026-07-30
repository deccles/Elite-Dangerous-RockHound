# Fleet Cooldown Exec Simplification

## Goal

Make Fleet Carrier cooldown Exec bindings run immediately and pass the next
Fleet Carrier route hop through one canonical placeholder without modifying the
Windows clipboard.

## Behavior

- When the Fleet Carrier cooldown ends, enabled `FLEET_COOLDOWN_COMPLETE`
  bindings launch immediately.
- The cooldown handler does not add another delay. Individual scripts may
  implement their own delay when needed.
- The cooldown handler does not copy to or clear the Windows clipboard.
- `$CLIPBOARD` resolves from the actual system clipboard at launch time.
- `$FLEET_CARRIER_DESTINATION` resolves from the next hop in the Fleet Carrier
  route session at launch time.
- If no next Fleet Carrier route hop exists,
  `$FLEET_CARRIER_DESTINATION` resolves using the existing unknown-value
  behavior.

## Naming and Compatibility

- `$FLEET_CARRIER_DESTINATION` is the only supported Fleet Carrier destination
  input for configured Exec arguments.
- Remove the `$DESTINATION` alias.
- Remove the special `EDO_DESTINATION` child-process environment variable.
- Do not add or retain a separate destination environment variable; scripts
  receive the value through the argument position containing
  `$FLEET_CARRIER_DESTINATION`.
- This is an intentional breaking change for configurations or scripts that
  still use `$DESTINATION` or `EDO_DESTINATION`.

## Implementation Shape

- Dispatch the Fleet cooldown trigger directly when cooldown completion is
  reported.
- Build the launch context without destination or clipboard side effects.
- Resolve `$FLEET_CARRIER_DESTINATION` through the existing live placeholder
  context.
- Remove the unused 20-second Exec delay calculation and Fleet cooldown
  clipboard-preparation plumbing.
- Remove the obsolete destination alias and special environment export.

## Verification

Automated tests will demonstrate:

- Fleet cooldown completion dispatches without a delayed timer.
- Cooldown and manual launches do not prepare, copy, or clear the clipboard.
- `$FLEET_CARRIER_DESTINATION` expands to the next Fleet Carrier route hop.
- `$DESTINATION` is no longer a recognized placeholder.
- `EDO_DESTINATION` is not exported to child processes.
- Existing Exec and Fleet Carrier cooldown tests continue to pass after their
  expectations are updated to the new behavior.

