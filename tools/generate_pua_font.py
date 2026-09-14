#!/usr/bin/env python3
"""Build LCPatch's PUA-compatible font from an OFL-licensed OpenType font."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


MAP_PATTERN = re.compile(r"^U\+([0-9A-Fa-f]{4,6}).*->\s+U\+([0-9A-Fa-f]{4,6})")


def read_mapping(path: Path) -> dict[int, int]:
    result: dict[int, int] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = MAP_PATTERN.match(line)
        if match:
            result[int(match.group(1), 16)] = int(match.group(2), 16)
    if not result:
        raise ValueError("PUA mapping is empty")
    return result


def rename_font(font: TTFont) -> None:
    replacements = {
        1: "LCPatch Sans CJK",
        2: "Regular",
        3: "LCPatch Sans CJK Regular",
        4: "LCPatch Sans CJK Regular",
        6: "LCPatchSansCJK-Regular",
    }
    name_table = font["name"]
    for record in name_table.names:
        if record.nameID in replacements:
            record.string = replacements[record.nameID].encode(record.getEncoding())


def build(source: Path, mapping_path: Path, output: Path) -> None:
    mapping = read_mapping(mapping_path)
    font = TTFont(source, recalcBBoxes=False, recalcTimestamp=False)
    # Unity versions used by the game are more predictable with a static
    # TrueType font than with a variable font. Freeze the open-source source
    # font at Regular before adding the PUA cmap.
    if "fvar" in font:
        font = instantiateVariableFont(font, {"wght": 400}, inplace=False)
    best = font.getBestCmap()
    missing = [codepoint for codepoint in mapping if codepoint not in best]
    if missing:
        sample = ", ".join(f"U+{value:04X}" for value in missing[:8])
        raise ValueError(f"source font lacks {len(missing)} mapped characters: {sample}")

    unicode_tables = [table for table in font["cmap"].tables if table.isUnicode()]
    for source_codepoint, pua_codepoint in mapping.items():
        glyph_name = best[source_codepoint]
        for table in unicode_tables:
            if pua_codepoint <= 0xFFFF or table.format in (10, 12, 13):
                table.cmap[pua_codepoint] = glyph_name

    rename_font(font)
    output.parent.mkdir(parents=True, exist_ok=True)
    font.save(output, reorderTables=True)
    rebuilt = TTFont(output, lazy=True)
    rebuilt_cmap = rebuilt.getBestCmap()
    absent = [value for value in mapping.values() if value not in rebuilt_cmap]
    if absent:
        raise ValueError(f"generated font lost {len(absent)} PUA mappings")
    print(f"wrote {output} with {len(mapping)} PUA mappings")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("mapping", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.source, args.mapping, args.output)


if __name__ == "__main__":
    main()
