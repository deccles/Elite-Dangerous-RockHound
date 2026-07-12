# Engineering data tools

Generates bundled engineering blueprint and material tables for EDO.

## Quick start

```bash
pip install -r tools/engineering/requirements.txt
python tools/engineering/inara_scraper.py --fallback-edengineer
```

Outputs:

- `src/main/resources/engineering/blueprints.json`
- `src/main/resources/engineering/materials.json`

## INARA scrape (primary)

When INARA is reachable from your network:

```bash
python tools/engineering/inara_scraper.py --delay 1.5
```

The script crawls:

1. [INARA blueprints index](https://inara.cz/elite/blueprints/)
2. Each `/elite/blueprint/{id}/` detail page (grades, modifiers, materials, experimentals)
3. Each referenced `/elite/component/{id}/` page (type, subtype, grade for material traders)

If INARA returns errors (503, rate limits), the script automatically falls back to EDEngineer open data.

## Spot check

After regenerating data, verify Charge Enhanced G3 materials include Modified Consumer Firmware:

```bash
python -c "import json; b=json.load(open('src/main/resources/engineering/blueprints.json')); ce=[x for x in b if x.get('name')=='Charge Enhanced' and x.get('grade')==3]; print(ce[0]['materials'] if ce else 'missing')"
```

## Refresh cadence

Re-run when Frontier adds engineering changes or after major game patches.
