package com.johnlindquist.acejump

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.johnlindquist.acejump.keycommands.*
import com.johnlindquist.acejump.ui.AceCanvas
import com.johnlindquist.acejump.ui.SearchBox
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import java.awt.Point
import java.awt.Font
import java.awt.Dimension
import java.awt.Color
import com.intellij.openapi.util.SystemInfo

public open class AceJumpAction(): DumbAwareAction() {

    override public fun update(e: AnActionEvent?) {
        e?.getPresentation()?.setEnabled((e?.getData(PlatformDataKeys.EDITOR)) != null)
    }
    override public fun actionPerformed(p0: AnActionEvent?) {
        val actionEvent = p0
        val project = actionEvent?.getData(PlatformDataKeys.PROJECT) as Project
        val editor = actionEvent?.getData(PlatformDataKeys.EDITOR) as EditorImpl
        val virtualFile = actionEvent?.getData(PlatformDataKeys.VIRTUAL_FILE) as VirtualFile
        val document = editor.getDocument() as DocumentImpl
        val scheme = EditorColorsManager.getInstance()?.getGlobalScheme()
        val font = Font(scheme?.getEditorFontName(), Font.BOLD, scheme?.getEditorFontSize()!!)
        val aceFinder = AceFinder(project, document, editor, virtualFile)
        val aceJumper = AceJumper(editor, document as DocumentImpl)
        val aceCanvas = AceCanvas()
        val searchBox = SearchBox()
        val textAndOffsetHash = HashMap<String, Int>()

        fun showJumpers(textPointPairs: List<Pair<String, Point>>?) {
            aceCanvas.jumpInfos = textPointPairs?.reverse()
            aceCanvas.repaint()
        }
        fun exit() {
            val contentComponent: JComponent? = editor.getContentComponent()
            contentComponent?.remove(aceCanvas)
            contentComponent?.repaint()
            textAndOffsetHash.clear()
        }

        fun setupJumpLocations(results: MutableList<Int>) {
            textAndOffsetHash.clear()
            val textPointPairs: MutableList<Pair<String, Point>> = ArrayList<Pair<String, Point>>()
            for (i in 0..results.size - 1) {
                val textOffset: Int = results.get(i)
                val point: RelativePoint? = getPointFromVisualPosition(editor, editor.offsetToVisualPosition(textOffset))
                val text: String = aceFinder.generateString(i, results.size - 1)
                textPointPairs.add(Pair<String, Point>(text, point?.getOriginalPoint() as Point))
                textAndOffsetHash.put(text, textOffset)
            }
            showJumpers(textPointPairs)
        }


        fun addAceCanvas() {
            val contentComponent: JComponent? = editor.getContentComponent()
            contentComponent?.add(aceCanvas)
            val viewport = editor.getScrollPane().getViewport()
            aceCanvas.setBounds(0, 0, (viewport?.getWidth())!! + 1000, (viewport?.getHeight())!! + 1000)
            val rootPane: JRootPane? = editor.getComponent().getRootPane()!!
            val locationOnScreen: Point? = SwingUtilities.convertPoint(aceCanvas, (aceCanvas.getLocation()), rootPane)
            aceCanvas.setLocation(-locationOnScreen!!.x, -locationOnScreen!!.y)
        }

        fun configureSearchBox() {
            fun setupSearchBoxKeys() {
                val showJumpObserver: ChangeListener = object : ChangeListener {
                    public override fun stateChanged(e: ChangeEvent) {
                        setupJumpLocations(aceFinder.results as MutableList<Int>)
                    }
                }
                val releasedHome: AceKeyCommand = ShowBeginningOfLines(searchBox, aceFinder)
                val releasedEnd: AceKeyCommand = ShowEndOfLines(searchBox, aceFinder)
                releasedHome.addListener(showJumpObserver)
                releasedEnd.addListener(showJumpObserver)
                searchBox.addPreProcessReleaseKey(KeyEvent.VK_HOME, releasedHome)
                searchBox.addPreProcessReleaseKey(KeyEvent.VK_LEFT, releasedHome)
                searchBox.addPreProcessReleaseKey(KeyEvent.VK_RIGHT, releasedEnd)
                searchBox.addPreProcessReleaseKey(KeyEvent.VK_END, releasedEnd)

                val releasedUp: AceKeyCommand = ShowFirstCharOfLines(searchBox, aceFinder)
                releasedUp.addListener(showJumpObserver)
                searchBox.addPreProcessReleaseKey(KeyEvent.VK_UP, releasedUp)

                val pressedBackspace: AceKeyCommand = ClearResults(searchBox, aceCanvas)
                val pressedEnter: AceKeyCommand = ExpandResults(searchBox, aceFinder, aceJumper)
                pressedEnter.addListener(showJumpObserver)
                searchBox.addPreProcessPressedKey(KeyEvent.VK_BACK_SPACE, pressedBackspace)
                searchBox.addPreProcessPressedKey(KeyEvent.VK_ENTER, pressedEnter)

                val pressedSpace: AceKeyCommand = ShowWhiteSpace(searchBox, aceFinder)
                pressedSpace.addListener(showJumpObserver)
                searchBox.addPreProcessPressedKey(KeyEvent.VK_SPACE, pressedSpace)

                val defaultKeyCommand: DefaultKeyCommand? = DefaultKeyCommand(searchBox, aceFinder, aceJumper, textAndOffsetHash)
                defaultKeyCommand?.addListener(showJumpObserver)
                searchBox.defaultKeyCommand = defaultKeyCommand
            }

            setupSearchBoxKeys()
            searchBox.setFont(font)
            val popupBuilder: ComponentPopupBuilder? = JBPopupFactory.getInstance()?.createComponentPopupBuilder(searchBox as JComponent, searchBox)
            popupBuilder?.setCancelKeyEnabled(true)
            val popup = (popupBuilder?.createPopup() as AbstractPopup?)
            popup?.show(guessBestLocation(editor))
            val width = searchBox.getFontMetrics(font)?.stringWidth("w")
            var dimension: Dimension? = null
            if(width != null){
                dimension = Dimension(width * 2, (editor.getLineHeight()))
                if (SystemInfo.isMac) {
                    dimension?.setSize(dimension!!.width * 2, dimension!!.height * 2)
                }

            }


            popup?.setSize(dimension as Dimension)
            searchBox.popupContainer = popup
            searchBox.setSize(dimension as Dimension)
            searchBox.setFocusable(true)
            searchBox.addFocusListener(object : FocusListener {
                public override fun focusGained(p0: FocusEvent) {
                    addAceCanvas()
                }
                public override fun focusLost(p0: FocusEvent) {
                    exit()
                }
            })
            searchBox.requestFocus()
        }

        configureSearchBox()

        fun configureAceCanvas() {
            aceCanvas.setFont(font)
            aceCanvas.lineHeight = editor.getLineHeight()
            aceCanvas.lineSpacing = scheme?.getLineSpacing()!!
            aceCanvas.colorPair = Pair<Color?, Color?>(scheme?.getDefaultBackground(), scheme?.getDefaultForeground())
        }

        configureAceCanvas()
    }

}