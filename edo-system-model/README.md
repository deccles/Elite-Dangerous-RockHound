# edo-system-model

Journal-authoritative Elite Dangerous system topology and Kepler positions for EDO.

## Build

```bash
mvn install
```

EDO depends on `org.dce:edo-system-model:1.0.1` from the local Maven repository.

## Versioning

Bump `edo-system-model.version` in `reactor/pom.xml` whenever this module changes (not when only the overlay app changes).

| Change | Bump |
|--------|------|
| Hierarchy / orbit-parent rules, Kepler or position behaviour, public model API | **Patch** (`1.0.x`) unless breaking |
| Breaking API or semantics for `org.dce.systemmodel.*` consumers | **Minor** or **major** as appropriate |

The overlay app version (`elite-dangerous-overlay-parent`, e.g. `1.3.0`) is separate from this library version.

## Submodule

Add as a git submodule beside `EDO`:

```bash
git submodule add <repo-url> edo-system-model
```

## No-infer audit

The submodule must contain **zero** of the following patterns (verified by grep):

- `infer*` / `backfill*` parent or topology repair
- `pseudoOffset` / map layout compression
- designation override beyond journal `Parents[]` rules

**Allowed journal-only rules:**

- `OrbitParentSelector` — binary moons prefer `Null:N` when `ScanBaryCentre` exists
- `DesignationParser` — read moon letter suffixes from body names (no guessing)
- Kepler math from journal orbital elements only

Run audit:

```bash
rg -i "infer|backfill|pseudoOffset|MapScaleMode" src/main/java
```

## Tests

Contract tests use `src/test/resources/systemmap/*-events.json` event logs.
