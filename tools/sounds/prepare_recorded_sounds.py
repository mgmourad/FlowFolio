"""Prepares the owner's paper recordings for the riffle scrubber (ADR-0025).

Usage: python -I tools/sounds/prepare_recorded_sounds.py <folder with the four recordings> <res/raw folder>

Reads Page-flip-forward.wav, Page-flip-baclward.wav (sic), Pages-riffle-forward.wav and Pages-riffle-backward.wav
(24-bit PCM), and writes flip_forward, flip_backward, riffle_forward and riffle_backward as 16-bit 44.1 kHz stereo WAV:
the silent ends trimmed with short fades, each brought to the same loudness, and the riffles made to loop (their end
crossfaded into their start), since they play for as long as the reader riffles. Needs numpy and scipy.
"""

import sys
import wave
from math import gcd
from pathlib import Path

import numpy as np
from scipy.signal import resample_poly

RATE = 44_100
SILENCE_DB = -60.0
FADE_S = 0.01
TARGET_RMS_DB = -22.0
PEAK_LIMIT_DB = -1.0
LOOP_CROSSFADE_S = 0.15

SOURCES = {
    "flip_forward": ("Page-flip-forward.wav", False),
    "flip_backward": ("Page-flip-baclward.wav", False),
    "riffle_forward": ("Pages-riffle-forward.wav", True),
    "riffle_backward": ("Pages-riffle-backward.wav", True),
}


def load(path: Path) -> tuple[np.ndarray, int]:
    with wave.open(str(path)) as w:
        if w.getsampwidth() != 3:
            raise ValueError(f"{path.name}: expected 24-bit PCM")
        raw = np.frombuffer(w.readframes(w.getnframes()), dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        values = raw[:, 0] | (raw[:, 1] << 8) | (raw[:, 2] << 16)
        values = np.where(values >= 1 << 23, values - (1 << 24), values) / float(1 << 23)
        return values.reshape(-1, w.getnchannels()), w.getframerate()


def resample(x: np.ndarray, rate: int) -> np.ndarray:
    if rate == RATE:
        return x
    g = gcd(rate, RATE)
    return resample_poly(x, RATE // g, rate // g, axis=0)


def trim(x: np.ndarray) -> np.ndarray:
    """Cuts the ends quieter than SILENCE_DB (10 ms windows) and fades the cut edges."""
    win = int(RATE * 0.01)
    rms = [np.sqrt(np.mean(x[i : i + win] ** 2)) for i in range(0, len(x), win)]
    loud = [i for i, r in enumerate(rms) if 20 * np.log10(max(r, 1e-9)) > SILENCE_DB]
    x = x[loud[0] * win : (loud[-1] + 1) * win].copy()
    fade = int(RATE * FADE_S)
    ramp = np.linspace(0.0, 1.0, fade)[:, None]
    x[:fade] *= ramp
    x[-fade:] *= ramp[::-1]
    return x


def level(x: np.ndarray) -> np.ndarray:
    """Brings the loud part (windows above the median) to TARGET_RMS_DB, keeping peaks under PEAK_LIMIT_DB."""
    win = int(RATE * 0.05)
    rms = np.array([np.sqrt(np.mean(x[i : i + win] ** 2)) for i in range(0, len(x) - win, win)])
    active = np.sqrt(np.mean(rms[rms >= np.median(rms)] ** 2))
    gain = 10 ** (TARGET_RMS_DB / 20) / active
    gain = min(gain, 10 ** (PEAK_LIMIT_DB / 20) / np.abs(x).max())
    return x * gain


def loop(x: np.ndarray) -> np.ndarray:
    """Overlaps the last LOOP_CROSSFADE_S with the start (equal power), so the sound repeats without a seam."""
    n = int(RATE * LOOP_CROSSFADE_S)
    t = np.linspace(0.0, np.pi / 2, n)[:, None]
    head = x[:n] * np.sin(t) + x[-n:] * np.cos(t)
    return np.concatenate([head, x[n:-n]])


def save(x: np.ndarray, path: Path) -> None:
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(x.shape[1])
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())


def main() -> None:
    source, out = Path(sys.argv[1]), Path(sys.argv[2])
    for name, (file, looping) in SOURCES.items():
        x, rate = load(source / file)
        x = level(trim(resample(x, rate)))
        if looping:
            x = loop(x)
        save(x, out / f"{name}.wav")
        print(f"{name}: {len(x) / RATE:.2f} s")


if __name__ == "__main__":
    main()
