package edu.vanderbilt.grader.tools.ui

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.filters.UrlFilter
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.utils.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JPanel

class LogView(project: Project) : ProjectContext(project) {
    var text: String
        get() = editor?.document?.text ?: ""
        set(value) {
            editor?.document?.let {
                it.replaceString(0, it.textLength, value)
            }
        }

    private val panel: JPanel = JPanel(BorderLayout())
    val component: JComponent
        get() = panel

    private val editor: Editor?
        get() = (console as ConsoleViewImpl).editor

    var consoleView: ConsoleViewImpl
    private lateinit var actionToolbar: ActionToolbar

    init {
        //TODO: this factory should probably be used...

        val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        builder.addFilter(UrlFilter(project))
        consoleView = builder.console as ConsoleViewImpl
        consoleView.addMessageFilter(UrlFilter(project))
        Disposer.register(project, consoleView)

        panel.add(consoleView.component, BorderLayout.CENTER)

        with(DefaultActionGroup()) {
            consoleView.addCustomConsoleAction(FindAction())
            consoleView.addCustomConsoleAction(LoadAction())
            consoleView.addCustomConsoleAction(SaveAction())
            addAll(*consoleView.createConsoleActions())
            panel.add(createToolbar(this), BorderLayout.WEST)
        }

        ApplicationManager.getApplication().invokeLater {
            editor?.document?.let {
                it.replaceString(0, it.textLength, project.logState.text)
            }
        }
        ProjectManager.getInstance().addProjectManagerListener {
            if (it == project) {
                project.logState.text = text
            }
            true
        }
    }

    private fun createToolbar(actions: ActionGroup): JComponent {
        actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, false)
        return actionToolbar.component
    }

    private fun updateActionToolbar() {
        actionToolbar.updateActionsImmediately()
    }

    private inner class LoadAction :
        AnAction("Load", "Load from a file", AllIcons.Actions.Menu_open),
        DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            try {
                if (!loadFromFile()) {
                    info("Operation cancelled.")
                }
            } catch (e: Exception) {
                error("Load exception: ${e.message}.")
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = true
        }
    }

    private inner class SaveAction :
        AnAction("Save", "Save log to a file", AllIcons.Actions.Menu_saveall), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            try {
                if (saveToFile()) {
                    info("Log saved.")
                } else {
                    info("Operation cancelled.")
                }
            } catch (e: Exception) {
                error("Save exception: ${e.message}.")
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = editor?.document?.textLength != 0
        }
    }

    private inner class FindAction :
        AnAction("Find", "Find", AllIcons.Actions.Find), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            find("")
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = editor?.document?.textLength != 0
        }
    }

    private fun createMouseEventWrapper(component: Component): MouseEvent {
        return MouseEvent(
            component,
            ActionEvent.ACTION_PERFORMED,
            System.currentTimeMillis(),
            0, 0, 0, 0, false, 0
        )
    }

    fun find(pattern: String) {
        ActionManager.getInstance().getAction(IdeActions.ACTION_FIND)?.let { delegate ->
            if (pattern.isNotBlank()) {
                val start = text.indexOf(pattern)
                if (start != -1) {
                    editor?.selectionModel?.apply {
                        removeSelection()
                        setSelection(start, start + pattern.length)
                    }
                } else {
                    notify("\"$pattern\" not found", type = NotificationType.WARNING)
                    return
                }
            }

            ActionManager.getInstance().tryToExecute(
                delegate,
                createMouseEventWrapper(consoleView),
                consoleView,
                null,
                false
            )
        }
    }

    fun print(msg: String, type: ConsoleViewContentType = NORMAL_OUTPUT) {
        ApplicationManager.getApplication().run {
            console.apply {
                print(config.hidePasswords(msg), type)
                // Force toolbars to update to update enabled states.
                ActivityTracker.getInstance().inc()
            }
        }
    }

    fun println(msg: String, type: ConsoleViewContentType = NORMAL_OUTPUT) {
        print("${config.hidePasswords(msg)}\n", type)
    }

    private fun saveToFile(virtualFile: VirtualFile? = null): Boolean {
        return try {
            val file = virtualFile?.asFile ?: chooseSaveFile(project) ?: return false
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
            val formattedTime = LocalDateTime.now().format(formatter)
            val comment = "# Saved on $formattedTime"
            file.writeText("$comment\n\n$text")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadFromFile(virtualFile: VirtualFile? = null): Boolean {
        return try {
            val file = virtualFile?.asFile
                ?: chooseFile(project, "Load Console Log")?.asFile
                ?: return false
            text = file.readText()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun chooseSaveFile(project: Project): File? {
        val descriptor = FileSaverDescriptor("Save Console Log", "").apply {
            isHideIgnored = true
            isForcedToUseIdeaFileChooser = true
        }
        FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project).also { dialog ->
            val file = config.logFilePath?.let { File(it) }
                ?: File(project.dir.asFile, Config.DEFAULT_LOG_FILE_NAME)
            val dir = VirtualFileWrapper(file.parentFile).virtualFile
            return dialog.save(dir, file.name)?.file
        }
    }

    fun processing() {
        updateActionToolbar()
    }
}