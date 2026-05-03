package voice.core.data.repo.internals.internals

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.allMigrations
import java.io.File
import java.util.UUID
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class DataBaseMigratorTest {

  @Rule
  @JvmField
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "migration-test-db"),
    AndroidSQLiteDriver(),
    AppDb::class,
  )

  @Test
  fun emptyTableLeadsToCorrectSchema() {
    val db = helper.createDatabase(43)
    db.execSQL(BookTable.CREATE_TABLE)
    db.execSQL(ChapterTable.CREATE_TABLE)
    db.execSQL(BookmarkTable.CREATE_TABLE)
    db.close()
    helper.runMigrationsAndValidate(AppDb.VERSION, allMigrations().toList())
  }

  @Test
  fun migrate44() {
    val db = helper.createDatabase(44)

    data class BookSetting(
      val id: String,
      val currentFile: String,
      val positionInChapter: Int,
    )

    data class Chapter(
      val file: String,
      val bookId: String,
    )

    fun insertBookSettings(settings: BookSetting) {
      db.execSQL(
        """
        INSERT OR REPLACE INTO `bookSettings`(
          `id`,`currentFile`,`positionInChapter`,`playbackSpeed`,`loudnessGain`,`skipSilence`,
          `active`,`lastPlayedAtMillis`
        ) VALUES (
          ${sqlString(settings.id)},
          ${sqlString(settings.currentFile)},
          ${settings.positionInChapter},
          1.0,
          0,
          0,
          1,
          0
        )
        """.trimIndent(),
      )
    }

    fun insertChapter(chapter: Chapter) {
      db.execSQL(
        """
        INSERT OR REPLACE INTO `chapters`(
          `file`,`name`,`duration`,`fileLastModified`,`marks`,`bookId`,`id`
        ) VALUES (
          ${sqlString(chapter.file)},
          'name',
          1,
          0,
          '{}',
          ${sqlString(chapter.bookId)},
          NULL
        )
        """.trimIndent(),
      )
    }

    val correctBookId = "id1"
    val file1 = "file1"
    val file2 = "file2"
    val correctBookSettings = BookSetting(id = correctBookId, currentFile = file1, positionInChapter = 5)
    insertBookSettings(correctBookSettings)
    insertChapter(Chapter(file1, correctBookId))
    insertChapter(Chapter(file2, correctBookId))

    val defectBookId = "id2"
    val defectBookSetting = BookSetting(id = defectBookId, currentFile = file1, positionInChapter = 10)
    insertBookSettings(defectBookSetting)
    insertChapter(Chapter(file2, defectBookId))

    db.close()

    val migratedDb = helper.runMigrationsAndValidate(45, allMigrations().toList())

    val migratedBookSettings = migratedDb.query("SELECT * FROM bookSettings") {
      BookSetting(
        id = getString("id"),
        currentFile = getString("currentFile"),
        positionInChapter = getInt("positionInChapter"),
      )
    }
    migratedBookSettings.shouldContainExactly(
      correctBookSettings,
      BookSetting(id = defectBookId, currentFile = file2, positionInChapter = 0),
    )
  }

  @Test
  fun migrate43() {
    val db = helper.createDatabase(43)
    db.execSQL(BookTable.CREATE_TABLE)
    db.execSQL(ChapterTable.CREATE_TABLE)
    db.execSQL(BookmarkTable.CREATE_TABLE)

    fun randomString() = UUID.randomUUID().toString()
    fun randomInt() = Random.nextInt(100)

    data class Bookmark(
      val path: String,
      val title: String,
      val time: Int,
    )

    val bookmarks = run {
      listOf(
        Bookmark(randomString(), randomString(), randomInt()),
        Bookmark(randomString(), randomString(), randomInt()),
      )
    }
    bookmarks.forEach {
      db.execSQL(
        """
        INSERT INTO ${BookmarkTable.TABLE_NAME}(
          ${BookmarkTable.PATH}, ${BookmarkTable.TITLE}, ${BookmarkTable.TIME}
        ) VALUES (
          ${sqlString(it.path)},
          ${sqlString(it.title)},
          ${it.time}
        )
        """.trimIndent(),
      )
    }

    data class Chapter(
      val duration: Int,
      val name: String,
      val path: String,
      val lastModified: Int,
      val marks: String?,
    )

    data class Book(
      val author: String?,
      val name: String,
      val currentMediaPath: String,
      val playbackSpeed: Float,
      val root: String,
      val type: String,
      val loudnessGain: Int,
      val active: Int,
      val time: Int,
      val chapters: List<Chapter>,
    )

    fun chapters(): List<Chapter> {
      return listOf(
        Chapter(randomInt(), randomString(), randomString(), randomInt(), randomString()),
        Chapter(randomInt(), randomString(), randomString(), randomInt(), null),
      )
    }

    val books = run {
      val firstBook = Book(
        author = randomString(),
        name = randomString(),
        currentMediaPath = randomString(),
        playbackSpeed = 1.1F,
        root = randomString(),
        type = randomString(),
        loudnessGain = randomInt(),
        active = 1,
        time = randomInt(),
        chapters = chapters(),
      )
      val secondBook = Book(
        author = null,
        name = randomString(),
        currentMediaPath = randomString(),
        playbackSpeed = 1.1F,
        root = randomString(),
        type = randomString(),
        loudnessGain = randomInt(),
        active = 0,
        time = randomInt(),
        chapters = emptyList(),
      )

      listOf(firstBook, secondBook)
    }

    books.forEachIndexed { index, book ->
      val bookId = index + 1
      db.execSQL(
        """
        INSERT INTO ${BookTable.TABLE_NAME}(
          ${BookTable.ID}, ${BookTable.AUTHOR}, ${BookTable.NAME}, ${BookTable.CURRENT_MEDIA_PATH},
          ${BookTable.PLAYBACK_SPEED}, ${BookTable.ROOT}, ${BookTable.TIME},
          ${BookTable.TYPE}, ${BookTable.LOUDNESS_GAIN}, ${BookTable.ACTIVE}
        ) VALUES (
          $bookId,
          ${sqlString(book.author)},
          ${sqlString(book.name)},
          ${sqlString(book.currentMediaPath)},
          ${book.playbackSpeed},
          ${sqlString(book.root)},
          ${book.time},
          ${sqlString(book.type)},
          ${book.loudnessGain},
          ${book.active}
        )
        """.trimIndent(),
      )
      book.chapters.forEach { chapter ->
        db.execSQL(
          """
          INSERT INTO ${ChapterTable.TABLE_NAME}(
            ${ChapterTable.DURATION}, ${ChapterTable.NAME}, ${ChapterTable.PATH},
            ${ChapterTable.LAST_MODIFIED}, ${ChapterTable.MARKS}, ${ChapterTable.BOOK_ID}
          ) VALUES (
            ${chapter.duration},
            ${sqlString(chapter.name)},
            ${sqlString(chapter.path)},
            ${chapter.lastModified},
            ${sqlString(chapter.marks)},
            $bookId
          )
          """.trimIndent(),
        )
      }
    }
    db.close()

    val migratedDb = helper.runMigrationsAndValidate(44, allMigrations().toList())

    val metaDataRows = migratedDb.query("SELECT * FROM bookMetaData") {
      BookMetaRow(
        getString("id"),
        getStringOrNull("author"),
        getString("name"),
        getString("root"),
      )
    }
    val bookSettingsRows = migratedDb.query("SELECT * FROM bookSettings") {
      BookSettingsRow(
        getString("id"),
        getString("currentFile"),
        getInt("positionInChapter"),
        getFloat("playbackSpeed"),
        getInt("loudnessGain"),
        getInt("skipSilence"),
        getInt("active"),
        getInt("lastPlayedAtMillis"),
      )
    }

    metaDataRows.size shouldBe books.size
    bookSettingsRows.size shouldBe books.size

    books.forEachIndexed { bookIndex, book ->
      val (metaDataId, author, name, root) = metaDataRows[bookIndex]
      author shouldBe book.author
      name shouldBe book.name
      root shouldBe book.root

      val (
        bookSettingsId,
        currentFile,
        positionInChapter,
        playbackSpeed,
        loudnessGain,
        skipSilence,
        active,
        lastPlayedAtMillis,
      ) = bookSettingsRows[bookIndex]
      currentFile shouldBe book.currentMediaPath
      positionInChapter shouldBe book.time
      playbackSpeed shouldBe book.playbackSpeed
      loudnessGain shouldBe book.loudnessGain
      skipSilence shouldBe 0
      active shouldBe book.active
      lastPlayedAtMillis shouldBe 0

      metaDataId shouldBe bookSettingsId

      val chapterRows = migratedDb.query("SELECT * FROM chapters WHERE bookId = ${sqlString(metaDataId)}") {
        ChapterRow(
          getString("file"),
          getInt("duration"),
          getString("name"),
          getInt("fileLastModified"),
          getStringOrNull("marks"),
        )
      }
      chapterRows.size shouldBe book.chapters.size
      book.chapters.forEachIndexed { chapterIndex, chapter ->
        val (chapterPath, duration, chapterName, fileLastModified, marks) = chapterRows[chapterIndex]
        chapterPath shouldBe chapter.path
        duration shouldBe chapter.duration
        chapterName shouldBe chapter.name
        fileLastModified shouldBe chapter.lastModified
        marks shouldBe (chapter.marks ?: "{}")
      }
    }

    val bookmarkRows = migratedDb.query("SELECT * FROM bookmark") {
      BookmarkRow(
        getString("file"),
        getInt("time"),
        getString("title"),
      )
    }
    bookmarkRows.size shouldBe bookmarks.size
    bookmarks.forEachIndexed { index, bookmark ->
      val (file, time, title) = bookmarkRows[index]
      file shouldBe bookmark.path
      time shouldBe bookmark.time
      title shouldBe bookmark.title
    }
    migratedDb.close()
  }

  private inline fun <T> SQLiteConnection.query(
    sql: String,
    mapper: SQLiteStatement.() -> T,
  ): List<T> {
    val rows = mutableListOf<T>()
    prepare(sql).use { statement ->
      while (statement.step()) {
        rows += statement.mapper()
      }
    }
    return rows
  }

  private fun SQLiteStatement.columnIndex(columnName: String): Int {
    return getColumnNames().indexOf(columnName).also { index ->
      check(index >= 0) { "Unknown column: $columnName" }
    }
  }

  private fun SQLiteStatement.getString(columnName: String): String = getText(columnIndex(columnName))

  private fun SQLiteStatement.getStringOrNull(columnName: String): String? =
    if (isNull(columnIndex(columnName))) null else getText(columnIndex(columnName))

  private fun SQLiteStatement.getInt(columnName: String): Int = getInt(columnIndex(columnName))

  private fun SQLiteStatement.getFloat(columnName: String): Float = getFloat(columnIndex(columnName))

  private fun sqlString(value: String?): String = value?.replace("'", "''")?.let { "'$it'" } ?: "NULL"

  private data class BookMetaRow(
    val id: String,
    val author: String?,
    val name: String,
    val root: String,
  )

  private data class BookSettingsRow(
    val id: String,
    val currentFile: String,
    val positionInChapter: Int,
    val playbackSpeed: Float,
    val loudnessGain: Int,
    val skipSilence: Int,
    val active: Int,
    val lastPlayedAtMillis: Int,
  )

  private data class ChapterRow(
    val file: String,
    val duration: Int,
    val name: String,
    val fileLastModified: Int,
    val marks: String?,
  )

  private data class BookmarkRow(
    val file: String,
    val time: Int,
    val title: String,
  )

  private object BookTable {
    const val ID = "bookId"
    const val NAME = "bookName"
    const val AUTHOR = "bookAuthor"
    const val CURRENT_MEDIA_PATH = "bookCurrentMediaPath"
    const val PLAYBACK_SPEED = "bookSpeed"
    const val ROOT = "bookRoot"
    const val TIME = "bookTime"
    const val TYPE = "bookType"
    const val ACTIVE = "BOOK_ACTIVE"
    const val LOUDNESS_GAIN = "loudnessGain"
    const val TABLE_NAME = "tableBooks"
    const val CREATE_TABLE = """
    CREATE TABLE $TABLE_NAME (
      $ID INTEGER PRIMARY KEY AUTOINCREMENT,
      $NAME TEXT NOT NULL,
      $AUTHOR TEXT,
      $CURRENT_MEDIA_PATH TEXT NOT NULL,
      $PLAYBACK_SPEED REAL NOT NULL,
      $ROOT TEXT NOT NULL,
      $TIME INTEGER NOT NULL,
      $TYPE TEXT NOT NULL,
      $LOUDNESS_GAIN INTEGER,
      $ACTIVE INTEGER NOT NULL DEFAULT 1
    )
  """
  }

  private object ChapterTable {

    const val DURATION = "chapterDuration"
    const val NAME = "chapterName"
    const val PATH = "chapterPath"
    const val TABLE_NAME = "tableChapters"
    const val BOOK_ID = "bookId"
    const val LAST_MODIFIED = "lastModified"
    const val MARKS = "marks"
    const val CREATE_TABLE = """
    CREATE TABLE $TABLE_NAME (
      $DURATION INTEGER NOT NULL,
      $NAME TEXT NOT NULL,
      $PATH TEXT NOT NULL,
      $BOOK_ID INTEGER NOT NULL,
      $LAST_MODIFIED INTEGER NOT NULL,
      $MARKS TEXT,
      FOREIGN KEY ($BOOK_ID) REFERENCES ${BookTable.TABLE_NAME} (${BookTable.ID})
    )
  """
  }

  private object BookmarkTable {

    const val PATH = "bookmarkPath"
    const val TITLE = "bookmarkTitle"
    const val TABLE_NAME = "tableBookmarks"
    const val TIME = "bookmarkTime"
    const val ID = "_id"
    const val CREATE_TABLE = """
    CREATE TABLE $TABLE_NAME (
      $ID INTEGER PRIMARY KEY AUTOINCREMENT,
      $PATH TEXT NOT NULL,
      $TITLE TEXT NOT NULL,
      $TIME INTEGER NOT NULL
    )
  """
  }
}
