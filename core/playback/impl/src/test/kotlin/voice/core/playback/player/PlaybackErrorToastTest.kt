package voice.core.playback.player

import android.app.Application
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.strings.R as StringsR

@RunWith(AndroidJUnit4::class)
class PlaybackErrorToastTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val toast = PlaybackErrorToast(context)

  @Test
  fun `a file that is gone is named as missing`() {
    val gone = File(temporaryFolder.root, "Louie - S02E04.mp3").toUri()

    assertEquals(
      expected = context.getString(StringsR.string.playback_error_file_missing, "Louie - S02E04"),
      actual = toast.message(gone),
    )
  }

  @Test
  fun `a file that is there but doesn't play is named as unplayable`() {
    val there = temporaryFolder.newFile("broken.mp3").toUri()

    assertEquals(
      expected = context.getString(StringsR.string.playback_error_unplayable, "broken"),
      actual = toast.message(there),
    )
  }

  @Test
  fun `the name of a document comes from the end of its id`() {
    assertEquals(
      expected = "S01E06-chatterbox_actors",
      actual =
        displayName(
          "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2FS0106edition%2FS01E06-chatterbox_actors.mp3"
            .toUri(),
        ),
    )
  }

  @Test
  fun `streamed books are left to the server handling`() {
    assertEquals(expected = null, actual = toast.message("audiobookshelf://item/1".toUri()))
  }
}
