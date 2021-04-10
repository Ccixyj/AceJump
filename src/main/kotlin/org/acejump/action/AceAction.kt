package org.acejump.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.project.DumbAwareAction
import org.acejump.boundaries.Boundaries
import org.acejump.boundaries.StandardBoundaries
import org.acejump.boundaries.StandardBoundaries.*
import org.acejump.input.JumpMode
import org.acejump.input.JumpMode.*
import org.acejump.search.Pattern
import org.acejump.search.Pattern.*
import org.acejump.session.Session
import org.acejump.session.SessionManager

/**
 * Base class for keyboard-activated actions that create or update an AceJump [Session].
 */
sealed class AceAction: DumbAwareAction() {
  final override fun update(action: AnActionEvent) {
    action.presentation.isEnabled = action.getData(EDITOR) != null
  }

  final override fun actionPerformed(e: AnActionEvent) =
    e.getData(EDITOR)?.let { invoke(SessionManager.start(it)) } ?: Unit

  abstract operator fun invoke(session: Session)

  /**
   * Generic action type that toggles a specific [JumpMode].
   */
  abstract class BaseToggleJumpModeAction(private val mode: JumpMode): AceAction() {
    final override fun invoke(session: Session) = session.toggleJumpMode(mode)
  }

  /**
   * Generic action type that starts a regex search.
   */
  abstract class BaseRegexSearchAction(private val pattern: Pattern, private val boundaries: Boundaries): AceAction() {
    override fun invoke(session: Session) = session.startRegexSearch(pattern, boundaries)
  }

  /**
   * Initiates an AceJump session in the first [JumpMode], or cycles to the next [JumpMode] as defined in configuration.
   */
  class ActivateOrCycleMode: AceAction() {
    override fun invoke(session: Session) = session.cycleNextJumpMode()
  }

  /**
   * Initiates an AceJump session in the last [JumpMode], or cycles to the previous [JumpMode] as defined in configuration.
   */
  class ActivateOrReverseCycleMode: AceAction() {
    override fun invoke(session: Session) = session.cyclePreviousJumpMode()
  }

  // @formatter:off

  class ToggleJumpMode        : BaseToggleJumpModeAction(JUMP)
  class ToggleJumpEndMode     : BaseToggleJumpModeAction(JUMP_END)
  class ToggleTargetMode      : BaseToggleJumpModeAction(TARGET)
  class ToggleDeclarationMode : BaseToggleJumpModeAction(DEFINE)


  class StartAllWordsMode          : BaseRegexSearchAction(ALL_WORDS, WHOLE_FILE)
  class StartAllWordsBackwardsMode : BaseRegexSearchAction(ALL_WORDS, BEFORE_CARET)
  class StartAllWordsForwardMode   : BaseRegexSearchAction(ALL_WORDS, AFTER_CARET)
  class StartAllLineStartsMode     : BaseRegexSearchAction(LINE_STARTS, WHOLE_FILE)
  class StartAllLineEndsMode       : BaseRegexSearchAction(LINE_ENDS, WHOLE_FILE)
  class StartAllLineIndentsMode    : BaseRegexSearchAction(LINE_INDENTS, WHOLE_FILE)
  class StartAllLineMarksMode      : BaseRegexSearchAction(LINE_ALL_MARKS, WHOLE_FILE)

  // @formatter:on
}
