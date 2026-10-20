# ADR-0020: Parse EPUBs with our own hardened code, not Readium

- **Status:** Proposed
- **Date:** 2026-10-20
- **Deciders:** product owner, lead engineer
- **Phase:** 2

## Context

FlowFolio must open any EPUB 2 or EPUB 3 book. That covers the container, the package document, the spine, metadata,
the nav document or NCX, the page list, reading direction and fixed-layout flags, and resources. Books come from
anywhere, so the parser is a security boundary: zip bombs, path traversal, XML entity attacks, DRM, scripts and remote
resources all have to be handled. Licensing (ADR-0003) allows no GPL code in the app.

## Options considered

1. **Readium Kotlin toolkit 3.4.0** (BSD-3-Clause, maintained by the Readium Foundation).
   - Pros: mature; handles many edge cases; has a locator model.
   - Cons:
     - it requires `desugar_jdk_libs`, which is GPL-2.0 with the Classpath Exception;
     - it is large;
     - its navigator does not fit our curl, so only the parser would be used.
2. **Our own parser** on Android's built-in zip and XML pull parser.
   - Pros: no dependency; small (about 250 lines in the spike); our limits, our errors.
   - Cons: we meet real-world quirks ourselves.

## Decision

**Option 2, our own parser.** On 2026-10-20 the owner chose not to make a GPL exception for Readium. The spike's
parser opened every corpus book and handled every hostile file. The product version, in `:core:epub`, keeps the
spike's rules and adds the missing case:

- **Paths:** only paths inside the book. `..` is resolved, and anything that climbs out is refused. Android's own
  `ZipException` for such entries is caught and turned into a refusal.
- **Zip bombs:** each entry is capped at 64 MB, counting the bytes actually inflated, not the size the zip claims.
- **XML:** DOCTYPE processing is off, so entities are never expanded and external entities never load. XHTML is parsed
  in relaxed mode, and broken markup keeps what was read.
- **DRM:** an `encryption.xml` that encrypts content is refused with a clear message. Font obfuscation is allowed.
- **Web content:** scripts and event handlers are stripped. The WebView (islands, fixed layout) serves only the book's
  own files and blocks every other request.

## Consequences

- **Positive:** no new dependency, no licence exception, and robustness tests for every rule run in CI (the hostile
  corpus, `tools/testepubs/`).
- **Negative / accepted risks:** quirks of books outside the corpus are found by users first. The corpus grows with
  every one we meet.
- **Follow-ups:** a locator scheme of our own (spine item, character offset and a text-quote selector, matching
  `conversion-spec.md` section 5). It is decided in M2, when positions are saved.
