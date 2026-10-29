import math
import struct
import tempfile
import unittest
import wave
from pathlib import Path

import generate_alarm_sounds as sounds


class AlarmSoundsTest(unittest.TestCase):
    def test_distinct_pitch_and_pulse_counts_with_pause(self):
        with tempfile.TemporaryDirectory() as directory:
            sounds.write_sounds(Path(directory))
            for name, pulses, frequency in (("alarm_single_low", 1, 440), ("alarm_triple_high", 3, 880)):
                bundled = Path(__file__).resolve().parents[1] / "app/src/main/res/raw" / (name + ".wav")
                self.assertEqual(bundled.read_bytes(), (Path(directory) / (name + ".wav")).read_bytes())
                with wave.open(str(Path(directory) / (name + ".wav")), "rb") as audio:
                    self.assertEqual((audio.getnchannels(), audio.getsampwidth(), audio.getframerate()), (1, 2, 22050))
                    samples = struct.unpack("<" + "h" * audio.getnframes(), audio.readframes(audio.getnframes()))
                tone_size = int(22050 * 0.180)
                self.assertEqual(len(samples), tone_size * (2 * pulses - 1) + 35280)
                self.assertTrue(all(value == 0 for value in samples[-35280:]))
                for pulse in range(pulses):
                    tone = samples[pulse * tone_size * 2: pulse * tone_size * 2 + tone_size]
                    crossings = sum(a <= 0 < b for a, b in zip(tone, tone[1:]))
                    self.assertAlmostEqual(crossings / .180, frequency, delta=10)
                    self.assertGreater(math.sqrt(sum(v * v for v in tone) / len(tone)), 1000)


if __name__ == "__main__":
    unittest.main()
