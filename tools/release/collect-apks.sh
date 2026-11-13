#!/usr/bin/env bash
# Collects a release's APKs under their published names, checks each is signed with the release key, and writes
# its .sha256 (owner, 2026-11-12). Run from the repository root after `./gradlew assembleRelease` (both editions).
#   tools/release/collect-apks.sh <out-dir>
# Names (ADR-0057 as amended for the two editions):
#   Essential: flowfolio-<version>-<code>.apk (universal), ...-arm64-v8a.apk, ...-armeabi-v7a.apk
#   Full:      flowfolio-<version>-<code>.full.apk,       ...-arm64-v8a.full.apk, ...-armeabi-v7a.full.apk
# The Full names end in `.full.apk` so updaters from before the editions (which look for `-<abi>.apk` and
# `-<code>.apk`) keep picking the Essential edition. x86_64 ships only inside the universal APKs (emulators,
# Chromebooks); phones get 64-bit ARM, or 32-bit ARM one tier back.
set -euo pipefail
out=${1:?out dir}
props=android/gradle.properties
version=$(grep '^flowfolio.versionName=' "$props" | cut -d= -f2)
code=$(grep '^flowfolio.versionCode=' "$props" | cut -d= -f2)
release_cert=59fbae55543949a03ffbdf08092b60792346b34cc6bbf803bae5241eee4ee004
apks=android/app/build/outputs/apk
signer=$(ls -d "${ANDROID_HOME:?ANDROID_HOME}"/build-tools/* | sort -V | tail -1)/apksigner
mkdir -p "$out"
for edition in essential full; do
  suffix=$([ "$edition" = full ] && echo .full || true)
  for abi in universal arm64-v8a armeabi-v7a; do
    src="$apks/$edition/release/app-$edition-$abi-release.apk"
    if [ "$abi" = universal ]; then name="flowfolio-$version-$code$suffix.apk"; else name="flowfolio-$version-$code-$abi$suffix.apk"; fi
    # The wording of the signer line varies between apksigner versions ("Signer #1 ...", "Signer (minSdkVersion=...)
    # ..."): take the first certificate SHA-256 whatever precedes it.
    cert=$("$signer" verify --print-certs "$src" | sed -n 's/^.*certificate SHA-256 digest: //p' | head -1)
    [ "$cert" = "$release_cert" ] || { echo "$src is not signed with the release key ($cert)" >&2; exit 1; }
    cp "$src" "$out/$name"
    (cd "$out" && shasum -a 256 "$name" > "$name.sha256")
    echo "$name $(du -h "$out/$name" | cut -f1)"
  done
done
