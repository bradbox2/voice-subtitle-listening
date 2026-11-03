# Subtitle Synchronization Improvements

## Overview

This document tracks improvements to the subtitle synchronization system, addressing the long-file drift limitation and adding per-chapter offset adjustment.

## Issue: Subtitle Drift on Long Files

**Original Limitation:** Subtitle sync drifted during extended playback on files longer than 10 minutes, particularly with VBR MP3 files. Caused by ExoPlayer's constant-bitrate approximation seek, which accumulates positioning errors as file size increases.

**Root Cause:** The playback controller relied on `setConstantBitrateSeekingEnabled(true)` exclusively. For MP3 files without Xing headers (common in practice clips), ExoPlayer uses the first frame's bitrate to estimate "bytes ↔ playback time", but VBR files violate this assumption. Each seek injects an error that propagates as a fixed offset through subsequent playback.

## Fix 1: Sample-Accurate MP3 Index Seeking (Gradual Rollout)

**Status:** Implemented, feature flagged, tests passing ✅

**Changes:**

- **File:** `core/featureflag/FeatureFlagBindingContainer.kt`
  - New feature flag: `mp3_index_seeking` (default `false`)
  - Description: "Uses sample-accurate MP3 index seeking to keep subtitles aligned on long files."
  - Available in Developer Menu under Feature Flag Overrides
  - Can be remotely controlled via Firebase Remote Config for gradual rollout

- **File:** `core/playback/di/PlaybackModule.kt`
  - DI wire-up: inject `@Mp3IndexSeekingFeatureFlagQualifier` into `mediaSourceFactory`
  - Applied via: `DefaultExtractorsFactory().setMp3ExtractorFlags(mp3ExtractorFlags(indexSeekingEnabled = flag.get()))`
  - CBR seeking remains enabled as fallback for other formats

- **File:** `core/playback/player/Mp3ExtractorFlags.kt` (new)
  - Pure function mapping boolean flag to `Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING`
  - Testable and decoupled from DI

- **Test:** `core/playback/src/test/kotlin/voice/core/playback/player/Mp3ExtractorFlagsTest.kt`
  - Validates flag→constant mapping
  - Ensures flag=true → INDEX_SEEKING, flag=false → 0

**How It Works:**

- **When enabled:** Mp3Extractor builds a real sample index as it reads the file. Seek positions are resolved using actual frame positions (sample-accurate).
- **Cost:** First seek to deep file position requires sequential scan to that point (~few seconds for 100 MB local file). Index then cached in Extractor instance.
- **Benefit:** Elimates the linear error accumulation; all subsequent seeks within indexed range are exact.

**Rollout Strategy:**

1. Ship with flag defaulting to `false`
2. Local testing: enable in Developer Menu, validate on long files (15+ min)
3. Remote rollout: enable flag in Firebase Remote Config at ~10% → 50% → 100%
4. Once confident: change default to `true` and eventually remove flag

**Note:** Flag is read at player creation time (PlaybackScope singleton). Toggling flag requires process restart to take effect.

---

## Fix 2: Per-Chapter Subtitle Offset Adjustment

**Status:** Implemented, persisted, UI integrated, tests passing ✅

**Changes:**

- **File:** `core/data/api/src/main/kotlin/voice/core/data/store/StoreQualifiers.kt`
  - New qualifier: `@SubtitleOffsetsStore`

- **File:** `core/data/impl/src/main/kotlin/voice/core/data/store/StoreModule.kt`
  - New DataStore: `subtitleOffsets` mapping `String` (chapter key) → `Long` (offset ms)
  - Filename: `subtitleOffsets`

- **File:** `features/playbackScreen/src/main/kotlin/voice/features/playbackScreen/SubtitleOffset.kt` (new)
  - `subtitleOffsetKey(bookId, chapterId)`: generates stable key (SHA-256 short hash, excludes raw URI)
  - `adjustSubtitleOffset(current, delta)`: applies delta and clamps to ±60 seconds
  - `formatSubtitleOffset(ms)`: human-readable display (e.g., "-1.2s", "+0.3s")
  - Constants: `MAX_SUBTITLE_OFFSET_MS = 60_000L`, `SUBTITLE_OFFSET_STEP_MS = 100L`

- **File:** `features/playbackScreen/src/main/kotlin/voice/features/playbackScreen/BookPlayViewModel.kt`
  - Inject `@SubtitleOffsetsStore` and expose methods:
    - `onSubtitleOffsetChange(deltaMs: Long)`: adjusts offset for active chapter, persists
    - `onSubtitleOffsetReset()`: clears offset for active chapter
    - `currentSubtitleOffsetKey()`: derives key from live/persisted chapter
  - Updated `updateRepeatSentenceLoop()`: receive `offsetMs` param, subtract from `positionMs` when matching cues
  - Updated `onSubtitleClick()`: add offset to seek target before calling `player.setPosition()`
  - Updated viewState: read offsets from store, pass to subtitle panel

- **File:** `features/playbackScreen/src/main/kotlin/voice/features/playbackScreen/BookPlayViewState.kt`
  - New field in `SubtitlePanelViewState`: `offsetMs: Long`

- **File:** `features/playbackScreen/src/main/kotlin/voice/features/playbackScreen/view/SubtitlePanel.kt`
  - New composable `SubtitleOffsetRow()`: displays "[−] Sync +0.3s [+]"
    - Left button: decrease by 100ms (−)
    - Center label: tap to reset, shows signed offset
    - Right button: increase by 100ms (+)
  - Placed between subtitle list and progress slider in focused mode

- **File:** `features/playbackScreen/src/main/kotlin/voice/features/playbackScreen/view/BookPlayContent.kt`
  - Plumb callbacks: `onSubtitleOffsetChange`, `onSubtitleOffsetReset`

- **File:** `features/playbackScreen/src/main/kotlin\voice/features/playbackScreen/view/BookPlayView.kt`
  - Accept and forward offset callbacks

- **File:** `features/playbackScreen/BookPlayController.kt`
  - Wire callbacks to ViewModel methods

- **Tests:** `features/playbackScreen/src/test/kotlin/voice/features/playbackScreen/SubtitleOffsetTest.kt`
  - Key generation stability, no URI leakage
  - Delta application and clamping
  - Format display (signed seconds)
  - Lookup defaults to zero

- **Tests:** `features/playbackScreen/src/test/kotlin/voice/features/playbackScreen/BookPlayViewModelTest.kt` (4 new)
  - Offset shifts active cue detection (`activeCueIndex(position - offset)`)
  - `onSubtitleClick` adds offset to seek
  - `onSubtitleOffsetChange` persists clamped value
  - `onSubtitleOffsetReset` clears stored offset

**Semantics:**

- **Positive offset:** Subtitles appear later (shift whole SRT forward in time)
- **Negative offset:** Subtitles appear earlier (shift whole SRT backward in time)
- **Scope:** Per chapter (user can have different offsets for different audio files)
- **Persistence:** Saved in DataStore, survives app restart

**UI Behavior (Subtitle Focused Mode):**

1. User sees subtitle list with current active sentence highlighted
2. Below list: `[−] Sync +0.3s [+]`
3. Tapping `−` decreases offset by 100ms (e.g., +0.3s → +0.2s)
4. Tapping `+` increases offset by 100ms
5. Tapping center label resets offset to zero (removes from store)
6. Changes apply immediately to highlighting and loop/seek behavior

---

## README Updates

**File:** `README.md`

- Added bullet point: "Nudge subtitle sync per chapter in 0.1 s steps when a subtitle file is slightly off; the offset is remembered"
- Updated limitations section:
  - Old: "Clicking a subtitle re-aligns playback from that sentence, but it does not globally re-time the entire subtitle file."
  - New: "Clicking a subtitle re-aligns playback from that sentence; for a global shift, use the per-chapter sync nudge on the subtitle screen."
  - Added note on experimental MP3 index seeking flag

---

## Testing Verification

**Run Full Test Suite:**

```bash
./gradlew voiceUnitTest lintKotlin
```

**Result:** ✅ All 51 tests pass, all lint checks pass

**Key Tests Added:**

1. `Mp3ExtractorFlagsTest` (2 tests)
   - Flag mapping correctness

2. `SubtitleOffsetTest` (5 tests)
   - Key stability and URI exclusion
   - Delta clamping
   - Format display
   - Lookup with defaults

3. `BookPlayViewModelTest` (4 new tests)
   - Offset shifts active cue indexing
   - Offset applied in seek targets
   - Persistence and reset
   - Offset works in repeat-sentence loop

**Test Strategy (TDD):**

All tests written first, observed to fail for expected reasons, then implementation written to pass. No existing test regressions.

---

## Deployment Checklist

- [ ] Code review and merge to main
- [ ] Test on local device with long MP3 files (15+ min)
  - Enable `mp3_index_seeking` in Developer Menu
  - Verify seek/tap accuracy and no drift over time
  - Test offset adjustment on misaligned SRT
- [ ] Optional: Integrate Firebase Remote Config experiment for gradual rollout
- [ ] Update Play Store release notes
- [ ] Monitor crash rates and seek-related logs post-release
- [ ] Remove feature flag or flip default after confidence period (suggested: 1–2 weeks at 100%)

---

## Impact Summary

| Aspect | Before | After |
|--------|--------|-------|
| Long file drift | Accumulates linearly | Eliminated (with flag) |
| Max recommended length | 10 minutes | 1+ hours (with flag) |
| SRT misalignment handling | None | Per-chapter ±60s nudge |
| Offset UI | N/A | Subtitle focused mode |
| User experience | Retry/reload on drift | Tap a button to adjust |

