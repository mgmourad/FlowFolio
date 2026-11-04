# ADR-0049: Re-key the release signing identity

- **Status:** Proposed
- **Date:** 2026-11-03
- **Deciders:** product owner, lead engineer
- **Phase:** 3

## Context

ADR-0042 made a release key (`CN=FlowFolio Release`, SHA-256
`560eb00ca346b33a79d9d9fdd465b0762a8d0d9662b4145406b19e9e06bb7d0d`) the identity readers update
against. `v0.3.0-alpha.1` was signed with it. Known issue 42 recorded that the keystore was missing
from this machine but that the key *existed*, so the task was to restore it.

**On 2026-11-03 the owner confirmed the key is lost**, not merely absent.

There is no recovery. Android's key rotation (APK Signature Scheme v3 proof-of-rotation) works by
signing a lineage **with the old key** to authorise the new one; with the old key gone no valid
lineage can be made. Play App Signing offers a key upgrade path, but FlowFolio is not on Play and
ADR-0042 says it never will be. So no future build can ever install over `v0.3.0-alpha.1`.

What that costs, measured rather than assumed: **both published releases have zero downloads.**

| Release | Signed with | APK downloads |
|---|---|---|
| `v0.3.0-alpha.1` | release key (lost) | 0 |
| `v0.3.0-alpha.2` | dev key | 0 |

The repository was private until 2026-11-03, so the APKs were never reachable. The install base is
the owner's own phone. This is the cheapest moment in the project's life for this to happen, and it
only stays cheap while the launch is held (which the owner decided on 2026-11-03, before knowing
this).

## Options considered

1. **Adopt the dev key as the release identity.** Nothing to generate, and `alpha.2` would update in
   place. Against: the reason this was rejected on 2026-11-03 has not changed — the dev key signs
   every sideloaded build and is handed around with debug APKs, so making it the identity readers
   update against means anyone who obtains it can publish an update straight over an install. It
   also would not help `alpha.1`, which is signed with the lost key.
2. **Generate a new release key now, before the launch.** One new keypair, used from `alpha.3`
   onwards. Against: `alpha.1` and `alpha.2` can never be updated from — which, at zero downloads,
   costs nothing.
3. **Delay and try to recover the old key.** Against: the owner says it is lost, and there is no
   technical recovery. Delay only raises the cost, because the repository is now public and someone
   may install `alpha.1` at any time.

## Decision

Proposed: **option 2**, immediately and before any announcement.

Generate a new release keypair, use it from `0.3.0-alpha.3` onwards, and treat durable custody of it
as part of the decision rather than an afterthought — the previous key was lost because nothing in
the project said where it had to live. It must exist in at least two places that are not this
machine:

- in a password manager, as the keystore file plus its passwords in one entry; and
- as base64 in this repository's GitHub Actions secrets, so release builds can be produced in CI and
  the key survives the loss of any single machine.

`alpha.1` and `alpha.2` should be **deleted** from the releases page rather than left to be
installed: both have zero downloads, neither can be updated from, and a newcomer who picks up
`alpha.1` lands on a dead end.

## Consequences

- Positive: the project has a signing identity again, backed up where losing one machine does not
  lose it. The cost is paid at zero users instead of after a launch.
- Negative / accepted risks: the two existing pre-releases are abandoned, and `CN=FlowFolio Release`
  SHA-256 `560eb00c...` is permanently retired — any APK presenting it in future is not ours and
  cannot be ours. Worth stating in `SECURITY.md`.
- Follow-ups (owner, before the launch): generate the key, store it in both places above, delete the
  two pre-releases, then ship `alpha.3` as the first build under the new identity. Supersede the key
  custody part of ADR-0042 and close known issue 42.
