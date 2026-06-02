# Eclipse setup (EDO + edo-system-model)

The system model module is **`edo-system-model/`** (inside this repo).

## Import both projects (recommended)

1. **File → Import → Maven → Existing Maven Projects**
2. Root directory: **`...\EDO\reactor`** (the `reactor` folder in this repo)
3. Eclipse should list **three** entries: the parent POM, **`edo-system-model`**, and **`EliteDangerousOverlay` (EDO)**
4. Select all → Finish
5. **Right-click workspace → Maven → Update Project…** (select both Java projects)

You should now see **`edo-system-model`** next to **`EDO`** in Package Explorer.

## Already have EDO imported?

Import only the second module:

1. **File → Import → Maven → Existing Maven Projects**
2. Root: **`...\EDO\edo-system-model`**
3. **Maven → Update Project** on EDO

## Build from command line

From the repo root (`EDO/`):

```bash
mvn -f reactor/pom.xml install
```

Or build the library first:

```bash
mvn -f edo-system-model/pom.xml install
```
