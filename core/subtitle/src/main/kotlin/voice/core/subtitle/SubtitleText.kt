package voice.core.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

// Subtitles downloaded for Chinese material are often still GBK encoded, which GB18030 contains.
internal fun decodeSubtitleText(bytes: ByteArray): String {
  return try {
    Charsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(bytes))
      .toString()
  } catch (_: CharacterCodingException) {
    String(bytes, charset("GB18030"))
  }
}
