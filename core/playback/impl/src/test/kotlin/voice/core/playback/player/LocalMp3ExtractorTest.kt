package voice.core.playback.player

import android.net.Uri
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.mp3.LocalMp3Extractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.test.utils.FakeExtractorOutput
import androidx.media3.test.utils.TestUtil
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

/**
 * Subtitles drift by however far a seek lands from the position the player reports. See FORK.md.
 */
@RunWith(AndroidJUnit4::class)
class LocalMp3ExtractorTest {

  // a 4 min VBR file with a Xing table, made with ffmpeg's libmp3lame from noise and silence
  private val uri = Uri.fromFile(File(javaClass.classLoader!!.getResource("vbr_xing.mp3").toURI()))
  private val flags =
    Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING

  @Test
  fun `LocalMp3Extractor is a copy of the Mp3Extractor of the media3 version in use`() {
    assertEquals(
      expected = "1.10.1",
      actual = MediaLibraryInfo.VERSION,
      message =
        "media3 was updated. Copy its Mp3Extractor into LocalMp3Extractor again, keep the index " +
          "preferred, then update this version. See FORK.md.",
    )
  }

  @Test
  fun `seeks land on the frame the player reports`() {
    val trueTimes = trueTimesOfFrames()
    listOf(30, 75, 120, 165, 210).forEach { seconds ->
      val error = seekError(LocalMp3Extractor(flags), seconds * 1_000_000L, trueTimes)
      assertEquals(expected = 0L, actual = error, message = "at ${seconds}s")
    }
  }

  @Test
  fun `the xing table of this file is off, so the test above means something`() {
    val trueTimes = trueTimesOfFrames()
    val worstErrorUs =
      listOf(30, 75, 120, 165, 210).maxOf { seconds ->
        abs(seekError(Mp3Extractor(flags), seconds * 1_000_000L, trueTimes))
      }
    // more than a frame of 72 ms. Long files are off by seconds.
    assertTrue(worstErrorUs > 100_000, "worst xing error ${worstErrorUs / 1000} ms")
  }

  /** The time of each frame when reading from the start, by the content of the frame. */
  private fun trueTimesOfFrames(): Map<Int, List<Long>> {
    val output = FakeExtractorOutput()
    val extractor = LocalMp3Extractor(flags)
    val dataSource = FileDataSource()
    val length = dataSource.open(DataSpec(uri))
    val input = DefaultExtractorInput(dataSource, 0, length)
    extractor.init(output)
    val position = PositionHolder()
    var result = Extractor.RESULT_CONTINUE
    while (result != Extractor.RESULT_END_OF_INPUT) {
      result = extractor.read(input, position)
    }
    dataSource.close()
    val track = output.trackOutputs.valueAt(0)
    return (0 until track.sampleCount).groupBy(
      keySelector = { track.getSampleData(it).contentHashCode() },
      valueTransform = { track.getSampleTimeUs(it) },
    )
  }

  /** How far the first frame after seeking to [timeUs] is from the time it is reported at. */
  private fun seekError(
    extractor: Extractor,
    timeUs: Long,
    trueTimes: Map<Int, List<Long>>,
  ): Long {
    val output = FakeExtractorOutput()
    val dataSource = FileDataSource()
    val seekMap = TestUtil.extractSeekMap(extractor, output, dataSource, uri)
    val track = output.trackOutputs.valueAt(0)
    val index = TestUtil.seekToTimeUs(extractor, seekMap, timeUs, dataSource, track, uri)
    val reportedUs = track.getSampleTimeUs(index)
    val trueUs =
      trueTimes[track.getSampleData(index).contentHashCode()].orEmpty().minBy {
        abs(it - reportedUs)
      }
    return reportedUs - trueUs
  }
}
