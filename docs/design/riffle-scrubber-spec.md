# FlowFolio feature spec: the Riffle Scrubber

The product owner's spec, received 2026-10-20, with the owner's answers to the lead engineer's questions. The mock is
[`FlowFolio_Riffle_Scrubber.html`](FlowFolio_Riffle_Scrubber.html). Where this spec and the mock disagree, the spec
wins; where the decisions below and the spec disagree, the decisions win. ADR-0022 and ADR-0023 record the design.

## Owner decisions (2026-10-20)

1. *(Changed 2026-10-21: EPUBs keep their normal margins too; see section 3.1.)* **PDF pages stay full size.** The overlays cover them while visible; only EPUBs get the extra top and bottom margins
   (Section 3.1's rule that the page size never changes holds for both).
2. **M2 is signed off.** The riffle scrubber is milestone M2b; EPUB links and footnote popovers follow it.
3. **Flags use each bookmark's own colour** (the five `BookmarkColor`s). The default colour is the ribbon's red, so on
   the strip it is drawn in another colour, and no flag ever matches the ribbon.
4. **Landing follows the turn style:** the multi-sheet turn for Curl, a slide for Slide, a scroll for Scroll.
5. **Paper sounds are generated** by our own script (MIT), not recorded samples. *Changed 2026-10-21:* the owner
   recorded the sounds instead (ADR-0025).

Also agreed in the plan: the strip maps to the reader's real units (single pages, spreads, cover alone) and is mirrored
for right-to-left books; thumbnails have their own small cache (the page cache only holds pages near the reader); the
ribbon's idle sway settles after a few seconds so a still strip stops drawing.

---

## 1. Context

The Riffle Scrubber is FlowFolio's signature reader navigation control: the bottom navigation strip in the reader. It
shows the book's fore edge front-on, and the reader riffles through the pages by sliding a satin ribbon.

The mock is the source of truth for visual design, motion and physics, except for Section 3. Its physics constants,
geometry, easing curves and draw order are deliberate and are carried over.

## 2. The feature in one paragraph

At the bottom of the reader floats a strip showing the book's fore edge as if seen in cross-section from the front: the
front cover board on the left, the back cover board on the right, and every sheet standing edge-on between them. A red
satin ribbon hangs from a red pin at the reader's position. Dragging the ribbon opens a gap in the fore edge; leaves
spring across the gap, bowing in their direction of travel, while a glass loupe above the finger riffles miniature
previews of the spreads being passed. Releasing lands on that spread with a fast multi-sheet page turn. Coloured
bookmark flags stick out of the top of the page block and are tap-only shortcuts. All controls auto-hide after a
user-chosen delay, leaving a clean full-screen book.

## 3. Deliberate deviations from the mock

### 3.1 Controls overlay the page; the page never resizes
- The page is laid out at full-screen size at all times. All chrome (top bar, strip, loupe, settings popover) is drawn
  on top as frosted-glass overlays. Hiding the chrome only fades and slides the overlays away.
- Edge to edge: system bar insets are applied only to the overlays, never to the reader content.
- A bottom page margin about the strip's height and a top margin covering the top bar (EPUBs only, decision 1). *Withdrawn 2026-10-21:* the owner found the space wasted; EPUB margins are back to their earlier size and the bars may cover text while they show.
- An automated test proves the reader content's measured size is identical with chrome visible and hidden.

### 3.2 Precision: fine scrubbing, go-to-page and nudge arrows
1. **Fine scrubbing by moving up off the ribbon.** The finger's height above the grab point sets the speed:
   under 56 dp 1× (absolute), 56–112 dp ½× ("Half speed"), 112–168 dp ¼× ("Quarter speed"), over 168 dp 1/10×
   ("Fine scrubbing"). Below 1× the ribbon moves by `dx × factor` (relative), so it never jumps. One light haptic per
   threshold crossed; the speed label shows in the loupe.
2. **Go to page:** tapping the page number in the loupe or strip header opens a numeric field that accepts printed page
   numbers, including roman front matter when the book has a page list. Jumps use the landing animation.
3. **Nudge arrows:** ‹ › buttons at the two ends of the strip step one spread; always shown with the dock (owner,
   2026-10-22: beside the ribbon they got in the way of dragging it on).

### 3.3 Bookmark flag stacking
- Flags within 14 dp of each other merge into a stack: a flag with a count badge, in the colour of the first flag.
- Tapping a stack opens a small horizontal popover listing its flags (colour, page, optional note); tapping one jumps.
- Flags and stacks are tap-only.

### 3.4 Auto-hide timer rules
- Resets on any interaction with the chrome (strip, ribbon, flags, top bar, popovers).
- Does not reset on page turns.
- Paused while dragging the ribbon, while a popover or the go-to-page field is open.
- Tapping the middle of the page toggles the chrome; tapping the left or right edge turns a page without showing it.

### 3.5 Real loupe thumbnails
Low-resolution renders of the real pages, with the mock's skeleton only as a placeholder while one loads. Prefetch
around the finger and in the direction of travel.

## 4. Visual specification

Colours and fonts come from the FlowFolio design tokens (Fraunces, Instrument Sans, the dark and light palettes).

### 4.1 The fore edge
- Cover boards about 12 dp wide, overhanging the block about 5 dp top and bottom, with cloth texture, a highlight line
  and a thin gilt line; tiny "FRONT" and "BACK" labels under them. Board colour from the book's cover (dominant
  colour, main and dark shades), else the theme accent.
- One leaf per sheet (`ceil(pages / 2)`), each a vertical line the full height of the block, warm cream in both themes
  (dimmer in dark), alternating subtly in brightness. If leaves exceed `stripWidthPx / 3.2`, draw one per N sheets but
  keep the mapping exact. Each leaf is drawn 70% as wide as its spacing, so pages read one by one (owner, 2026-10-21;
  was 1.6 px and 94%).
- The dark book interior behind the leaves shows only where they separate. The block darkens toward its top, bottom
  and both covers.
- Chapter bleed bands: short coloured segments on each chapter's leaves at a band height stepping down chapter by
  chapter (eight bands, then repeat), about 35% opacity; glossary and index neutral grey; none without chapters.

### 4.2 The pin and ribbon
A pin in the ribbon's satin reds rises from the top of the ribbon, through the fore edge and into the pages, to a point a fifth of the
way down the block, at the current position; the open gap shows it while riffling (owner, 2026-10-21: no pin head and
no floor shadow under the block). The satin ribbon hangs from the pin with a forked tail, a satin
gradient with a central sheen, and white ‹ › grip chevrons; it sways gently at rest, widens about 2 dp and glows while
held, with a soft red glow in dark theme.

### 4.3 Flags
Rounded tabs standing out of the top of the block (each bookmark's own colour, decision 3). With a mouse or trackpad,
hovering lifts a flag 4 dp, outlines it, and the loupe previews its spread without moving the book.

### 4.4 The loupe
A frosted-glass card above the strip with a pointer tail aimed at the ribbon or the hovered flag: a two-page miniature
spread with a gutter shadow, the chapter label, the printed page range and a progress bar. It follows the finger with
a slight lag and tilts up to 7° against the direction of motion.

## 5. Interaction specification

### 5.1 Geometry and the gap
- Strip x ↔ position, clamped to the book (ADR-0022 maps it to the reader's units).
- Engagement `e` springs toward 1 while dragging, 0.55 while travelling after a flag tap, 0 at rest.
- Gap displacement around the pin: `G × (tanh((x − fx) / r) − linear end correction)`, `G = 4 + 48e`,
  `r = 22 + 16e` (dp) (owner, 2026-10-21, a wider, deeper fan; was `4 + 30e` and `20 + 14e`).

### 5.2 Leaf physics
- Each leaf's x is a spring toward its target (`k = 280`, `c = 20`: softer and a little underdamped so leaves trail the
  pin and settle with a small sway, owner 2026-10-21; was `k = 700`, `c = 22`), two substeps per frame with the real frame delta,
  independent of frame rate (the Fold runs at 120 Hz).
- Bow in the direction of travel: `clamp(velocity × 0.03, ±14)`, a quadratic curve whose middle leads.
- Leaves near the gap and fast-moving leaves are slightly brighter.

### 5.3 Gesture rules
- Only the ribbon starts a riffle: 26 dp either side of it, from just above the pin to the tail, keeping the grab
  offset.
- Flags are tap-only; a touch on a flag never riffles; the tap cancels after 10 dp of movement. A flag tap travels: the
  ribbon glides to the destination over about 900 ms with `e = 0.55`, while the page lands with the multi-sheet turn.
- Tapping the page block only wiggles the ribbon for 600 ms.
- Overlays consume their own touches.

### 5.4 Landing
Up to 7 sheets toward the target, intermediate pages sampled evenly: 520 ms per sheet, 70 ms stagger, easing
`cubic-bezier(.45, .05, .25, 1)`, with shading. A single-spread move is one 700 ms turn. The final turn uses the page
curl; intermediate sheets may use a flat flip (decision 4 for Slide and Scroll).

### 5.5 Loupe riffle
Each change of previewed spread flips a mini sheet over the loupe's spine in the direction of travel, lasting
`clamp(timeSinceLastChange × 2.4, 110, 320)` ms, at most 6 in flight.

### 5.6 Haptics and sound
- Haptics only for real drags: a light tick every 4 spreads, a stronger confirm at a chapter start, a double tick at a
  flag, a light tick at each fine-scrub threshold (constants per API level in ADR-0023).
- Sound (ADR-0025, the owner's recordings): while dragging, a riffle loops in the drag's direction, its rate and
  loudness following the speed, fading out when pages stop or the ribbon is let go; each landing plays a page flip in
  its direction. Respect silent and vibrate modes. Every ordinary page turn (Curl, Slide) plays the flip too (ADR-0026).
  "Page turn sound" and "Riffle sound" each have a switch and a volume slider, and "Riffle haptics" a switch, in "Aa"
  and in Settings.

### 5.7 Auto-hide and immersive mode
- 3 s, 5 s (default), 8 s, 15 s or Never, set in the reader's "Aa" panel and in app settings, kept in DataStore.
- A 2 dp countdown line under the strip header.
- On hide, the overlays fade and slide away and the system bars hide (transient bars by swipe). The page never moves.

## 6. Accessibility
- The strip is a slider with a state description ("Page 216 of 842, Chapter 7, Membrane Transport").
- Custom actions: next and previous spread, next and previous chapter, go to page, one per bookmark.
- Flags have content descriptions; every touch target is at least 48 dp (invisible hit areas where needed).
- Reduced motion (animator scale 0): no leaf animation, a crossfade instead of the multi-sheet turn, a static loupe.
- Keyboard and D-pad: arrows step spreads, Page Up and Down move 10, Enter toggles the chrome.

## 7. Performance and architecture
- Module `:feature:reader:riffle`: a stateless `RiffleScrubber(state, onLand, onFlagTap, ...)` and a
  `RiffleScrubberState`.
- One `Canvas`; leaves batched into about 18 shade buckets of reused `Path`s; no allocations per frame (preallocated
  `FloatArray`s in a `withFrameNanos` loop); never one `Animatable` per leaf.
- The frame loop runs only while something moves or shows.
- State machines: `ChromeState` (Visible, Hidden), `RiffleState` (Idle, Dragging, Landing, Travelling), `ScrubSpeed`
  (Normal, Half, Quarter, Fine).
- Targets on the unfolded Fold at 120 Hz: strip draw under 4 ms per frame while riffling; no dropped frames in a
  macrobenchmark of a 5 s riffle across a 1,200-page book; loupe thumbnail first paint under 50 ms from cache.
- Works folded and unfolded, in portrait and landscape, on tablets and phones; hit areas stay full size on narrow
  screens.

## 8. Testing and acceptance
- Unit tests: x ↔ position at both ends for odd and even page counts; fine-scrub factors and relative movement; flag
  hit-testing, the 10 dp tap cancel and 14 dp stacking; the auto-hide state machine with a virtual clock.
- Screenshot tests (at rest, mid-riffle, stack open; dark and light; tool in ADR-0024). A Compose UI test that a drag
  starting on a flag does not move the position and one on the ribbon does. A test that the reader's content size is
  unchanged when the chrome toggles.
- Acceptance, each item demonstrated on a device and reported as verified or not: the strip matches the mock in both
  themes with cover-derived boards; riffling with spring leaves, bowing, needle, loupe flips, haptics and sound; fine
  scrubbing reaches any spread in an 842-page book; go-to-page with printed numbers; nudge arrows; flags and stacks jump
  on tap and never drag; the landing turn and the reduced-motion crossfade; auto-hide rules, with no movement or
  re-pagination when the chrome toggles; TalkBack actions; performance targets with macrobenchmark numbers.

## 9. Dependencies on the rest of the app
The module is built against interfaces (`BookGeometry`, a bookmark source, `PageThumbnailProvider`) with a demo screen
on fake data equivalent to the mock's sample book (Cellular Biology, 842 pages, 17 chapters plus glossary and index,
five flags), then wired into the real reader.
