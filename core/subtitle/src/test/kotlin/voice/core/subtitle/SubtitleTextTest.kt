package voice.core.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleTextTest {

  @Test
  fun `reads utf 8`() {
    assertEquals("你好 world", decodeSubtitleText("你好 world".toByteArray(Charsets.UTF_8)))
  }

  @Test
  fun `falls back to gb18030 for chinese files that are not utf 8`() {
    val bytes = "你好，世界".toByteArray(charset("GB18030"))

    assertEquals("你好，世界", decodeSubtitleText(bytes))
  }
}
