# Phase 4 M4 plan: syntax highlighting for code (Dynamic Flow)

- **Status:** Done: built and closed with Phase 4, signed off by the owner 2026-11-06 (`phase4-signoff.md`).
- **Phase plan:** `PLAN.local.md`, owner's on-the-fly tweak during Phase 4 M3

## Goal

Code in a Dynamic Flow book — both inline `<code>` and whole `<pre>` listings — is coloured by language, with the
language detected automatically from the code itself. No reader has to pick a language.

## Scope

- **Detection, automatic.** Given a code block (or an inline run), decide the language from the text alone: keyword
  frequency, comment markers (`#`, `//`, `<!--`), and distinctive tokens. No user input, no filename (Dynamic Flow
  code has no filename).
- **Highlighting.** Tokenise the code into keywords, strings, comments, numbers, types/identifiers and punctuation,
  and colour each with the theme's palette.
- **Both forms.** Inline `<code>` and block `<pre class="ff-code">`, so a mention of `print(x)` in prose and a whole
  listing are both coloured.

## Where it lives

The converter emits plain text (standard EPUB); the highlighting happens in the **reader**, so the EPUB stays clean
for other reading systems.

- **Parse** — `ChapterParser` already turns `<pre>` into a `TextStyle(preformatted = true)` text block and `<code>`
  into an inline code span. It needs to carry the code's text to a highlighter.
- **Highlight** — a new `CodeHighlighter` (in `:core:reflow`, pure JVM): detect the language, tokenise, and return
  the code as a `Spannable` with colour spans.
- **Draw** — `PageDrawer` draws preformatted text with one ink colour. It will draw the spans instead, so each token
  gets its colour. Inline code is already a span (`CodeFace`), so its colour comes from the same highlighter.
- **Colours** — from the theme's `Palette` (accent, item, hint, and a comment/number tone), so highlighting follows
  the paper/theme, not hardcoded editor colours.

## Design decisions

1. **Language set.** Start with the languages Dynamic Flow actually produces: Python, JavaScript/TypeScript, Java,
   Kotlin, Shell/Bash, C/C++, SQL, HTML/CSS/XML. A detector's keyword table is cheap; adding one later is a table
   entry.
2. **Detection heuristic vs model.** A hand-rolled detector (score by keyword hits, comment markers, indentation) is
   dependency-free and fast enough. A model is overkill for colouring; revisit only if detection proves unreliable.
3. **Tokenizer.** A regex/state-machine tokenizer (strings and comments first, then keywords, numbers, identifiers),
   not a full parser — colouring needs tokens, not an AST.
4. **Dependency.** None. A syntax-highlighting library would add a dependency for a hand-rollable job, and no
   candidate is approved. If the owner prefers a library, that is a separate ADR.

## Milestones

1. **Highlighter core (JVM).** `CodeHighlighter` with the detector + tokenizer, and unit tests per language
   (keywords, strings with escapes, comments, numbers, a line comment vs a string).
2. **Wire into the reader.** Colour `<pre>` listings in `PageDrawer`, and inline `<code>` runs, from the theme
   palette. Verify a code-heavy book (*Think Python*, *Pro Git*) in Dynamic Flow.
3. **Polish.** Handle a listing the detector can't place (fall back to the plain code colour, no wrong colours), and
   a listing too wide to wrap.

## Done when

A Python listing shows keywords, strings and comments in the theme's colours; a shell listing is detected as shell,
not Python; inline code in prose is coloured; and an unrecognised listing falls back to the plain code face.
