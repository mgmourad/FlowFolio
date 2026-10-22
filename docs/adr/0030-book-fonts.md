# ADR-0030: The book's own fonts

- **Status:** Accepted (lead engineer, 2026-10-22, within the approved M3 plan, `docs/plans/phase2-m3.md` item 3)
- **Date:** 2026-10-22
- **Deciders:** lead engineer (product owner approved M3's scope)
- **Phase:** 2 (M3)

## Context

Many EPUBs ship fonts and name them in their CSS with `@font-face` (the IDPF *Moby-Dick* sets its text in STIX).
The reader sets every EPUB in one of seven bundled fonts (ADR-0027); the M3 plan adds "Book's font" for readers who
want the book as designed. Font files from a book are untrusted input, may be obfuscated (IDPF or Adobe algorithm),
and come as TrueType, OpenType, WOFF 1 or WOFF 2.

## Options considered

1. **Body family only, read with our own code** (chosen): find the family the book's CSS gives `body` (or `html`,
   `:root`, then `p`), load that family's faces. Headings stay in the app's display face, as for the bundled fonts.
2. **Every family the CSS uses, per element**: needs a CSS cascade in the paginator; far more work for little gain on a
   phone.
3. **A library for WOFF 2** (Brotli decoding): a new dependency for a format few EPUBs use.

## Decision

- `ReadingFont.BOOK` ("Book's font") is a choice like the seven; when the book ships no usable font for its body it is
  set in Literata, and the reader's card says "Book's (none)".
- `EpubFonts` (`:core:epub`) reads the manifest's style sheets (at most 1 MB each), their `@font-face` rules and the
  body's `font-family`. Font files are read through `EpubArchive` (book-internal paths only), de-obfuscated when
  `META-INF/encryption.xml` lists them (IDPF: SHA-1 of the unique identifier over the first 1040 bytes; Adobe: the
  `urn:uuid` bytes over the first 1024), WOFF 1 is inflated back to the plain font with bounds checks, and only files
  that start as TrueType or OpenType with a table directory that fits, at most 16 MB, are passed on. WOFF 2 is skipped
  (option 3 declined).
- The reader builds one typeface from the faces (regular, bold, italic, bold italic) with `Font.Builder` and
  `Typeface.CustomFallbackBuilder`, the system serif behind it for missing characters. Anything the system cannot load
  is dropped silently.

## Consequences

- Positive: books that ship fonts can look as designed; obfuscated fonts work; no new dependency.
- Negative: the system's font engine parses the book's font files; our checks stop non-fonts and oversized files, not a
  crafted font. Fonts named only per element (headings, captions) are not used. WOFF 2 books fall back to Literata.
