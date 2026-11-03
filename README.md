# Voice Subtitle Listening Practice

[![CI](https://github.com/bradbox2/voice-subtitle-listening/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bradbox2/voice-subtitle-listening/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE.md)

This is a GPLv3-licensed fork of [Voice](https://github.com/PaulWoitaschek/Voice) by Paul Woitaschek.
It is **not** the official Voice app and is not published on Google Play or F-Droid.

It adds subtitle listening practice on top of the current Voice:

- Loads the `.srt` file with the same name from the folder of each audio file (UTF-8 or GBK/GB18030)
- Highlights the sentence playing, tap a sentence to play from it
- Repeat sentence, looped exactly at the end of the sentence at any speed
- Star sentences, quick speeds 0.75 / 1 / 1.25, and a per file subtitle sync nudge in 0.1 s steps
- Playback modes per file: sequential, repeat one, shuffle, repeat all
- Keeps the screen on while playing on the subtitle screen

Subtitles stay aligned on long VBR MP3 files: files on the device seek by an index of their frames
instead of their Xing table, which lands seconds off. See [FORK.md](FORK.md) for how this works and
what to check when updating.

Tip: turn off *Skip silence* while practicing, it moves the position ahead of what you hear.

Everything else, including the library, sleep timer, bookmarks and Audiobookshelf support, is Voice
as it is upstream.

## Building

```bash
./gradlew :app:assembleFreeDebug
```

## License

GNU GPLv3, the same as the original project. See [LICENSE.md](LICENSE.md).
