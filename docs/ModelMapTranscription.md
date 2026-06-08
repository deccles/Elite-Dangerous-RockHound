# Model map transcription contract

The orbital map transcribes `SystemModel` into draw primitives. The display layer does not reinterpret topology.

## M-0 — No inference

| Allowed | Not allowed |
|---------|-------------|
| Read journal fields as-is | `infer*`, `synthesize*`, default parents |
| Arrival star `BodyID:0` with no `Parents[]` = system origin | Treat empty parents on body 0 as incomplete |
| Mark nodes MISSING when data absent | Guess hub parents from designation |
| Omit MISSING nodes from map | Synthetic `BarycentreNode` without `ScanBaryCentre` |
| `parents[0]` as immediate orbit parent | `OrbitParentSelector` overrides |

```
immediateParent(scan) = scan.parents[0]   // when parents non-empty
```

`BarycentreNode` exists only from `ScanBaryCentre` journal events.

## T-1 — World position (3D metres)

Recursive graph walk from `HierarchyGraph.parentOf`:

- Root (no parent): `(0, 0, 0)`
- Body with orbital elements: `parentPos + keplerDisplacement(orbit, t)`
- Barycentre with orbit: same
- Barycentre without orbit but with definitive members: member centroid
- Missing elements: omit position

## T-2 — Orbit rings

For each definitive node `N` with parent `P` and orbital elements, emit one Kepler polyline translated to `position(P)`. Co-orbit members at a shared `Null:N` hub each get their own ring from their journal elements around that hub — no shared or averaged mutual ellipse.

For each `BarycentreNode` with heliocentric orbital elements from `ScanBaryCentre`, emit one hub ring around its orbit parent (e.g. `Star:X` or `Null:0`).

Not emitted: synthetic trunk rings, inner-pair rings, heuristic same-star pair rings, display-averaged mutual rings.

## T-3 — Drawable bodies

Include all definitive `BodyNode` keys. Exclude scan-barycentre metadata rows, planetary ring belt scans, non-definitive nodes.

Bary hubs are revolution centres, not dots by default.

## T-4 — Map-plane projection

PCA axis choice on position cloud, then `MapViewProjection` with optional view tilt. Projection must not change parent-child relationships.

## T-5 — Viewport

True-scale only. No auto-fit, wide-binary flatten, or branch-specific shifts on load.

## T-6 — Labels and LOD

Label visibility and cluster icons may hide labels; must not move body positions or ring geometry.

## T-7 — Playback

At sim epoch `t`, re-run T-1/T-2/T-4. Optional `freezeBarycentreStars` holds stellar mean anomaly at reference epoch.

## Vocabulary

| Entity | Map key |
|--------|---------|
| Star, planet, moon | positive journal `bodyId` |
| Barycentre `Null:N` | `HierarchyKeys.baryMapKey(N)` = `-50000 - N` |
| System root `Null:0` | `-50000` |

Parent edges: `HierarchyGraph.parentOf(mapKey)` only.
