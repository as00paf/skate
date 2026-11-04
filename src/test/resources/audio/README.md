# Audio test fixtures

`tone-*.mp3` are synthetic sine tones (440 Hz, plus 660 Hz on the right channel in stereo) generated for the Skate
engine tests by `make_mp3_fixtures.py` and encoded with LAME (through the `lameenc` Python package). They contain no
third-party content and are dedicated to the public domain under CC0 1.0. LAME was only the encoding tool; its
license does not apply to its output.

| File | Format | Purpose |
|---|---|---|
| `tone-stereo-44k.mp3` | MPEG-1 Layer III, 44.1 kHz stereo, 128 kbps, 0.25 s, ID3v2 tag with a 1 KB picture frame | decode and stream, tag skipping |
| `tone-mono-44k.mp3` | MPEG-1 Layer III, 44.1 kHz mono, 64 kbps, 0.25 s | mono decode |
| `tone-mono-22k-mpeg2.mp3` | MPEG-2 Layer III, 22.05 kHz mono | unsupported format fails cleanly |

To regenerate them: `uv run --no-project --with lameenc python make_mp3_fixtures.py <out-dir>`.
