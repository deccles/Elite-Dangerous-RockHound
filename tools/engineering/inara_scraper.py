#!/usr/bin/env python3
"""
Scrape engineering blueprint and material data from INARA (inara.cz).

When INARA blocks automated requests, use --fallback-edengineer to build from
EDEngineer's open data (same game facts; INARA scraper remains the primary refresh path).

Outputs:
  src/main/resources/engineering/blueprints.json
  src/main/resources/engineering/materials.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import unicodedata
from pathlib import Path
from typing import Any
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup, Tag

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "src" / "main" / "resources" / "engineering"
INARA_BASE = "https://inara.cz"
BLUEPRINTS_INDEX = f"{INARA_BASE}/elite/blueprints/"
EDENGINEER_BLUEPRINTS = (
    "https://raw.githubusercontent.com/msarilar/EDEngineer/master/"
    "EDEngineer/Resources/Data/blueprints.json"
)
EDENGINEER_ENTRY = (
    "https://raw.githubusercontent.com/msarilar/EDEngineer/master/"
    "EDEngineer/Resources/Data/entryData.json"
)

USER_AGENT = "EDO-Engineering-Scraper/1.0 (offline dev tool; contact via github)"
SESSION = requests.Session()
SESSION.headers.update({"User-Agent": USER_AGENT})

RARITY_TO_GRADE = {
    "verycommon": 1,
    "common": 2,
    "standard": 3,
    "rare": 4,
    "veryrare": 5,
}

SUBKIND_TO_TYPE = {
    "raw": "Raw",
    "manufactured": "Manufactured",
    "encoded": "Encoded",
    "data": "Encoded",
}


def slugify(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    text = text.encode("ascii", "ignore").decode("ascii")
    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return text


def journal_key(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def fetch(url: str, retries: int = 3) -> str:
    last_err: Exception | None = None
    for attempt in range(retries):
        try:
            resp = SESSION.get(url, timeout=60)
            resp.raise_for_status()
            resp.encoding = resp.apparent_encoding or "utf-8"
            return resp.text.lstrip("\ufeff")
        except Exception as exc:  # noqa: BLE001
            last_err = exc
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Failed to fetch {url}: {last_err}") from last_err


def fetch_json(url: str) -> Any:
    text = fetch(url)
    return json.loads(text)


def discover_blueprint_urls(index_html: str) -> list[tuple[int, str, str]]:
    """Return list of (blueprint_id, module_type, blueprint_name)."""
    soup = BeautifulSoup(index_html, "html.parser")
    results: list[tuple[int, str, str]] = []
    current_type = "Unknown"

    for el in soup.find_all(["h5", "a"]):
        if el.name == "h5":
            current_type = el.get_text(strip=True)
            continue
        if el.name != "a":
            continue
        href = el.get("href", "")
        m = re.search(r"/elite/blueprint/(\d+)/?", href)
        if not m:
            continue
        bp_id = int(m.group(1))
        name = el.get_text(strip=True)
        if name:
            results.append((bp_id, current_type, name))

    # Deduplicate by blueprint id (keep first module type)
    seen: set[int] = set()
    unique: list[tuple[int, str, str]] = []
    for item in results:
        if item[0] in seen:
            continue
        seen.add(item[0])
        unique.append(item)
    return unique


def parse_modifier_rows(section: Tag) -> list[dict[str, Any]]:
    modifiers: list[dict[str, Any]] = []
    for row in section.select("table tr"):
        cells = [c.get_text(" ", strip=True) for c in row.find_all(["td", "th"])]
        if len(cells) < 2:
            continue
        prop = cells[0].strip()
        if not prop or prop.lower() in ("attribute", "property", "modifiers"):
            continue
        effect = cells[1].strip()
        if not effect or effect == "%":
            continue
        is_good = effect.startswith("+")
        modifiers.append({"property": prop, "effect": effect, "isGood": is_good})
    return modifiers


def parse_crafting_cost(section: Tag) -> list[dict[str, Any]]:
    materials: list[dict[str, Any]] = []
    for link in section.select('a[href*="/elite/component/"]'):
        name = link.get_text(strip=True)
        if not name:
            continue
        href = link.get("href", "")
        m = re.search(r"/elite/component/(\d+)/?", href)
        comp_id = int(m.group(1)) if m else None
        count = 1
        parent = link.parent
        if parent:
            text = parent.get_text(" ", strip=True)
            cm = re.match(r"(\d+)\s*" + re.escape(name), text)
            if cm:
                count = int(cm.group(1))
        materials.append(
            {
                "key": journal_key(name),
                "name": name,
                "count": count,
                "inaraComponentId": comp_id,
            }
        )
    return materials


def parse_blueprint_page(
    bp_id: int, module_type_hint: str, name_hint: str, html: str
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    soup = BeautifulSoup(html, "html.parser")
    blueprints: list[dict[str, Any]] = []
    experimentals: list[dict[str, Any]] = []

    title = soup.find("h2")
    bp_name = title.get_text(strip=True) if title else name_hint
    desc_el = soup.find("p")
    description = desc_el.get_text(strip=True) if desc_el else ""

    module_type = module_type_hint
    h1 = soup.find("h1")
    if h1:
        t = h1.get_text(strip=True)
        if "for " in t.lower():
            module_type = t.split("for", 1)[-1].strip()

    grade_sections = soup.find_all(
        lambda tag: tag.name in ("h4", "h5")
        and tag.get_text(strip=True)
        and "grade" in tag.get_text(strip=True).lower()
    )

    for heading in grade_sections:
        heading_text = heading.get_text(strip=True)
        gm = re.search(r"grade\s*(\d+)", heading_text, re.I)
        if not gm:
            continue
        grade = int(gm.group(1))
        section = heading.find_parent(["div", "section"]) or heading.parent
        if not section:
            continue
        modifiers = parse_modifier_rows(section)
        materials = parse_crafting_cost(section)
        engineers = [
            a.get_text(strip=True)
            for a in section.select('a[href*="/elite/engineer/"]')
            if a.get_text(strip=True)
        ]
        bp_slug = slugify(f"{module_type}_{bp_name}_g{grade}")
        blueprints.append(
            {
                "id": bp_slug,
                "inaraBlueprintId": bp_id,
                "moduleType": module_type,
                "name": bp_name,
                "grade": grade,
                "experimental": False,
                "description": description,
                "engineers": sorted(set(engineers)),
                "materials": [
                    {"key": m["key"], "count": m["count"]} for m in materials
                ],
                "modifiers": modifiers,
            }
        )

    exp_heading = soup.find(
        lambda tag: tag.name in ("h2", "h3", "h4")
        and "experimental" in tag.get_text(strip=True).lower()
    )
    if exp_heading:
        container = exp_heading.find_parent(["div", "section"]) or soup
        for sub in container.find_all(["h4", "h5"]):
            sub_text = sub.get_text(strip=True)
            if not sub_text or "experimental" in sub_text.lower():
                continue
            if "grade" in sub_text.lower():
                continue
            exp_name = sub_text
            section = sub.find_parent(["div", "section"]) or sub.parent
            if not section:
                continue
            modifiers = parse_modifier_rows(section)
            materials = parse_crafting_cost(section)
            exp_slug = slugify(f"{module_type}_{exp_name}_experimental")
            experimentals.append(
                {
                    "id": exp_slug,
                    "inaraBlueprintId": bp_id,
                    "moduleType": module_type,
                    "name": exp_name,
                    "grade": 0,
                    "experimental": True,
                    "parentBlueprint": bp_name,
                    "description": "",
                    "engineers": [],
                    "materials": [
                        {"key": m["key"], "count": m["count"]} for m in materials
                    ],
                    "modifiers": modifiers,
                }
            )

    return blueprints, experimentals


def parse_component_page(comp_id: int, html: str) -> dict[str, Any] | None:
    soup = BeautifulSoup(html, "html.parser")
    h2 = soup.find("h2")
    if not h2:
        return None
    name = h2.get_text(strip=True)
    type_val = ""
    subtype = ""
    grade = 1
    for row in soup.select("table tr"):
        cells = [c.get_text(" ", strip=True) for c in row.find_all("td")]
        if len(cells) < 2:
            continue
        label = cells[0].lower()
        value = cells[1]
        if label == "type":
            type_val = value
        elif label == "subtype":
            subtype = value
        elif label == "grade":
            for token in value.lower().split():
                if token in RARITY_TO_GRADE:
                    grade = RARITY_TO_GRADE[token]
                    break

    mat_type = SUBKIND_TO_TYPE.get(type_val.lower(), type_val or "Manufactured")
    if mat_type == "Encoded" and not subtype:
        subtype = "Encoded"

    return {
        "key": journal_key(name),
        "name": name,
        "type": mat_type,
        "subtype": subtype or mat_type,
        "grade": grade,
        "inaraComponentId": comp_id,
    }


def scrape_inara(delay: float) -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]]]:
    print("Fetching INARA blueprint index...")
    index_html = fetch(BLUEPRINTS_INDEX)
    discovered = discover_blueprint_urls(index_html)
    print(f"Discovered {len(discovered)} blueprint links")

    all_blueprints: list[dict[str, Any]] = []
    component_ids: set[int] = set()

    for i, (bp_id, module_type, name_hint) in enumerate(discovered):
        url = f"{INARA_BASE}/elite/blueprint/{bp_id}/"
        print(f"[{i + 1}/{len(discovered)}] {url}")
        try:
            html = fetch(url)
            grades, exps = parse_blueprint_page(bp_id, module_type, name_hint, html)
            all_blueprints.extend(grades)
            all_blueprints.extend(exps)
            for bp in grades + exps:
                for mat in bp.get("materials", []):
                    cid = mat.get("inaraComponentId")
                    if cid:
                        component_ids.add(cid)
        except Exception as exc:  # noqa: BLE001
            print(f"  WARN: skipped {bp_id}: {exc}", file=sys.stderr)
        time.sleep(delay)

    materials: dict[str, dict[str, Any]] = {}
    comp_list = sorted(component_ids)
    for i, comp_id in enumerate(comp_list):
        url = f"{INARA_BASE}/elite/component/{comp_id}/"
        print(f"[component {i + 1}/{len(comp_list)}] {url}")
        try:
            html = fetch(url)
            parsed = parse_component_page(comp_id, html)
            if parsed:
                materials[parsed["key"]] = parsed
        except Exception as exc:  # noqa: BLE001
            print(f"  WARN: skipped component {comp_id}: {exc}", file=sys.stderr)
        time.sleep(delay)

    return all_blueprints, materials


def build_from_edengineer() -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]]]:
    print("Fetching EDEngineer fallback data...")
    bp_raw = fetch_json(EDENGINEER_BLUEPRINTS)
    entry_raw = fetch_json(EDENGINEER_ENTRY)

    entry_by_name = {e["Name"]: e for e in entry_raw}
    materials: dict[str, dict[str, Any]] = {}

    for e in entry_raw:
        if e.get("Kind") not in ("Material", "Data"):
            continue
        key = e.get("FormattedName") or journal_key(e["Name"])
        subkind = e.get("Subkind", "")
        mat_type = SUBKIND_TO_TYPE.get(subkind.lower(), "Encoded" if e["Kind"] == "Data" else "Manufactured")
        rarity = (e.get("Rarity") or "VeryCommon").lower().replace(" ", "")
        grade = RARITY_TO_GRADE.get(rarity, 1)
        if mat_type == "Raw" and grade > 4:
            grade = 4
        materials[key] = {
            "key": key,
            "name": e["Name"],
            "type": mat_type,
            "subtype": e.get("Group", mat_type),
            "grade": grade,
        }

    blueprints: list[dict[str, Any]] = []
    for row in bp_raw:
        module_type = row["Type"]
        name = row["Name"]
        grade = row.get("Grade", 0)
        experimental = grade == 0 or "Grade" not in row
        bp_id = slugify(f"{module_type}_{name}_g{grade}" if not experimental else f"{module_type}_{name}_experimental")
        mats = []
        for ing in row.get("Ingredients", []):
            ing_name = ing["Name"]
            ent = entry_by_name.get(ing_name, {})
            key = ent.get("FormattedName") or journal_key(ing_name)
            mats.append({"key": key, "count": ing.get("Size", 1)})
            if key not in materials:
                materials[key] = {
                    "key": key,
                    "name": ing_name,
                    "type": "Manufactured",
                    "subtype": "Unknown",
                    "grade": 1,
                }
        modifiers = [
            {
                "property": eff["Property"],
                "effect": eff["Effect"],
                "isGood": bool(eff.get("IsGood", False)),
            }
            for eff in row.get("Effects", [])
        ]
        blueprints.append(
            {
                "id": bp_id,
                "inaraBlueprintId": 0,
                "moduleType": module_type,
                "name": name,
                "grade": 0 if experimental else grade,
                "experimental": experimental,
                "description": "",
                "engineers": row.get("Engineers", []),
                "materials": mats,
                "modifiers": modifiers,
            }
        )

    return blueprints, materials


def write_output(blueprints: list[dict[str, Any]], materials: dict[str, dict[str, Any]]) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    bp_path = OUT_DIR / "blueprints.json"
    mat_path = OUT_DIR / "materials.json"
    bp_path.write_text(json.dumps(blueprints, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    mat_list = sorted(materials.values(), key=lambda m: (m["type"], m["name"]))
    mat_path.write_text(json.dumps(mat_list, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(blueprints)} blueprints -> {bp_path}")
    print(f"Wrote {len(mat_list)} materials -> {mat_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Build EDO engineering JSON data")
    parser.add_argument(
        "--fallback-edengineer",
        action="store_true",
        help="Use EDEngineer open data instead of scraping INARA",
    )
    parser.add_argument("--delay", type=float, default=1.0, help="Delay between INARA requests (seconds)")
    args = parser.parse_args()

    try:
        if args.fallback_edengineer:
            blueprints, materials = build_from_edengineer()
        else:
            try:
                blueprints, materials = scrape_inara(args.delay)
            except Exception as exc:  # noqa: BLE001
                print(f"INARA scrape failed ({exc}); falling back to EDEngineer.", file=sys.stderr)
                blueprints, materials = build_from_edengineer()
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    write_output(blueprints, materials)
    return 0


if __name__ == "__main__":
    sys.exit(main())
