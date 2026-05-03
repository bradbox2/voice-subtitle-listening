# Voice Subtitle Listening Practice

![CI](https://github.com/bradbox2/voice-subtitle-listening/actions/workflows/ci.yml/badge.svg?branch=main)

This is a GPLv3-licensed fork of Voice by Paul Woitaschek.
It is not the official Voice app.

Original project: <https://github.com/PaulWoitaschek/Voice>

Built for subtitle listening practice:

- Load `.srt` subtitles from the same folder as your audiobook
- Pick subtitles from SAF / `content://` sources
- Tap a subtitle to re-align playback from that sentence
- Highlight the current subtitle line while listening
- Star lines for review and practice
- Use Voice's original playback modes: sequential play, single-track repeat, shuffle, and folder repeat
- Keep offline audiobook playback when no subtitles are available

Recommended workflow:

- Use short `mp3 + srt` practice clips of about 3-8 minutes
- For the most stable subtitle sync, prefer clips no longer than 10 minutes
- Longer files still work, but subtitle alignment is easier to keep stable on shorter segments

Known limitations:

- Clicking a subtitle re-aligns playback from that sentence, but it does not globally re-time the entire subtitle file.
- Subtitle sync is most reliable on short clips; longer audiobook files are more likely to drift during extended practice sessions.
- The app keeps Voice's original audiobook behavior when subtitles are absent.

## UI Preview

| Library | Now Playing | Subtitle Sync | Review |
| --- | --- | --- | --- |
| ![Library screen](fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png) | ![Now playing screen](fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png) | ![Subtitle sync screen](fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png) | ![Review screen](fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png) |

## License

This fork is licensed under GNU GPLv3, the same terms as the original project. The original project is licensed under [GNU GPLv3](docs/license). By contributing, you agree to license your code under the same terms.
