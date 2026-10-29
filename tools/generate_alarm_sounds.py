"""Generate the two deterministic bundled recording-alarm WAV patterns."""

import math
import struct
import wave
from pathlib import Path


def write_sounds(output: Path) -> None:
    """Write mono PCM patterns, including their pause, into output."""
    output.mkdir(parents=True, exist_ok=True)
    rate = 22050
    tone_size = int(rate * .180)
    for name, pulses, frequency in (("alarm_single_low", 1, 440), ("alarm_triple_high", 3, 880)):
        samples = []
        for pulse in range(pulses):
            if pulse:
                samples.extend([0] * tone_size)
            for index in range(tone_size):
                envelope = min(1, index / 110, (tone_size - 1 - index) / 110)
                samples.append(int(11000 * envelope * math.sin(2 * math.pi * frequency * index / rate)))
        samples.extend([0] * 35280)
        with wave.open(str(output / (name + ".wav")), "wb") as audio:
            audio.setparams((1, 2, rate, len(samples), "NONE", "not compressed"))
            audio.writeframes(struct.pack("<" + "h" * len(samples), *samples))


if __name__ == "__main__":
    write_sounds(Path(__file__).resolve().parents[1] / "app/src/main/res/raw")
