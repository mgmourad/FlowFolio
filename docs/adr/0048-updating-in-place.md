# ADR-0048: Update FlowFolio from its own releases page

- **Status:** Proposed
- **Date:** 2026-11-03
- **Deciders:** product owner, lead engineer
- **Phase:** 3 (M5)

## Context

FlowFolio is published on GitHub Releases and, later, F-Droid; never the Play Store (owner, 2026-11-02).
Nothing else will therefore tell a reader that a newer build exists, and a sideloaded APK has no
update path of its own. The owner asked (2026-11-03) for a "check for updates" button with the
option to download and install updates automatically.

Two facts shape what "automatically" can mean:

- **No app can install another silently** unless it is the device owner or a system app. FlowFolio is
  neither, and should not be: an app that could replace itself without asking is a far larger thing to
  trust than a reader.
- Everything about updating is a network request to a third party, which ADR-0017 says must be
  deliberate, identified in Settings, and carry nothing about the reader.

## Options considered

1. **Nothing in the app.** The reader watches the releases page. Honest, costs no code, and in practice
   means running an old build for months.
2. **A notification that a build exists, and a link out.** Small and safe, but leaves the reader to find
   the APK, download it and locate it in Files.
3. **Check, fetch, and hand to Android's installer.** The app asks the releases page for the newest
   build, compares it to its own `versionCode`, downloads the APK into its cache and starts the system
   installer. Android asks the reader before anything is installed.

## Decision

Option 3, with the install step always Android's.

`Updates` (`:core:data`) reads `api.github.com/repos/mgmourad/FlowFolio/releases`, takes the highest
build with an APK asset, and compares it to the installed `versionCode` read from the package manager.
Pre-releases count, because FlowFolio ships alphas and canaries on purpose (ADR-0042) and a reader on
an alpha wants the next alpha. The build number is parsed out of the asset's **filename**, which
ADR-0042 fixes as `flowfolio-<version>-<versionCode>.apk`; that is sturdier than parsing a version
string whose shape changes between channels.

The APK is downloaded into `cacheDir/updates/`, its length checked against the size the releases page
gave, and handed to `ACTION_VIEW` through a `FileProvider` scoped to that one folder. The app declares
`REQUEST_INSTALL_PACKAGES`; where the phone has not granted it, the releases page is opened instead.

The setting is called **"Fetch updates automatically"**, not "install automatically", and its own
description says Android asks before installing. Naming it otherwise would promise something no app
can do.

## Consequences

- Positive: a reader who sideloaded once is told about every build afterwards, and is two taps from it.
  Nothing about them is sent: the releases list is a public, unauthenticated GET with no identifier.
- Negative: `REQUEST_INSTALL_PACKAGES` is a sensitive permission and F-Droid flags apps that hold it.
  It is legitimate here — self-updating is why the permission exists — but it has to be explained in
  the listing.
- **Blocked until the repository is public.** `api.github.com/.../releases` answers 404 to an
  unauthenticated request against a private repository, so the check reports "Could not reach the
  releases page" today. Verified on the device 2026-11-03. Nothing in the app can fix this; the
  repository has to be made public, which is the owner's to decide and is needed for F-Droid anyway.
- Accepted risk: the cache holds one APK (~200 MB) after a download. It is deleted when the next
  download starts, cleared by Settings → Storage → Clear cache, and Android may evict it at any time.
- Follow-up (owner, Phase 3): make the repository public before the next release, or the update check
  cannot work for anyone.
