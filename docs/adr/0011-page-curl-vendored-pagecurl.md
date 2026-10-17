# ADR-0011: Vendor oleksandrbalan/pagecurl as the page-curl engine

- **Status:** Proposed (awaiting product-owner approval)
- **Date:** 2026-10-16
- **Phase:** 1
- **Evidence:** `docs/spikes/2026-10-pdf-renderer.md` (page-curl section)

## Context

The signature interaction is a realistic page curl, on single pages (cover screen, phones) and two-page spreads
(unfolded foldables, tablets). The brief suggested `oleksandrbalan/pagecurl` (Apache-2.0), unmaintained since
February 2024.

On the reference Fold at 120 Hz, pagecurl 1.5.1 delivered ~98% of frames on time over 50 automated turns, for single
page and (naive, whole-spread) two-page mode. A real book spread turns only the right-hand page and reveals the next
spread underneath, which the library does not support.

## Options considered

1. **Use the Maven artifact as-is:** cheapest; cannot do book-style spreads; unmaintained upstream.
2. **Vendor (copy the source into our repo) and extend:** we own the code (about 12 small Kotlin files); Apache-2.0
   allows it with attribution; we can add spread-aware curling, shadows/back-page tuning, and tests.
3. **Write our own curl from scratch:** full control; weeks of work to reach parity.

## Decision

Option 2: vendor pagecurl 1.5.1 into `:core:pagecurl`, keeping its license header and adding a `NOTICE` entry.
First changes in Phase 1: spread mode where only the right page curls, and inputs that come from the reader's page
cache rather than arbitrary composables.

## Consequences

- No dependency on an unmaintained artifact; changes are ours to test.
- We carry ~1,000 lines of graphics code; covered by screenshot/benchmark tests in Phase 1.
- Upstream fixes (if any) must be merged by hand.
