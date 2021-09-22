package edu.vanderbilt.grader.tools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import edu.vanderbilt.grader.tools.ui.Repo
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

enum class ActionResult(val text: String) {
    Replace("Replace"),
    ReplaceAll("Replace all"),
    Skip("Skip"),
    SkipAll("Skip all"),
    Cancel("Cancel")
}

class ReplaceDialog(project: Project, oldRepo: Repo, newRepo: Repo) : DialogWrapper(project) {
    private var panel: JPanel

    init {
        title = "Overwrite existing repository?"
        val messageArea = JTextArea(buildQuestion(oldRepo, newRepo))
        messageArea.isEditable = false
        messageArea.background = JLabel().background
        panel = JPanel(BorderLayout())
        panel.add(messageArea, BorderLayout.CENTER)
        super.init()
    }

    private fun buildQuestion(oldRepo: Repo, newRepo: Repo): String =
        """|User ${oldRepo.user} already has a local repository.
        |
        |Would you like to replace the existing repository
        |
        |    ${oldRepo.user}:${oldRepo.repo} 
        |
        |with this one?
        |
        |    ${newRepo.user}:${newRepo.repo}
        |""".trimMargin("|")

    override fun createActions(): Array<Action> {
        return ActionResult.values().map {
            if (it == ActionResult.Skip) {
                MyActions(NEXT_USER_EXIT_CODE + it.ordinal, it.text, default = true, focused = true)
            } else {
                MyActions(NEXT_USER_EXIT_CODE + it.ordinal, it.text)
            }
        }.toTypedArray()
    }

    override fun createCenterPanel(): JComponent {
        return panel
    }

    inner class MyActions(
        private val exitCode: Int,
        text: String,
        default: Boolean = false,
        focused: Boolean = false
    ) : AbstractAction(text) {
        init {
            if (default) {
                putValue("DEFAULT_ACTION", default)
            }
            if (focused) {
                putValue("FOCUSED_ACTION", focused)
            }
        }

        override fun actionPerformed(e: ActionEvent) {
            close(exitCode)
        }
    }

    fun showAndGetResult(): ActionResult {
        showAndGet()

        return when (exitCode) {
            CANCEL_EXIT_CODE -> ActionResult.Cancel
            else -> ActionResult.values()[exitCode - NEXT_USER_EXIT_CODE]
        }
    }
}