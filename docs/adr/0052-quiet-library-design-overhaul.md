# ADR-0052: Adopt the "Quiet Library" design and four-destination structure

- **Status:** Accepted (product owner, 2026-11-09)
- **Date:** 2026-11-09
- **Deciders:** product owner, lead engineer
- **Phase:** UX overhaul between Phase 6 and Phase 7 (`docs/plans/ux-overhaul-plan.md`)
- **Supersedes in part:**
  - ADR-0015: the design reference (`docs/design/FlowFolio_Mock.html`) and its colour table. Its fonts, the 3D
    curl decision and the Fold layout rules (rail when unfolded, wider layouts, full-bleed spread) remain.
  - ADR-0034: the shape of the screen entrance (112 % scale) and the full-height top edge fade. Entrances on every
    visit, still landings and the bottom edge fade remain.
  - ADR-0014's per-role accent colours (title, item, sub-item as separate hues). Its structure (palette, own toggle,
    glyphs, Night pages) remains.

## Context

The 2026-11-09 review (`docs/ux-review/2026-11-09/ux-review.md`, kept locally) found the UI distinctive but
over-spent and the shell around the reader uneven:

- five saturated accents with glow in dark mode;
- seven top-level destinations, four of them lists of marks;
- eight Settings pages for about 25 options;
- a reader tool rail that covers the right page's text.

It also found bugs in search, counts, plurals and a library crash. The owner approved an interactive mock,
`docs/ux-review/2026-11-09/flowfolio-app-mock.html`, Direction A "Quiet Library", covering every screen folded and
unfolded, light and dark. That folder is not committed (it holds screenshots of commercial books), so this ADR and
the plan record the decisions in words.

## Options considered

1. **A, Quiet Library:** warm neutrals, one terracotta/amber accent, no default glow; keeps Fraunces, 3D covers,
   shelves and the curl. Largest gain in polish for the least risk.
2. **B, Paper & Ink:** neutral ink on paper, covers as the only colour, Literata headings. Calmest, but loses the
   brand.
3. **C, Neon, disciplined:** the current indigo/neon with one accent and no glow. Smallest visual change.

All three share the same structure, so tone and structure were chosen separately.

## Decision

Option A, with the mock as the design reference from now on.

- **Colour:** one accent with one meaning, the primary action and the selected state. Everything else is ink at three
  weights on warm neutrals. The semantic tokens are bg, bg2, surface, surface2, surface3, line, ink, ink2, ink3,
  accent, accentInk, accentSoft, ok, okSoft, danger, dangerSoft, paper and paperInk.
  - **FlowFolio** uses the values in the plan: light accent `#C2410C`, dark `#F59E5B`.
  - **Rustic, Material You and AMOLED** take the mock's overrides. Material You keeps dynamic colour.
- **Glow:** "Night glow" is opt-in and off by default. It blooms display titles in dark mode only.
- **Type:** Fraunces for display and titles, Instrument Sans for UI. Literata is the default reading font, and all
  seven reading fonts stay.
- **Shape:** cards 22 dp, sheets 28 dp, pills fully round. Icon buttons are 42 dp (36 dp small) with 48 dp targets.
- **Controls:**
  - A toggle's track is its state, and the thumb is always white.
  - Sliders show their value.
  - Every icon button has a long-press tooltip.
  - Every removal offers Undo, and every destructive action asks first.
- **Covers:** one 2:3 cover everywhere, with a spine highlight, a soft shadow and badges at the bottom right.
  `Book3D` remains for transitions.
- **Structure:**
  - Four destinations: Library, Shelves, Notebook and Settings.
  - Favorites becomes a Library filter. Bookmarks, annotations, VoiceMarks and saved words merge into Notebook, grouped
    by book.
  - Old routes redirect to their new homes.
- **Settings:** a hub of dedicated pages when folded, and list and detail when unfolded. Text settings are the
  defaults for new books, and the reader's Aa panel overrides them per book (Room v21, `book_text_settings`).
- **Reader:**
  - The page is the hero. The top bar holds a title chip, the ribbon button and ⋯.
  - A bottom dock combines the riffle with six tools. It **replaces the unfolded right-hand rail**, so chrome never
    covers text.
  - The riffle keeps every behaviour (ADR-0022 to 0026) and is only redrawn.
- **Motion:** panels, menus and snackbars rise 16 dp with a fade (about 280 ms, `cubic-bezier(.2,.8,.2,1)`). Screen
  entrances keep ADR-0034's "every visit" rule but use the same rise instead of the 112 % scale. A status-bar fade
  replaces the full top edge fade. Reduced motion turns off the flip, the lens and entrances. Haptics stay.
- **One source for counts:** every count of marks comes from one repository query that respects the reader's current
  view (ADR-0045 `ReadingViews`).

## Consequences

- **Positive:**
  - Calmer and more legible.
  - The folded tab bar drops from seven targets to four.
  - Nothing covers the page.
  - Turning glow off by default returns the 70 to 100 MB of GPU memory that text glows cost in dark mode
    (known issue 21).
  - Counts can no longer disagree between screens.
- **Negative / accepted risks:**
  - A large change across every screen.
  - Old palette fields are aliased during the transition and could be misread. They are removed at the end.
  - Users of the right-hand rail lose it, and the dock carries the same tools.
  - The design reference is not in the repository, so a fresh clone has only this ADR and the plan.
- **Follow-ups:**
  - Phases 1 to 9 in `docs/plans/ux-overhaul-plan.md`.
  - The feature-parity checklist `docs/plans/ux-overhaul-parity.md`.
  - The release of FlowFolio 0.6.0-canary.2 at the end.
  - Update `CLAUDE.md`'s "Design language" section to point at this ADR once it is accepted.
