import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions.*
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import org.acejump.config.AceConfig
import org.acejump.control.*
import org.acejump.label.Tagger
import org.acejump.view.Canvas
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import java.io.File

/**
 * Functional test cases and end-to-end performance tests.
 *
 * TODO: Add more structure to test cases, use test resources to define files.
 */

class AceTest: BasePlatformTestCase() {
  fun `test that scanner finds all occurrences of single character`() =
    assertEquals("test test test".search("t"), setOf(0, 3, 5, 8, 10, 13))

  fun `test empty results for an absent query`() =
    assertEmpty("test test test".search("best"))

  fun `test sticky results on a query with extra characters`() =
    assertEquals("test test test".search("testz"), setOf(0, 5, 10))

  fun `test a query inside text with some variations`() =
    assertEquals("abcd dabc cdab".search("cd"), setOf(2, 10))

  fun `test a query containing a space character`() =
    assertEquals("abcd dabc cdab".search("cd "), setOf(2))

  fun `test a query containing a { character`() =
    assertEquals("abcd{dabc cdab".search("cd{"), setOf(2))

  fun `test that jumping to first occurrence succeeds`() {
    "<caret>testing 1234".search("1")

    takeAction(ACTION_EDITOR_ENTER)

    myFixture.checkResult("testing <caret>1234")
  }

  fun `test that jumping to second occurrence succeeds`() {
    "<caret>testing 1234".search("ti")

    takeAction(ACTION_EDITOR_ENTER)

    myFixture.checkResult("tes<caret>ting 1234")
  }

  fun `test that jumping to previous occurrence succeeds`() {
    "te<caret>sting 1234".search("t")

    takeAction(ACTION_EDITOR_START_NEW_LINE)

    myFixture.checkResult("<caret>testing 1234")
  }

  fun `test tag selection`() {
    "<caret>testing 1234".search("g")

    typeAndWaitForResults(Canvas.jumpLocations.first().tag!!)

    myFixture.checkResult("testin<caret>g 1234")
  }

  fun `test shift selection`() {
    "<caret>testing 1234".search("4")

    typeAndWaitForResults(Canvas.jumpLocations.first().tag!!.toUpperCase())

    myFixture.checkResult("<selection>testing 123<caret></selection>4")
  }

  fun `test words before caret action`() {
    makeEditor("test words <caret> before caret is two")

    takeAction(AceWordBackwardsAction())

    assertEquals(2, Tagger.markers.size)
  }

  fun `test words after caret action`() {
    makeEditor("test words <caret> after caret is four")

    takeAction(AceWordForwardAction())

    assertEquals(4, Tagger.markers.size)
  }

  fun `test word mode`() {
    makeEditor("test word action")

    takeAction(AceWordAction())

    assertEquals(3, Tagger.markers.size)

    typeAndWaitForResults(Canvas.jumpLocations.toList()[1].tag!!)

    myFixture.checkResult("test <caret>word action")
  }

  fun `test target mode`() {
    "<caret>test target action".search("target")

    takeAction(AceTargetAction())
    typeAndWaitForResults(Canvas.jumpLocations.first().tag!!)

    myFixture.checkResult("test <selection>target<caret></selection> action")
  }

  fun `test line mode`() {
    makeEditor("    test\n    three\n    lines")

    takeAction(AceLineAction())

    assertEquals(9, Tagger.markers.size)
  }

  fun `test pinyin selection`() {
    getSettings().supportPinyin = true

    "test 拼音 selection".search("py")

    takeAction(AceTargetAction())
    typeAndWaitForResults(Canvas.jumpLocations.first().tag!!)

    myFixture.checkResult("test <selection>拼音<caret></selection> selection")
  }

  fun `test tag latency`(editorText: String) {
    var time = 0L

    editorText.toCharArray().distinct().filter { !it.isWhitespace() }
      .forEach { query ->
        repeat(10) {
          makeEditor(editorText)

          myFixture.testAction(AceAction())
          time += measureTimeMillis { typeAndWaitForResults("$query") }
          assert(Tagger.markers.isNotEmpty()) { "Should be tagged: $query" }
          resetEditor()
        }
      }

    println("Average time to tag results: ${time / 100}ms")
  }

  fun `test random text latency`() =
    `test tag latency`(
      generateSequence {
        generateSequence {
          generateSequence {
            ('a'..'z').random(Random(0))
          }.take(5).joinToString("")
        }.take(20).joinToString(" ")
      }.take(100).joinToString("\n")
    )

  fun `test lorem ipsum latency`() =
    `test tag latency`(
      File(
        javaClass.classLoader.getResource("lipsum.txt")!!.file
      ).readText()
    )

  fun getSettings() =
    ServiceManager.getService(AceConfig::class.java).aceSettings

  // Enforces the results are available in less than 100ms
  private fun String.search(query: String) =
    myFixture.run {
      maybeWarmUp(this@search, query)
      val queryTime = measureTimeMillis { this@search.executeQuery(query) }
//    assert(queryTime < 100) { "Query exceeded time limit! ($queryTime ms)" }
      this@search.replace(Regex("<[^>]*>"), "").assertCorrectNumberOfTags(query)
      editor.markupModel.allHighlighters.map { it.startOffset }.toSet()
    }

  // Ensures that the correct number of locations are tagged
  private fun String.assertCorrectNumberOfTags(query: String) =
    assertEquals(split(query.fold("") { prefix, char ->
      if ((prefix + char) in this) prefix + char else return
    }).size - 1, myFixture.editor.markupModel.allHighlighters.size)

  private var shouldWarmup = true

  // Should be run exactly once to warm up the JVM
  private fun maybeWarmUp(text: String, query: String) {
    if (shouldWarmup) {
      text.executeQuery(query)
      takeAction(ACTION_EDITOR_ESCAPE)
      UIUtil.dispatchAllInvocationEvents()
      // Now the JVM is warm, never run this method again
      shouldWarmup = false
    }
  }

  fun makeEditor(contents: String): PsiFile =
    myFixture.configureByText(PlainTextFileType.INSTANCE, contents)

  fun takeAction(action: String) = myFixture.performEditorAction(action)

  fun takeAction(action: AnAction) = myFixture.testAction(action)

  // Just does a query without enforcing any time limit
  private fun String.executeQuery(query: String) {
    myFixture.run {
      makeEditor(this@executeQuery)
      testAction(AceAction())
      typeAndWaitForResults(query)
    }
  }

  fun resetEditor() {
    takeAction(ACTION_EDITOR_ESCAPE)
    UIUtil.dispatchAllInvocationEvents()
    assertEmpty(myFixture.editor.markupModel.allHighlighters)
  }

  override fun tearDown() = resetEditor().also { super.tearDown() }

  private fun typeAndWaitForResults(string: String) =
    myFixture.type(string).also { UIUtil.dispatchAllInvocationEvents() }
}
