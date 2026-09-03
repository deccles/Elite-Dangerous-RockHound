# RockHound Engineering Recommendation Format

RockHound accepts engineering recommendations as [SLEF (Ship Loadout Exchange Format)](https://inara.cz/elite/inara-impexp-slef/) JSON. Return either a `.slef.json`/`.json` file or a raw JSON block that can be copied to the clipboard.

## Instructions for recommendation agents

The RockHound summary already supplies every identifier needed for a valid recommendation. The `SLEF ship:` line gives the exact `Ship`, `ShipID`, and (when present) `ShipName`. Every engineerable module line ends with its exact `[Slot=...; Item=...]` values. Duplicate modules are listed separately with distinct slots.

Do not ask the commander for another loadout export or journal event. Copy these identifiers exactly; do not infer, translate, normalize, or invent them. Include only modules you recommend engineering, each with its matching `Slot` and `Item` plus an `Engineering` object containing:

- `BlueprintName`: the Elite journal blueprint identifier, such as `FSD_LongRange`.
- `Level`: the target grade as an integer.
- `ExperimentalEffect`: the Elite journal identifier when recommending an experimental effect.
- `ExperimentalEffect_Localised`: the display name of that experimental effect. Include this whenever `ExperimentalEffect` is present so RockHound can validate it without guessing.

Modules without an `Engineering` object are ignored during goal import. Do not invent current progress, completed rolls, material quantities, or modifiers. RockHound derives progress and material needs from its journal and engineering database.

If an identifier is genuinely absent from the supplied RockHound summary, explain which specific module is missing it instead of guessing. A normal current summary is complete and requires no additional export.

Return valid JSON without comments or Markdown fences. A minimal response is:

```json
{
  "header": {
    "appName": "Engineering Recommendation Agent",
    "appVersion": "1"
  },
  "data": {
    "event": "Loadout",
    "Ship": "mandalay",
    "ShipID": 9,
    "ShipName": "Wayfinder",
    "Modules": [
      {
        "Slot": "FrameShiftDrive",
        "Item": "int_hyperdrive_overcharge_size5_class5",
        "Engineering": {
          "BlueprintName": "FSD_LongRange",
          "Level": 5,
          "ExperimentalEffect": "special_fsd_heavy",
          "ExperimentalEffect_Localised": "Mass Manager"
        }
      }
    ]
  }
}
```

RockHound imports recommendations by ship and exact module slot. Existing goals for matching slots are previewed as updates; new slots are previewed as additions; unrelated goals remain unchanged. The commander confirms the preview before any goals are changed.

## Merc Coin (Operations) blueprints

These recipes spend Merc Coins. Coriolis does not know them; RockHound does. Use the journal `BlueprintName` values below. Copy `Slot` and `Item` from the RockHound summary as usual — hardpoint recipes share a `Weapon_*` name, so `Item` is what selects Burst vs Beam vs Pulse (and so on).

| Module | Recipe | `BlueprintName` | Grades |
| --- | --- | --- | --- |
| Fuel Scoop | Scoop Rate Enhanced | `FuelScoop_ScoopRateEnhanced` | G1–G5 |
| Burst / Beam / Pulse Laser | Plasma Conversion | `Weapon_PlasmaConversion` | G1–G5 |
| Surface Scanner | Long Range Detailed | `SurfaceScanner_LongRangeDetailed` | G2–G5 |
| Cargo Rack | Extended | `CargoRack_Extended` | G2–G5 |
| Power Distributor | Balanced | `PowerDistributor_Balanced` | G2–G5 |
| Power Distributor | Support Focused | `PowerDistributor_SupportFocused` | G2–G5 |
| Module Reinforcement Package | Heavy Duty | `ModuleReinforcement_HeavyDuty` | G2–G5 |
| Fragment Cannon | Double Screaming | `Weapon_DoubleScreaming` | G2–G5 |
| Rail Gun | Enduring Feedback | `Weapon_EnduringFeedback` | G2–G5 |
| Abrasion Blaster | Far-reaching | `Weapon_FarReaching` | G2–G5 |
| Mining Laser | Long Range | `Weapon_LongRange` | G2–G5 |
| Missile Rack | Drag | `Weapon_Drag` | G2–G5 |
| Missile Rack | Lightweight Thermal | `Weapon_LightweightThermal` | G2–G5 |
| Missile Rack | Lockdown | `Weapon_Lockdown` | G2–G5 |
| Missile Rack | Exposing | `Weapon_Exposing` | G2–G5 |
| Multi-cannon | Rapid Phase | `Weapon_RapidPhase` | G2–G5 |
| Enzyme Missile Rack | High-yield | `Weapon_HighYield` | G2–G5 |
| Cannon | Force Impact | `Weapon_ForceImpact` | G2–G5 |
| Beam Laser | Overloaded | `Weapon_Overloaded` | G2–G5 |
| Burst Laser | Regenerative | `Weapon_Regenerative` | G2–G5 |

Do not use display names (`Scoop Rate Enhanced`) as `BlueprintName`. Do not recommend these as Coriolis experimental effects.
