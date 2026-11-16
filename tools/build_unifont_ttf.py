"""Rebuild the bundled fallback font from an upstream GNU Unifont OpenType file.

PDFBox cannot embed OpenType fonts with CFF outlines, which is the only outline
format Unifont publishes. This converts the outlines to TrueType quadratics and
drops the layout tables: OCR text is written one code point at a time, so
substitution and positioning rules are never wanted.

Usage::

    python tools/build_unifont_ttf.py unifont-16.0.04.otf \
        java/src/main/resources/dev/pypdftools/fonts/Unifont-Regular.ttf

Requires ``fonttools``. Unifont glyphs are drawn from straight segments only, so
the cubic-to-quadratic step is exact.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont, newTable

MAX_CONVERSION_ERROR = 1.0
DROPPED_TABLES = ("CFF ", "VORG", "GSUB", "GPOS", "GDEF")


def glyf_from_cff(font: TTFont) -> dict:
    """Draw every CFF glyph of ``font`` as a TrueType glyph, keyed by glyph name."""
    glyph_set = font.getGlyphSet()
    glyphs = {}
    for name in font.getGlyphOrder():
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(
            Cu2QuPen(pen, MAX_CONVERSION_ERROR, reverse_direction=True)
        )
        glyphs[name] = pen.glyph()
    return glyphs


def convert(source: Path, target: Path) -> None:
    """Write a TrueType-outline copy of ``source`` to ``target``."""
    font = TTFont(source, recalcTimestamp=False)
    if "CFF " not in font:
        raise SystemExit(f"{source} has no CFF table; nothing to convert")

    glyphs = glyf_from_cff(font)
    for tag in DROPPED_TABLES:
        if tag in font:
            del font[tag]

    font["loca"] = newTable("loca")
    glyf = font["glyf"] = newTable("glyf")
    glyf.glyphOrder = font.getGlyphOrder()
    glyf.glyphs = glyphs
    font["head"].glyphDataFormat = 0

    # TrueType renderers place outlines by the left side bearing, which must equal xMin.
    hmtx = font["hmtx"]
    for name, glyph in glyphs.items():
        glyph.recalcBounds(glyf)
        hmtx[name] = (hmtx[name][0], getattr(glyph, "xMin", 0))

    maxp = font["maxp"]
    maxp.tableVersion = 0x00010000
    for field in (
        "maxZones",
        "maxTwilightPoints",
        "maxStorage",
        "maxFunctionDefs",
        "maxInstructionDefs",
        "maxStackElements",
        "maxSizeOfInstructions",
        "maxComponentElements",
    ):
        setattr(maxp, field, 0)
    maxp.maxZones = 1

    # Format 2 keeps glyph names; PDFBox logs a warning for every load of a format 3 table.
    post = font["post"]
    post.formatType = 2.0
    post.extraNames = []
    post.mapping = {}

    font.sfntVersion = "\x00\x01\x00\x00"
    font.save(target)


def main() -> None:
    """Parse arguments and convert the font."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", type=Path, help="upstream unifont-<version>.otf")
    parser.add_argument("target", type=Path, help="TrueType output path")
    arguments = parser.parse_args()
    convert(arguments.source, arguments.target)


if __name__ == "__main__":
    main()
