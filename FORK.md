# 字幕听力练习 fork：维护须知

本仓库是 [PaulWoitaschek/Voice](https://github.com/PaulWoitaschek/Voice) 的 fork，加了字幕听力练习功能。
**每次同步上游、升级依赖（尤其是 media3）之前，先读完这份文件。**

## 一、防漂移措施（字幕与音频对不上）

漂移的根源：seek 落点 ≠ 播放器报告的位置。连续播放（任何倍速）本身不漂，
每次 seek（暂停自动回退、点字幕、单句循环跳回、启动恢复进度）都会重新引入一次误差。

| # | 措施 | 位置 | 升级时要做什么 |
|---|---|---|---|
| 1 | **本地 MP3 一律逐帧索引 seek**。media3 的 `FLAG_ENABLE_INDEX_SEEKING` 只在文件没有 VBR 目录时才生效，对带 Xing/VBRI 目录或无头 VBR 的文件无效（实测 25 分钟文件误差达 3.4 s）。所以复制了 media3 的 `Mp3Extractor` 改名 `LocalMp3Extractor`，只改一行：有 index 标志就无条件用 `IndexSeeker` | `core/playback/impl/src/main/java/androidx/media3/extractor/mp3/LocalMp3Extractor.java` | **升级 media3 必做**：从新版本 `media3-extractor-<版本>-sources.jar` 重新复制 `Mp3Extractor.java`，改名、改 `computeSeeker` 里那一行（去掉 `&& !resultSeeker.isSeekable()`），再更新测试里的版本号。版本守卫测试会在升级后失败提醒你 |
| 2 | 只对本地文件（`file`/`content`）用 #1；Audiobookshelf 等流媒体不用（索引 seek 要从头读到目标） | `core/playback/impl/.../player/LocalIndexSeekingExtractorsFactory.kt`，在 `PlaybackModule.mediaSourceFactory` 里接入 | 上游若改 `mediaSourceFactory`，确认仍然用这个工厂 |
| 3 | **单句循环在播放器里用 `PlayerMessage` 实现**，在句尾精确跳回，不靠 250 ms 轮询（轮询在 1.25x 会冲进下一句约 300 ms） | `ChapterMarkPlayer.setLoopRange/clearLoopRange` | 上游若重写 `ChapterMarkPlayer`，把这两个方法搬过去 |
| 4 | 字幕页按暂停 = **不自动回退**（避免一次 seek） | `VoicePlayer.pauseWithoutRewind`、`BookPlayViewModel.playPause` | 上游若改自动回退逻辑（`autoRewindListener`），保留 `skipNextAutoRewind` |
| 5 | 字幕位置**始终**用播放器实时位置 `livePlaybackStateFlow`，不用数据库里定时写入的位置（上游只在实验开关打开时才用实时位置） | `BookPlayViewModel.viewState` 里的 `playerState` | 上游若改这段，确认字幕仍然拿实时位置 |
| 6 | 按章节手动微调 offset（±0.1 s，存 DataStore），只能修正字幕文件本身的固定偏差，修不了 seek 误差 | `features/playbackScreen/.../subtitle/` | — |

已知仍会造成偏差、但不是 bug 的情况：
- **Skip silence 打开时**，ExoPlayer 处理完静音段就把它计入位置，字幕会提前高亮。上游行为，练习时建议关闭。
- 字幕文件时间轴本身和音频不一致（例如按不同版本音频做的字幕）：用 offset 微调；若是线性漂移（越往后差越多），目前不支持。

### 回归测试（改动播放相关代码后必须通过）

```bash
./gradlew :core:playback:impl:testDebugUnitTest
```

- `LocalMp3ExtractorTest`：版本守卫 + 用 `src/test/resources/vbr_xing.mp3`（4 分钟 VBR+Xing，ffmpeg libmp3lame 生成）验证 seek 误差为 0，并验证原版 `Mp3Extractor` 在同一文件上确实有误差（证明测试有效）。
- `ChapterMarkPlayerTest`：循环区间、播放模式。
- `VoicePlayerTest`：暂停不回退、"本章结束"定时器遇到单句循环在句尾暂停。

想在真实文件上测误差：参考 `LocalMp3ExtractorTest.seekError`，把文件换成要测的 MP3。

## 二、不能随便改的东西（改了用户数据会丢）

| 内容 | 存储 | 说明 |
|---|---|---|
| 星标 | DataStore 文件 `starredSubtitleCueKeys`，key 由 `SubtitleKeys.starKey` 生成（书 id、章节 id、文本的 SHA-256 前 8 字节） | 改算法 = 所有星标失效。`SubtitleKeysTest` 锁定了格式 |
| 每章 offset | DataStore 文件 `subtitleOffsets`，key 由 `subtitleOffsetKey` 生成 | 同上 |
| 字幕专注模式 | DataStore 文件 `subtitleFocusModeEnabled` | — |
| 播放模式 | DataStore 文件 `playbackMode`，枚举名 `Sequential/SingleTrackLoop/Shuffle/FolderLoop` | 枚举名不能改 |
| 学习统计 | DataStore 文件 `studyStats`，每条是 日期 × 文件夹名 × 文件名（**按名字，不用 id/hash**：同名文件替换成新版本后时长接着累计） | 字段名不能改，否则旧记录读不出来 |
| 包名 | debug 构建 `de.ph1b.audiobook.debug`（`app/build.gradle.kts` 里的 `applicationIdSuffix`） | 手机上装的就是这个包名，改了就变成另一个 App，书库不会带过去 |

## 三、fork 新增的其他功能（同步上游时注意冲突）

- `core:subtitle` 模块：`.srt` 解析（UTF-8 / GB18030、BOM、`.` 或 `,` 毫秒）、同文件夹查找（扩展名大小写不敏感）。日志不记录路径、文件名、字幕文本（上游 AGENTS.md 要求）。
- 字幕页 `view/SubtitleContent.kt`：底部 `[播放模式] ↶ ▶ ↷ [工具]`，工具行（单句循环 / 倍速 / Sync）默认收起；播放时保持亮屏。
- 字幕列表**不许按当前句改变字号或字重**（旧版这么做，导致长句换行变化、下面的句子上下跳）。所有行同一排版（Literata 18sp），当前句只用颜色、底色和 `graphicsLayer` 视觉放大 6%（不改排版）。当前句配色用 `primary` 实心底 + `onPrimary` 字，深色、浅色模式下都必须是最显眼的一行（浅蓝底配蓝字在深色模式下会输给旁边的白字，改动配色后两种模式都要截图检查：`adb shell cmd uimode night yes|no`，测完 `auto`）。当前句固定停在列表高度 20% 处（顶部留白 = 20%，底部留白 = 80%）。句间空隙保持上一句高亮（`lastStartedCueIndex`）。
- 学习统计的类别（`core/studystats/.../StudyCategories.kt`、`StudyCategorizer.kt`）：学外语 `language`、有声书 `audiobook`、冥想 `meditation`，**按文件夹名**保存（DataStore `studyCategories`，改文件不用重标）。自动识别：文件夹名含 `meditat` / `冥想` / `正念` → 冥想；文件旁边有 `.srt` 字幕 → 学外语；否则有声书。自动识别**只往上升**（有声书 → 学外语 → 冥想，一个没字幕的文件不会把整个文件夹降回去），**手动选择永远优先**，不会被自动识别覆盖。识别时机：文件开始播放时（`StudyTimeRecorder` 调 `StudyCategorizer.onPlaying`），以及每次启动和打开统计页时给书库里还没标记的书补标（每本只看前 3 个文件，列 SAF 目录比较慢）。统计页：顶部过滤（全部 / 学外语 / Audiobook / 冥想），三张卡片（今天 / 本周 / 累计），视图（文件夹 / 按周 / 按月），长按文件夹改类别。一周从周一开始，和 TTC 一致。CSV 最后一列是 `category`，同步的每个条目都带 `category`（`id`，不要改）。
- 服务器端（另一个仓库 `goalsifter_v3`）的 `study_log` 表有 `category` 列，`/api/v1/study/summary` 里有 `week_by_category` / `month_by_category`；服务器没升级时多发的 `category` 会被忽略，不会出错。
- 学习时间同步到自己的服务器（GoalSifter / TTC 的 `POST /api/v1/study-sync/days`，接口说明在另一个仓库：`openclaw-bot/bots/goalsifter_v3/docs/operations/study-sync-api.md`）：`core/studystats/.../StudySync*.kt`。规则：只发最近 7 天；每天带上该天**全部**文件（服务器按天整体替换，所以重复发送安全）；`item_key` = `文件夹/文件名`；单个文件秒数 ≤ 86400，一天合计 ≤ 86400，播放次数 ≤ 10000，日期不能比中国时间晚一天以上（任何一条不合格服务器会整个拒绝，所以发送前就裁掉）；失败不重试，下次同步再发。入口：学习统计页右上角云朵。第一次（还没填地址和令牌）点它打开对话框，填完点"立即同步"，成功就自动关掉，只弹一句提示；之后点云朵就直接同步，只弹结果；地址或令牌不对时对话框会自己弹出来；**长按云朵**随时可以改地址和令牌。自动同步：App 回到前台（3 分钟内最多一次）、听完一段后学习时间 45 秒不再变化。
  - **地址和令牌只存在手机的 DataStore（`studySync`），不要写进代码或提交**，并且已经在 `backup_rules.xml` / `data_extraction_rules.xml` 里排除出云备份。
  - 令牌在 http 下明文传输（服务器暂时没有 TLS），对话框里有提示；服务器上了 https 就把地址改成 https://。
  - 不要在日志里记录地址、令牌、服务器返回的内容（上游 AGENTS.md 的规定），只记结果类型。
  - 本机测试办法（不碰真实服务器）：在电脑上起一个假服务器，按接口文档校验请求，`adb reverse tcp:8099 tcp:8099`，地址填 `http://127.0.0.1:8099/api/v1/study-sync/days`。手机的拼音输入法会吞掉 `adb input text`，改用 `adb push` + `run-as cp` 把 `{"url":..,"token":..}` 直接写进 `files/datastore/studySync`。
- 单击字幕 = 跳到这句**并播放**（暂停时开始播放，播放中继续播放），用 `PlayerController.playFrom`，在同一个协程里先跳转再播放；分成 `setPosition` + `play` 两次调用，`play` 会抢在跳转前，先从旧位置响一下。上游其他地方的跳转（章节、进度条、书签、历史）都只改位置、不改播放状态，这是有意区别。
- 字幕页标题显示**文件名**（`ChapterId.fileName()`），因为同一文件夹的文件常常标签标题相同。
- 字幕页顶栏有睡眠定时器按钮（封面模式的底部工具栏在字幕页不显示）；字幕/封面切换：字幕页显示"图片"图标，封面页显示文字按钮"字幕"。
- 播放模式作用在**文件**上（直接设在被包装的 ExoPlayer 上），`ChapterMarkPlayer` 对上层报告不循环、不随机，避免上一章/下一章逻辑出错。
- "本章结束"定时器 + 单句循环：句尾即视为章末，暂停。
- 学习统计：`core:studystats`（数据、汇总、CSV）+ `StudyTimeRecorder`（挂在 `PlaybackModule.player` 上，只在真正出声时计时，每 30 s 存一次）+ `features:studyStats`（页面，入口在设置 → 书库）。播完次数：文件播到结尾（自然结束、单曲循环转回开头、自动跳到下一个）**且**自上次播完以来听到的音频达到文件长度的 90% 才算一次（按音频时间算，倍速不影响；拖到结尾不算；单句循环不算）。统计页有"按文件夹"和"按月"（日历，按当天时长着色，点某天看当天文件）两种视图。CSV 开头带 BOM，Excel 才能正确显示中文。不使用上游的 `listening_session` 表，避免同步上游时的数据库迁移冲突。
- 播放失败时弹 Toast（`PlaybackErrorToast`，挂在 `PlaybackModule.player` 上）：本地文件不存在 → "文件找不到"，存在但播不了 → "无法播放"；流媒体不提示（上游有自己的"不可用"处理）。文件名放在句末，因为 Toast 最多两行会截断。上游原本只记日志、不提示；若上游以后自己加了播放错误提示，删掉这个，避免重复。

## 四、同步上游的方法

本 fork 最早是上游 `2269db925` 的压缩快照，2026-11 在上游 `4ef7fa716`（2026-11-02）之上重新移植，现在 `main` 和上游**有共同历史**，可以直接合并。
旧 fork 的历史保存在远程分支 `archive/fork-2026-05`、`archive/old-local-main`、`archive/fork-branding`，只作备份，不再开发。

本地需要添加上游远程（只需一次）：`git remote add upstream https://github.com/PaulWoitaschek/Voice.git`，并把 upstream 的 push 关掉：`git remote set-url --push upstream DISABLED`。



```bash
git fetch upstream
git merge upstream/main   # 或 rebase
./gradlew spotlessApply :core:subtitle:testDebugUnitTest :core:playback:impl:testDebugUnitTest :features:playbackScreen:testDebugUnitTest :app:assembleFreeDebug
```

合并后对照第一节逐条检查，再装到手机上实测：长文件拖到后段，暂停/点字幕/单句循环，听高亮是否对得上。

## 五、和上游不同的仓库文件

- `README.md`：fork 自己的说明（注明不是官方 Voice）。同步上游时保留 fork 版本。
- 删除了 `.github/workflows/deploy_pages.yml`：它在推送 `main` 时会把上游的官方网站部署到本仓库的 GitHub Pages，看起来像官方 Voice。同步上游时如果它又出现，再删掉。
- 主界面锁定竖屏：`app/src/main/AndroidManifest.xml` 里 `MainActivity` 的 `android:screenOrientation="portrait"`（lint 的 `LockedOrientationActivity`、`DiscouragedApi` 已用 `tools:ignore` 豁免）。Android 16 在平板和折叠屏上会忽略这个设置，手机上有效。测试办法：`adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1`，截图尺寸还是竖的就对；测完把这两个设置恢复原值。
- 设置页的"Support development"赞助卡片已关闭：`app/build.gradle.kts` 里 `free` 版的 `SUPPORT_DEVELOPMENT_INCLUDED` 改成了 `false`（上游 `free` 是 `true`，`play` 是 `false`）。原因：链接是 Ko-fi，在中国打不开。只改了这一行，赞助页和卡片的代码都保留，同步上游时不会冲突；仍可在开发者菜单里重新打开。同步上游后检查这一行有没有被改回去。
- fork 的文案都在 `core/strings/src/main/res/values*/strings_fork.xml`（英文、简体、繁体），**不要加进 `strings.xml`**：上游的 `scripts/check_locale_config.main.kts` 要求 `strings.xml` 里每种语言 100% 翻译，否则 CI 失败；放在单独文件里也避免同步上游时冲突。其他语言缺翻译时显示英文（文件根上有 `tools:ignore="MissingTranslation"`）。
- `.github/workflows/ci.yml` 加了 `permissions: checks: write`，否则 JUnit 报告步骤没有权限。同步上游时保留。
- 最低支持 Android 9（API 28）：不要用只有新版本才有的 API（例如 `URLDecoder.decode(String, Charset)` 需要 API 33，改用 `"UTF-8"` 字符串版本）。提交前跑 `./gradlew :app:lintFreeDebug`，它会抓到这类问题。
- 格式化工具（ktfmt）会把字符串里的 `"﻿"` 换成看不见的字符，lint 会报 `ByteOrderMark`。用 `0xFEFF.toChar()`。
- 其余发布类工作流（Play、Firebase、release）只能手动触发且需要本仓库没有的密钥，保留不动。

## 六、本机环境备忘（Windows）

- 这些上游测试在 Windows 上失败，与 fork 无关（Room 测试数据库路径、`File.toUri()` 差异）：`core:data:impl` 的 `Migration*Test`、`DataBaseMigratorTest`、`ConvertersTest`、`CustomMetadataRepoImplTest`；`core:scanner` 的 `MediaScannerTest` 中三个改名相关用例。在 Linux CI 上应通过。
- 装机：`adb install -r app/build/outputs/apk/free/debug/app-free-debug.apk`。签名用本机 `~/.android/debug.keystore`。**签名不一致时不要卸载旧版**（会清空书库），先想办法。

## Playback mode "One" (SingleTrackLoop)

Previous/next from the app (`ForceSeekToPrevious/Next`) stay in the looped file: previous goes to
its start, next to its end, where `REPEAT_MODE_ONE` begins it again. Implemented in
`ChapterMarkPlayer.seekWithinLoopedFile`; headset/car skips are unaffected. The presented state
reports repeat off, so without this the chapter-level next/previous would leave the file.

## Refreshing the library

Files change in a file manager while Voice stays open, so the library can be refreshed three ways:
pull down on the bookshelf (`MediaScanTrigger.rescan`, restarts a running scan), automatic scan when
the app comes to the front (`MediaScanInitializer`, at most every 30 s), and the subtitle file is
read again whenever the app starts (`SubtitlePractice`, on `ON_START`). The scanner already compares
size and modified time of every audio file (`ChapterParser` / `ChapterRepo.getOrPut`), so replaced
files are found by any scan; no extra change detection is needed.

### Books without files leave the library (deviates from upstream)

Upstream keeps a book active when all its files are gone (test `metadataPreservedOnDeletion`
expected the content unchanged). That left a single-folder book whose folder was deleted on the
shelf, and playing it said "file not found" even after a refresh. `MediaScanner.scan` now sets a
book without audio files inactive, which keeps its position; it is active again with the position
once its files are back. When merging upstream, keep this and the changed test.

A single-book folder that is gone but still set up is no longer counted as scanned
(`MediaScanner`: only files that exist), so the same book under its new name finds it as its
predecessor and takes over position, bookmarks and history. Windows can't run the rename tests of
`MediaScannerTest` (file names are not read from `C:\` uris), CI on Linux does.

## Chapter list shows file names

Files often carry the same tag title (several `Friends S01E02 (TTS dialogue)` with different
versions), so the chapter sheet adds the file name with its extension below the title when the book
has more than one file and the title is not the file name already. The extension tells it apart
from the title. `ChapterId.fullFileName()` (with extension) next to `fileName()`.

## Merging upstream

Trial merge on 2026-11-10 (fork base `4ef7fa716`, upstream 64 commits ahead): 10 conflicted files,
all in code the fork changed: `VoicePlayer`, `ChapterMarkPlayer`, `MediaScanner` (+ its test),
`BookOverview`, `BookPlayViewModel`, `PlaybackTopBar`, three `build.gradle.kts`, and
`deploy_pages.yml` (deleted here). The rest merged by itself.

How to merge: `git fetch upstream`, merge on a branch, resolve, then
`./gradlew spotlessApply :app:lintFreeDebug` and the unit tests of `core:playback:impl`,
`core:scanner`, `features:playbackScreen`, `core:studystats`, `core:subtitle`; push and read the
Linux CI (the rename tests of `MediaScannerTest` can't run on Windows). Check each point of
this file (drift measures, deviations, stored keys, support card, portrait lock) is still in place.
Merge every few weeks, the conflicts grow with the distance. Never push to `upstream`.
