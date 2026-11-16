# Bundled OCR fonts

Each OCR code point is drawn with the first font below that has a glyph for it.

## Noto Sans (primary)

`NotoSans-Regular.ttf` is the hinted static Noto Sans Regular font from the
archived `notofonts/noto-fonts` repository at commit
`ffebf8c1ee449e544955a7e813c54f9b73848eac`:

https://github.com/notofonts/noto-fonts/blob/ffebf8c1ee449e544955a7e813c54f9b73848eac/hinted/ttf/NotoSans/NotoSans-Regular.ttf

SHA-256: `b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5`

The font is distributed under the SIL Open Font License 1.1. See
`LICENSE.txt` in this directory.

## GNU Unifont (fallback)

`Unifont-Regular.ttf.gz` covers the rest of the Basic Multilingual Plane
(Arabic, Hebrew, Indic, Thai, CJK, Hangul, ...). It is built from the upstream
release file `unifont-16.0.04.otf`:

https://unifoundry.com/pub/unifont/unifont-16.0.04/font-builds/unifont-16.0.04.otf

Upstream SHA-256: `0e3981ab552231b5a2a870f2b61741903a4bf25c23ef5aeb05fdced1b3c7af4d`

PDFBox cannot embed CFF outlines, so the outlines are converted to TrueType and
the layout tables dropped, then the result is gzipped. The build is
reproducible with fontTools 4.68.0:

```bash
python tools/build_unifont_ttf.py unifont-16.0.04.otf /tmp/Unifont-Regular.ttf
gzip -9n -c /tmp/Unifont-Regular.ttf > Unifont-Regular.ttf.gz
```

- Converted TTF SHA-256: `ec8dc0826bfc1e8f2a22a1a6f352d3d69f48365a5d9aad0c5af50807f7256a58`
- `Unifont-Regular.ttf.gz` SHA-256: `744b86e9ca5970e64130d32b35372c97426aae37ee5ec9bfd877815e962c95f3`

The modified font is distributed under the SIL Open Font License 1.1. See
`UNIFONT-LICENSE.txt` in this directory.
