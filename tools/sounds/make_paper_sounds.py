"""Generate the riffle scrubber's paper sounds (ADR-0023): standard library only, output is our own (MIT).

Each sound is filtered noise shaped by an envelope, like the mock's Web Audio sounds:
  riffle_flick.wav  a page edge flicking past (35 ms, band 2.3-4.9 kHz)
  riffle_thump.wav  a chapter start passing (90 ms, lower band around 950 Hz)
  riffle_whoosh.wav a sheet turning on landing (240 ms, band sweeping 700 Hz -> 3.2 kHz)

Usage: python -I tools/sounds/make_paper_sounds.py android/feature/reader/riffle/src/main/res/raw
"""

import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 22050


def bandpass(samples, centre, q):
    """A two-pole band-pass (RBJ cookbook); centre may be a function of the sample index (a sweep)."""
    out = []
    x1 = x2 = y1 = y2 = 0.0
    for i, x in enumerate(samples):
        f = centre(i) if callable(centre) else centre
        w = 2 * math.pi * f / RATE
        alpha = math.sin(w) / (2 * q)
        b0, b2 = alpha, -alpha
        a0, a1, a2 = 1 + alpha, -2 * math.cos(w), 1 - alpha
        y = (b0 * x + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1 = x1, x
        y2, y1 = y1, y
        out.append(y)
    return out


def envelope(n, attack_s, peak):
    """A quick linear rise, then an exponential fall to silence by the end."""
    attack = max(1, int(attack_s * RATE))
    return [peak * (i / attack if i < attack else math.exp(-6.0 * (i - attack) / max(1, n - attack))) for i in range(n)]


def sound(seconds, centre, q, attack_s, rng):
    n = int(seconds * RATE)
    noise = [rng.uniform(-1, 1) * (1 - i / n) for i in range(n)]
    shaped = bandpass(noise, centre, q)
    peak = max(abs(s) for s in shaped) or 1.0
    env = envelope(n, attack_s, 0.9 / peak)
    return [s * e for s, e in zip(shaped, env)]


def write(path, samples):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(9)  # the same files on every run
    write(out / "riffle_flick.wav", sound(0.035, 3600, 0.9, 0.002, rng))
    write(out / "riffle_thump.wav", sound(0.09, 950, 1.4, 0.002, rng))
    n = int(0.24 * RATE)
    write(out / "riffle_whoosh.wav", sound(0.24, lambda i: 700 * (3200 / 700) ** min(1.0, i / (0.18 * RATE)), 0.7, 0.04, rng))
    for f in sorted(out.glob("riffle_*.wav")):
        print(f.name, f.stat().st_size)


if __name__ == "__main__":
    main()
