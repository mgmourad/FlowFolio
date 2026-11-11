# Handoff prompt: Phase 7 M4, polish and beta readiness (0.7.0-beta.1)

Paste this to the session that takes M4, after M3 is signed off. It assumes nothing from earlier conversations.

---

You are continuing **FlowFolio**, an Android reader (Kotlin, Compose) whose Dynamic Flow converts PDFs to reflowable
EPUBs on the phone. Read `CLAUDE.md` first, then `docs/plans/phase7-plan.md` (M4) and the CHANGELOG's Unreleased
section. Follow CLAUDE.md's working agreement:
- plan before code;
- never claim something works without running it;
- ask before adding any dependency;
- Conventional Commits with the co-author line the conversation gives you;
- verify on `fold8_api37`, folded and unfolded;
- a uniquely named debug APK for every handover, installed on the emulator and the owner's phone (RFGL…) when
  connected;
- CI at the phase boundary.

Phase 7 is the **final phase** and ships as **0.7.0-beta.1**: channel beta per ADR-0050, versionCode 700001, per
ADR-0042's checklist in CLAUDE.md.

## Before you start

Confirm M3 is signed off: English and Arabic scanned books pass the corpus gate in CI, and Tesseract for Arabic is on
the phone. If not, stop and tell the owner. M4 does not start until M3 is done.

## Your task

### 1. The owner's polish batch

Every phase ends with one (`PLAN.local.md`, "How to keep to this"). **Ask the owner for their list first.** Work it in
one batch: verify each item on the emulator, then hand over one debug APK.

### 2. Open issues to close before a beta

Each needs a fix verified on the emulator, or an honest note in CLAUDE.md:
- **Known issue 44:** a full-page cover image overflows the reflowed column.
- **Known issue 45:** taps did not turn pages in the reflowed view. Diagnose before fixing.
- **Known issue 51:** the converter drops a letter at the end of list items, and splits words before a "y", in
  *Learning DevOps*. That needs the corpus baselines and CI.
- **What's new labels:** the "What's new" popup heads its notes with the newest *published* version rather than the
  installed one (seen before canary.3 was published).
- **Silent updates:** an update from a FlowFolio-installed copy should not ask. Check this on the owner's phone with
  the first beta.

### 3. Release housekeeping the plan carries

- **The release key:** a second off-machine copy of the release signing key, in GitHub Actions secrets
  (ADR-0049). The owner does this, because it touches secrets: give them the steps; never print or move key
  material yourself.
- **The launch posts** are written and unsent. Ask the owner whether the beta is the moment.
- **F-Droid:** get the owner's yes or no on a self-hosted F-Droid repository. ML Kit (proprietary), the prebuilt
  PDFium and Chaquopy block the main F-Droid repository. If yes, plan it as follow-up work.
- **The tour:** retake its pictures if any screen they show has changed (known issue 43, no status bar), and add a
  slide for scanned books if the owner wants one.

### 4. Sign-off and the beta release

- Write `docs/plans/phase7-signoff.md` in the style of `phase6-signoff.md`: what was delivered, the exit criteria
  with evidence, and what is left.
- After the owner signs off:
  - set `flowfolio.versionName=0.7.0-beta.1` and `versionCode=700001`;
  - add the CHANGELOG heading;
  - commit and push, then wait for green CI (all jobs);
  - build the release APK and check its signer: `CN=FlowFolio Release`, SHA-256 `59fbae55…`. Never the dev key;
  - tag, then `gh release create --prerelease` with the APK and its `.sha256`;
  - install it on the emulator over the previous release to prove it updates in place;
  - record the hash in CLAUDE.md.

## Remember

- The owner reads results, not process. Say plainly what was verified on a device, what only in tests, and what was
  not checked at all.
- Treat their phone with care: check that FlowFolio is the resumed activity before any input (known issue 22), and
  restore any setting you change.
