# FlowFolio 0.3.0-alpha.3 — release notes

Body for the GitHub pre-release. Title: **FlowFolio 0.3.0-alpha.3 (build 300003)**.
Delete this file once published.

---

**FlowFolio is a reader that treats a book like a book.** This is the first build anyone can
actually install — the repository was private until today, and the two earlier pre-releases were
withdrawn without ever being downloaded.

> **If you sideloaded an earlier build, uninstall it first.** It was signed with a different key, so
> Android treats it as a different app and will refuse to replace it. From this build on, the
> signing key does not change, so every later version updates in place.

## What it does

A textbook PDF is typeset for A4. Your phone is not A4, so reading one means pinch, zoom, pan,
repeat. **Dynamic Flow** converts the PDF into reflowable text **on the device** — about a minute
for a 400-page textbook — keeping figures, tables, equations, code blocks, footnotes and printed
page numbers. The original page stays one tap away, and your highlights follow you between the two.

Nothing is uploaded. There is no server.

- **Pages that turn** — a real 3D curl, and a two-page spread when you unfold a foldable
- **Riffle scrubber** — drag to flick through, with chapters and your ribbons marked on the bar
- **Highlights, notes and ribbons** in nine colours, plus **VoiceMark** for a spoken note
- **A full English dictionary on the device**, no connection needed, and on-device translation
- **Reading Atmosphere** — pair the page with rain, a fire, a café, a file of your own, or a stream
- **Reading goals** by day, week or month, with reminders
- **Four themes** including true AMOLED black, seven reading fonts, page colours and margins

No accounts, no analytics, no trackers. Your library never leaves the phone.

## In this build

- **Settings → Updates works.** The app can check this page for a newer build and fetch it. Android
  always asks before installing anything; that step stays yours.
- **A failed update check says why.** It used to blame your connection for every failure, including
  a prompt and perfectly correct answer from GitHub.
- **Release APKs are signed with APK Signature Scheme v3**, which is what makes a future key
  rotation possible at all.

## Installing

Download the APK, check it if you like, and open it — Android will ask before installing.

```bash
shasum -a 256 flowfolio-0.3.0-alpha.3-300003.apk
```

**Requires Android 10 (API 29) or newer.** This is an alpha: the converter will mangle some
layouts, and a bug report naming the book is the most useful thing you can send.

Full detail in [CHANGELOG.md](https://github.com/mgmourad/FlowFolio/blob/main/CHANGELOG.md).
