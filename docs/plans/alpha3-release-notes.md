# FlowFolio 0.3.0-alpha.3 — draft release notes

Paste as the body of the GitHub pre-release. Set the title to
**FlowFolio 0.3.0-alpha.3 (build 300003)**.

Delete this file once the release is published.

---

> ### ⚠️ One last clean install
>
> This build is signed with the project's **release key**. `0.3.0-alpha.2` was signed with the
> development key, and Android treats a differently signed APK as a different app — so this one
> **will refuse to install over it**. Uninstall FlowFolio first, then install this.
>
> Anything in the old install goes with it: books, highlights, notes, reading positions.
>
> **This is the last time.** From here on the release key is the only one that ships, so every
> future build updates in place.

## Updates now work

FlowFolio is open source and the repository is public, which means **Settings → Updates** can
finally reach the releases page. It tells you which build you are on, checks for a newer one and
can fetch it for you. Android always asks before it installs anything — that last step stays yours.

While the repository was private the releases API answered 404 to every check, so no version of
this feature could ever have found a build.

## Fixed

- **A failed update check now says *why* it failed.** It used to report every failure as "Could not
  reach the releases page", including a prompt, perfectly correct answer from GitHub. It now
  distinguishes no connection, no releases page, rate limiting, an unexpected answer, and a download
  that arrived short — each with its own message, because each needs something different from you.

## For developers

- Release builds now **fail** rather than silently produce an unsigned APK when the signing key is
  missing. The development key signs sideloaded builds only: it lives on a development machine, so
  making it the identity readers update against would let anyone holding it publish an update
  straight over an install.
- A manual CI run can pass for the first time. The commit-subject check turned an absent base into
  the whole history, which reaches GitHub's own "Initial commit" and could never pass.
- `main` is protected: `ci-gate` is a required check, and force pushes and deletion are blocked.

---

Requires Android 10 (API 29) or newer. Full detail in
[CHANGELOG.md](https://github.com/mgmourad/FlowFolio/blob/main/CHANGELOG.md).

Check the APK before installing:

```bash
shasum -a 256 flowfolio-0.3.0-alpha.3-300003.apk
```
