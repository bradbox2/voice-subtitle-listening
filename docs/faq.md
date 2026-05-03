### Does this fork support Android Auto?

Yes, the fork supports Android Auto.

!!! warning

    For Android Auto functionality to work, install the app directly from the
    [Google Play Store](https://play.google.com/store/apps/details?id=de.ph1b.audiobook).
    This is because Android Auto integration requires versions that have been reviewed and approved by the Google Play team.

### Why doesn't the fork support the xyz media format?

The fork relies on the media formats that are natively supported by the Android platform.

You can review the currently supported file extensions [here](https://developer.android.com/media/media3/exoplayer/supported-formats).
If a file that should be supported is not displayed, it is most likely either corrupted or incompatible with your Android version.

### Why isn't feature xyz available in the app?

The app keeps a narrow scope. It only includes settings and UI components that are necessary for the fork's current goals.

### How can I join the beta?

To participate in the public beta, you can either:

- [Join via the Web](https://play.google.com/store/apps/details?id=de.ph1b.audiobook)
- [Join through Google Play](https://play.google.com/apps/testing/de.ph1b.audiobook)

### Which fork version should I use on older Android?

!!! tip

    To check your API level, go to **Settings > About > Android version** on your device.

If you're running an Android release that's not supported by the latest fork build, pick the version below that matches your OS/API level:

| Android Version | API Level (SDK) | Fork Version                                                            |
|-----------------|-----------------|-------------------------------------------------------------------------|
| Android 9+      | 28+             | Supported in the latest version                                        |
| Android 8.1     | 27              | [8.2.4-2](https://github.com/bradbox2/voice-subtitle-listening/releases/tag/8.2.4-2) |
| Android 8       | 26              | [8.2.4-2](https://github.com/bradbox2/voice-subtitle-listening/releases/tag/8.2.4-2) |
| Android 7.1     | 25              | [8.2.4-2](https://github.com/bradbox2/voice-subtitle-listening/releases/tag/8.2.4-2) |
| Android 7.0     | 24              | [6.0.10](https://github.com/bradbox2/voice-subtitle-listening/releases/tag/6.0.10)   |

### How do I resume playback after the sleep timer stops?

Once the sleep timer elapses, the app pauses playback after a brief fade-out. To keep listening, you have two options:

- **Shake to resume**: Shake your device within 30 seconds of pause to restart playback.
- **Open to resume**: Open the app and press play again.

!!! warning

    On some devices, shake-to-resume may not work reliably.
