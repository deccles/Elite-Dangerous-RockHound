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
