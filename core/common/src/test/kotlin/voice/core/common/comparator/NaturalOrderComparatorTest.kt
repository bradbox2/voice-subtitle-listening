package voice.core.common.comparator

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class NaturalOrderComparatorTest {

  private val testFolder = TemporaryFolder()

  @Rule
  fun testFolder() = testFolder

  @Before
  fun setUp() {
    testFolder.create()
  }

  private fun testFiles(): List<File> {
    return listOf(
      file("folder/subfolder/subsubfolder/test2.mp3"),
      file("folder/subfolder/test.mp3"),
      file("folder/subfolder/test2.mp3"),
      file("folder/a.jpg"),
      file("folder/aC.jpg"),
      file("storage/emulated/0/1.ogg"),
      file("storage/emulated/0/2.ogg"),
      file("xFolder/d.jpg"),
      file("1.mp3"),
      file("a.jpg"),
    )
  }

  private fun file(path: String): File {
    val file = File(testFolder.root, path)
    file.parentFile?.let { Files.createDirectories(it.toPath()) }
    Files.createFile(file.toPath())
    return file
  }

  @Test
  fun stringComparator() {
    val expected = listOf(
      "00 I",
      "00 Introduction",
      "1",
      "01 How to build a universe",
      "01 I",
      "2",
      "9",
      "10",
      "a",
      "Ab",
      "aC",
      "Ba",
      "cA",
      "D",
      "e",
      "folder1/1.mp3",
      "folder1/10.mp3",
      "folder2/2.mp3",
      "folder10/1.mp3",
    )
    val sorted = expected.sortedWith(NaturalOrderComparator.stringComparator)
    sorted shouldBe expected
  }

  @Test
  fun uriComparatorContent() {
    val expected = listOf(
      "folder1/1.mp3",
      "folder1/10.mp3",
      "folder2/2.mp3",
      "folder10/1.mp3",
      "00 I",
      "00 Introduction",
      "1",
      "01 How to build a universe",
      "01 I",
      "2",
      "9",
      "10",
      "a",
      "Ab",
      "aC",
      "Ba",
      "cA",
      "D",
      "e",
    )

    val uris = expected.map {
      Uri.Builder()
        .scheme("content")
        .authority("com.android.externalstorage.documents")
        .appendPath("tree")
        .appendPath("primary:audiobooks")
        .appendPath("document")
        .appendPath("primary:audiobooks/$it")
        .build()
    }

    uris.sortedWith(NaturalOrderComparator.uriComparator)
      .shouldContainExactly(uris)
  }

  @Test
  fun uriComparatorFiles() {
    val expected = listOf(
      file("1.mp3"),
      file("a.jpg"),
      file("folder/a.jpg"),
      file("folder/aC.jpg"),
      file("folder/subfolder/subsubfolder/test2.mp3"),
      file("folder/subfolder/test.mp3"),
      file("folder/subfolder/test2.mp3"),
      file("storage/emulated/0/1.ogg"),
      file("storage/emulated/0/2.ogg"),
      file("xFolder/d.jpg"),
    )
    val uris = expected.map { Uri.fromFile(it) }

    uris.sortedWith(NaturalOrderComparator.uriComparator)
      .shouldContainExactly(uris)
  }

  @After
  fun tearDown() {
    testFolder.delete()
  }
}
