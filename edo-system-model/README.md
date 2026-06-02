# edo-system-model

Journal-authoritative Elite Dangerous system topology and Kepler positions for EDO.

## Build

```bash
mvn install
```

EDO depends on `org.dce:edo-system-model:1.0.0` from the local Maven repository.

## Submodule

Add as a git submodule beside `EDO`:

```bash
git submodule add <repo-url> edo-system-model
```

## No-infer audit

The submodule must contain **zero** of the following patterns (verified by grep):

- `infer*` / `backfill*` parent or topology repair
- `pseudoOffset` / schematic layout compression
- designation override beyond journal `Parents[]` rules

**Allowed journal-only rules:**

- `OrbitParentSelector` — binary moons prefer `Null:N` when `ScanBaryCentre` exists
- `DesignationParser` — read moon letter suffixes from body names (no guessing)
- Kepler math from journal orbital elements only

Run audit:

```bash
rg -i "infer|backfill|pseudoOffset|schematic" src/main/java
```

## Tests

Contract tests use `src/test/resources/systemmap/*-events.json` event logs.
