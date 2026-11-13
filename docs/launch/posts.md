# FlowFolio launch posts

Ready to paste. Attach `flowfolio-banner.png` (or the `.jpg`) wherever an image is allowed.

- **Release link:** https://github.com/mgmourad/FlowFolio/releases/latest
- **Repository:** https://github.com/mgmourad/FlowFolio
- **Version at launch:** 0.7.0-beta.5, Android 10 or newer, Essential and Full editions.

Every claim below is checked against the app as it ships. Keep it that way if you edit: no "no tracking" (ML Kit
translation, used only when you translate, reports anonymous usage to Google), and say "beta".

---

## Reddit

Check each subreddit's self-promotion rules before posting (several allow it only on set days or in a weekly
thread), post from your own account, and stay in the thread to answer questions for the first few hours.

### r/androidapps

**Title:** [DEV] FlowFolio: a free, open-source reader that reflows any PDF to fit your phone, offline

I built FlowFolio because reading textbooks on a phone meant pinching and panning across every line of an A4 page.

**What it does**
- **Dynamic Flow:** turns a PDF into a book that reflows to your screen, on the phone. Headings, lists, tables,
  code, equations, figures and page numbers survive. The original stays as it is; one switch goes back and forth.
- **Scanned books:** on-device OCR in English and Arabic. Pages it can't read well stay as pictures rather than
  turning into nonsense.
- **Narrator Mode:** natural offline neural voices (Kokoro and Piper) that read aloud, turn the pages, and keep going
  with the screen off.
- **A real 3D page curl**, two-page spreads on wide screens, and a "riffle" bar to flick through the book.
- Highlights, notes, ribbons, and **VoiceMarks** (record a thought instead of typing it).
- Offline dictionary (Wiktionary + WordNet), on-device translation, in-book search.
- Reading goals with streaks, home-screen widgets, ambient sounds, four themes including AMOLED.

**What it doesn't do:** no account, no ads, no subscription. Your library stays on the phone. It's MIT-licensed.

Two editions: **Essential** (smaller; downloads the OCR, voice and dictionary packs when you ask) and **Full**
(everything inside, ready offline). APKs for 64-bit ARM, 32-bit ARM and universal.

It's a beta, so I'd love bug reports and feature ideas: https://github.com/mgmourad/FlowFolio/releases/latest

### r/GalaxyFold (also fits r/foldables)

**Title:** I made a reading app designed for the Fold first: one page folded, a two-page spread unfolded

The Fold is the best reading device I own, but most readers treat the inner screen as a big phone. FlowFolio was
designed on the Fold from day one:

- **Folded:** one page, sized for the cover screen. **Unfolded:** a two-page spread with a real 3D page curl.
- **Dynamic Flow** reflows textbook PDFs so the text fits whichever screen you're on: no pinching on the cover
  screen. A 959-page physics textbook converts in about 3½ minutes on a Fold, on the device.
- The unfolded layouts use the width: side rail, list and page side by side in Settings, wider library.
- Offline narrator voices, highlights and notes, offline dictionary, widgets.

Free and open source (MIT), no account, no ads. Beta, Android 10+:
https://github.com/mgmourad/FlowFolio/releases/latest

Tested on the Fold's dimensions throughout; feedback from other Fold models is very welcome.

### r/opensource and r/fossdroid

**Title:** FlowFolio: an MIT-licensed Android reader that converts PDFs to reflowable books entirely on-device

Most readers show a PDF as a picture of a page. FlowFolio converts it on the phone into a reflowable EPUB: no server,
nothing uploaded.

**Under the hood**
- PDFium through its own JNI bridge; the converter is a plain JVM module (reading order, regions, tables, lists,
  code by column, headings and links) writing EPUB 3 that passes EPUBCheck on its test corpus.
- OCR for scanned books: PaddleOCR on ONNX Runtime for English; for Arabic, Tesseract 5.5 built from source and
  checked against PaddleOCR, keeping a page as text only when the two engines agree.
- Offline TTS with sherpa-onnx (Kokoro and Piper voices).
- Kotlin, Jetpack Compose, Room, Hilt, Glance widgets. No GPL code or model weights in the app (licence matrix in
  the repo).
- 58 architecture decision records in `docs/adr/`, if you like reading why things are the way they are.

No account, no ads; the library never leaves the device. Releases are signed and built by a public GitHub workflow,
with SHA-256 sums next to every APK.

Repo: https://github.com/mgmourad/FlowFolio. Issues and PRs welcome.

### r/ebooks (also fits r/ereader and r/books)

**Title:** A free Android reader that turns hard-to-read PDFs into proper reflowable books

If you have PDFs that are miserable on a phone (textbooks, papers, scanned old books), FlowFolio converts them into
reflowable books on the phone itself. You choose the font, the size and the margins, and it reflows. Figures,
tables and footnotes come along.

It also reads EPUBs (including fixed-layout), reads aloud with natural offline voices, and has highlights, notes,
an offline dictionary and reading goals. Free, open source, no account:
https://github.com/mgmourad/FlowFolio/releases/latest

### r/GradSchool, r/college, r/students (check each sub's rules first)

**Title:** Made a free app that makes textbook PDFs readable on a phone (reflows them, reads them aloud)

Reading a textbook PDF on a phone usually means zooming and panning on every line. FlowFolio reflows the PDF on your
phone so the text fits the screen: figures, tables, equations and page numbers included, original untouched. It can
read chapters aloud with offline neural voices, search the book, define a word, and keep your highlights and notes
in one notebook you can export.

Free, open source, no account, works offline: https://github.com/mgmourad/FlowFolio/releases/latest

### r/arabic (Arabic post)

**العنوان:** تطبيق مجاني ومفتوح المصدر لقراءة الكتب العربية المصوّرة على أندرويد

صنعتُ FlowFolio، قارئ كتب لأندرويد يحوّل ملفات PDF إلى نص يتكيّف مع حجم الشاشة، على الهاتف نفسه ومن دون إنترنت.

- **الكتب العربية المصوّرة:** يتعرّف على النص العربي بمحرّكين معًا (Tesseract وPaddleOCR)، ولا يحوّل الصفحة إلى نص
  إلا إذا اتفق المحرّكان؛ وإلا بقيت صورة كما هي بدل أن تمتلئ بأخطاء.
- **من اليمين إلى اليسار:** تُفتح الكتب العربية بالاتجاه الصحيح، وتُقرأ الأعمدة بالترتيب الصحيح.
- قراءة صوتية، وتظليل وملاحظات، وقاموس وترجمة على الجهاز.

ما زال في مرحلة تجريبية: الطباعة الحديثة تُقرأ جيدًا، أما الطبعات القديمة جدًا والمخطوطات فتبقى صورًا. يسعدني أن
تجرّبوه على كتبكم وتخبروني بالنتيجة.

مجاني ومفتوح المصدر، بلا حساب ولا إعلانات: https://github.com/mgmourad/FlowFolio/releases/latest

---

## Hacker News

**Title:** Show HN: FlowFolio – Reflow any PDF into a readable book, on an Android phone

**Text:**

FlowFolio is an Android reader that converts PDFs into reflowable EPUBs on the device: no server, nothing uploaded.
I started it because textbooks on a phone are unreadable: you pinch and pan across every line.

The converter is a plain JVM module over PDFium. It finds the document's design system (body text, heading styles,
folios), regions and reading order, then rebuilds blocks: lists by marker and depth, code by column, ruled and
unruled tables, superscripts, headings and internal links. It writes EPUB 3 with a sidecar mapping PDF pages to
places, so highlights survive the switch between the original and the reflowed view. A 959-page physics textbook
converts in about 200 s on a Galaxy Z Fold.

Scanned books go through OCR: PaddleOCR on ONNX Runtime for English. For Arabic, Tesseract reads and PaddleOCR checks,
and a page becomes text only when the two agree (measured on letter pairs, since PaddleOCR garbles a letter in most
Arabic words). Pages that fail stay as images.

Also: offline neural TTS (sherpa-onnx with Kokoro), a 3D page curl, an offline dictionary, and Glance widgets.
MIT-licensed, with 58 ADRs in the repo explaining the decisions.

https://github.com/mgmourad/FlowFolio

---

## X / Twitter (thread; attach the banner to the first post)

1/ I built FlowFolio: a free, open-source Android reader that turns any PDF into a book that reflows to your screen.
On the phone. Offline. No account. 📖

2/ Dynamic Flow converts the PDF on the device. Headings, tables, code, equations, figures and page numbers all come
along. One switch flips between the original and the reflowed book.

3/ Scanned books too: on-device OCR in English and Arabic. If a page can't be read reliably, it stays a picture
instead of turning into gibberish.

4/ Narrator Mode reads aloud with natural offline voices, turns the pages, and keeps going with the screen off.

5/ Built Fold-first: one page folded, a two-page spread with a real page curl unfolded. Works on any phone or tablet
running Android 10+.

6/ Highlights, notes, VoiceMarks, an offline dictionary, reading goals and home-screen widgets. MIT-licensed, beta,
feedback welcome 👉 https://github.com/mgmourad/FlowFolio/releases/latest

---

## Mastodon / Bluesky (single post; attach the banner)

FlowFolio is a free, open-source Android reader that reflows any PDF to fit your screen, converted on the phone and
offline. It also does scanned books (English and Arabic OCR), offline neural narration, a real page curl, highlights,
notes and an offline dictionary. No account, no ads. MIT. Beta, so feedback is welcome.

https://github.com/mgmourad/FlowFolio

#Android #OpenSource #FOSS #eBooks #PDF #Accessibility

---

## LinkedIn

I'm releasing FlowFolio, a free and open-source Android reader that solves a problem I kept running into: PDFs, and
textbooks especially, are nearly unreadable on a phone.

FlowFolio converts a PDF into a reflowable book on the device itself, so the text fits the screen, with figures,
tables, equations and page numbers intact. It reads scanned books in English and Arabic with on-device OCR, reads
aloud with natural offline voices, and was designed foldable-first: one page folded, a two-page spread unfolded.

A few things I'm proud of:
• Everything runs on the phone. No server, no account, no ads.
• The Arabic OCR checks two engines against each other and keeps a page as text only when they agree.
• Every significant decision is written down as an architecture decision record (58 so far).
• Releases are built and signed by a public CI workflow, with checksums for every file.

It's in beta and MIT-licensed. I'd welcome feedback from anyone who reads on their phone:
https://github.com/mgmourad/FlowFolio

#Android #OpenSource #Accessibility #EdTech #Kotlin

---

## Product Hunt

- **Name:** FlowFolio
- **Tagline (60 chars max):** Any PDF, reflowed to fit your phone, on-device and offline
- **Topics:** Android, Books, Open Source, Education
- **Description:** A free, open-source Android reader that converts PDFs into reflowable books on the phone:
  textbooks, papers and scanned books (English and Arabic OCR). Offline narration, a real page curl, highlights,
  notes, an offline dictionary and reading goals. No account, no ads.
- **First comment:**
  Hi! I made FlowFolio because textbooks on a phone meant pinching and panning across every line. It converts the
  PDF on your device into a book that reflows to the screen, and keeps the original one tap away. It's open source
  (MIT) and in beta, so tell me what breaks and what you'd want next.

---

## XDA Forums (Android Apps & Games thread)

**Title:** [APP][10+][FREE][OPEN SOURCE] FlowFolio: reflow any PDF on-device, OCR, offline narrator

**Body:**

FlowFolio is a reading app for PDFs and EPUBs that converts PDFs into reflowable books on the phone.

**Features**
- Dynamic Flow: on-device PDF to reflowable EPUB (headings, tables, code, equations, figures, page numbers)
- Scanned books: OCR in English (PaddleOCR) and Arabic (Tesseract + PaddleOCR cross-check)
- Narrator Mode: offline neural TTS (Kokoro, Piper) with background playback
- 3D page curl, two-page spreads, riffle scrubber, foldable-first layouts
- Highlights, notes, ribbons, VoiceMarks, notebook export
- Offline dictionary (Wiktionary + WordNet), on-device translation, in-book search
- Reading goals, widgets, ambient sounds, four themes

**Downloads** (https://github.com/mgmourad/FlowFolio/releases/latest)
- *Essential* (packs on request) or *Full* (everything inside)
- `arm64-v8a` for most phones, `armeabi-v7a` for older 32-bit phones, universal for anything else
- Full edition files end in `.full.apk`. SHA-256 next to each file. Either edition updates the other in place.

**Requirements:** Android 10+. **Licence:** MIT. **Source:** https://github.com/mgmourad/FlowFolio

Status: beta (0.7.0-beta.5). Bug reports welcome here or on GitHub Issues.

---

## Posting checklist

- [ ] The release page shows all twelve files and its notes read well.
- [ ] Post the banner image, not just the link, where the platform allows.
- [ ] Space the Reddit posts over a few days; don't cross-post the same text to several subs at once.
- [ ] Answer questions in the first hours; note the feature requests on GitHub Issues.
- [ ] If F-Droid is decided, add the link here before the second wave.
