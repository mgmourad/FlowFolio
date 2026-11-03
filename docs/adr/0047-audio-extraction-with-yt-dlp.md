# ADR-0047: Extract a video page's audio with yt-dlp, embedded

- **Status:** Accepted
- **Date:** 2026-11-03
- **Deciders:** product owner, lead engineer
- **Phase:** 3 (M5)

## Context

A Reading Atmosphere can play a sound of the reader's own, given as an address (ADR to follow for Atmosphere
itself). `StreamResolver` already resolves everything a site publishes openly: a media file, an `.m3u`/`.pls`
playlist, an `og:audio` tag, a podcast `<enclosure>`, an `<audio>` element, and media addresses sitting in a page's
own player data (including the HLS manifests that live streams publish). Verified on the device against an Icecast
mount with no file extension, a `.pls` that chains to that mount, and a page with no audio at all.

What that cannot do is read an address a site deliberately keeps behind its own code: a video page hands its player
a scrambled address to be unscrambled by that site's JavaScript, so there is nothing in the page for a generic
reader to find. The owner asked (2026-11-02, restated 2026-11-03) for yt-dlp to be baked in for audio from videos,
explicitly **not** for that site's live streams — a live stream should be taken from any other site, where the
generic resolver already handles it.

The licensing is the thing that decides the shape of this:

- **yt-dlp's source is the Unlicense** (public-domain dedication), which ADR-0003 allows. Its *release binaries* are
  GPLv3+ because of what PyInstaller bundles into them; those are not used here.
- The GPL in every comparable Android project comes from **ffmpeg** (and from `youtubedl-android`, which is GPLv3).
  We need neither: FlowFolio only wants an *address*, which Android's own `MediaPlayer` then plays. Nothing is
  downloaded, demuxed or transcoded in the app.
- **Chaquopy**, which embeds CPython in an Android build, is MIT. CPython itself is the PSF licence.

So an embedded-Python path is clear of GPL, where the obvious ready-made library is not.

## Options considered

1. **Port the extractors to Kotlin.** No new dependency and no Python. But yt-dlp's value *is* its extractors and the
   people who keep them current as sites change; a hand-written copy would be enormous on day one and wrong by the
   end of the month. Rejected.
2. **`youtubedl-android` (yausername).** Ready-made and well used. **GPLv3**, which ADR-0003 forbids outright since
   everything ships inside the app. Rejected on licence.
3. **A third-party `yt-dlp-android` Maven artifact.** An MIT wrapper over Chaquopy. Saves a little wiring, but adds a
   dependency of unclear provenance whose free build is deliberately feature-gated against a paid one. Rejected: the
   wiring it saves is about forty lines.
4. **Chaquopy + yt-dlp from pip, in our own module.** MIT + PSF + Unlicense, no ffmpeg, and yt-dlp is updated by
   changing one version in the catalogue. Costs an embedded Python runtime in the APK and a pip step at build time.

## Decision

Option 4. A new Android library module **`:core:extract`** applies the Chaquopy Gradle plugin, pip-installs yt-dlp at
build time, and exposes `YtDlpExtractor`, which asks yt-dlp for the best **audio-only** format's address and returns
it. `:core:data` declares the `AudioExtractor` interface it implements (with `NoAudioExtractor` for builds that have
none), and `StreamResolver` asks the extractor **only after** its own generic resolution has come up empty — the open
web is cheaper, covers radio and podcasts, and already handles live HLS.

`flowfolio_extract.py` returns `None` for anything live, on purpose: a live address goes stale while it is being
read, and the generic resolver already plays the HLS that live pages publish. That is the owner's "videos, not live
streams" line, enforced in the one place that can see it.

Python is started on first use rather than at app start, so a reader who never uses a stream address never pays the
second it costs.

## Consequences

- Positive: the owner's stream addresses work for video pages without FlowFolio learning any site's internals;
  keeping up with sites is a version bump in `libs.versions.toml`. The app stays MIT and GPL-free. No ffmpeg, no
  download, no storage: the address goes straight to `MediaPlayer`.
- Negative: **the debug APK grows from 154 MB to 196 MB** — the CPython 3.12 runtime for `arm64-v8a` and `x86_64`
  plus yt-dlp's extractors (5.6 MB compiled). Build time grows by the pip step, which needs a Python 3 on the build
  machine and network access.
- Accepted risk: **32-bit builds have no extractor.** Chaquopy ships Python 3.12 for 64-bit ABIs only. The app still
  packages all four ABIs for PDFium, and on a 32-bit device `Python.start` simply fails, `YtDlpExtractor` answers
  null and the generic resolver carries on — the app does not crash and every open address still plays. Verified by
  inspecting the built APK: `lib/` holds all four ABIs, `libchaquopy_java.so` only the two.
- Accepted risk: **F-Droid reproducibility is unproven.** Chaquopy resolves its runtime and pip-installs yt-dlp
  during the build; F-Droid's build server restricts network access. This needs checking before the first F-Droid
  submission, and may need the wheels vendored into the repository.
- Accepted risk: extraction depends on a third party keeping up with sites, and on what any given site permits. A
  failure is silent by design (null, then the generic result), so the reader sees "Nothing to play at that address"
  rather than an error about internals.
- Follow-ups (owner, Phase 3): decide whether to keep 32-bit ABIs at all now that they are the only ones without the
  extractor; check the F-Droid build before submission.
