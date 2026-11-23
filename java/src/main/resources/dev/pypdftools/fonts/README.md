# Bundled OCR font

`Unifont-Regular.ttf.gz` is GNU Unifont 16.0.04, which covers the whole Basic
Multilingual Plane (Latin, Greek, Cyrillic, Arabic, Hebrew, Indic, Thai, CJK,
Hangul, ...).

Upstream publishes Unifont only with CFF outlines, which PDFBox 3.0.8 cannot
embed. The bundled TrueType font is built from the upstream source release with
Unifont's own `hex2otf` program, which can write TrueType outlines directly:

```bash
curl -LO https://unifoundry.com/pub/unifont/unifont-16.0.04/unifont-16.0.04.tar.gz
tar xzf unifont-16.0.04.tar.gz
cd unifont-16.0.04
make -C src hex2otf
src/hex2otf hex=font/precompiled/unifont-16.0.04.hex \
  pos=font/plane00/plane00-combining.txt format=truetype 6=Unifont \
  out=Unifont-Regular.ttf
gzip -9n Unifont-Regular.ttf
```

- `unifont-16.0.04.tar.gz` SHA-256: `2bd4e4679757126f48e1bf2c1be40b09aa92162bfedda4683ce5fbc70a2a5972`
- `Unifont-Regular.ttf` SHA-256: `196daad73cc4fd0afb14bbfc6636fe9d286cff568af4ffbdc5dbe04af71e7b32`
- `Unifont-Regular.ttf.gz` SHA-256: `d5d60d225e6c1c1b027c9d43683f97f2a92aaf71c0a5ac13ec6bcb15a72a8ca2`

The build is reproducible. `6=Unifont` sets the PostScript name, which PDFBox
uses for the embedded font's `BaseFont`. The upstream `.otf` additionally
carries a Plane 2/3 CJK subset and the copyleft sign; the bundled font is
limited to the Basic Multilingual Plane.

The font is distributed under the SIL Open Font License 1.1. See
`LICENSE.txt` in this directory.
