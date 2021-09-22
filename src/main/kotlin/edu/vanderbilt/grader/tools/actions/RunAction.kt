package edu.vanderbilt.grader.tools.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import edu.vanderbilt.grader.tools.Constants

object RunAction {
    fun runInBackgroundWithProgress(
        project: Project,
        title: String,
        canCancel: Boolean = true,
        action: (indicator: ProgressIndicator) -> Unit
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canCancel) {
            override fun run(indicator: ProgressIndicator) {
                action(indicator)
            }
        })
    }

    fun runModalWithProgress(
        project: Project,
        title: String,
        canCancel: Boolean = true,
        action: (indicator: ProgressIndicator) -> Unit
    ) {
        ProgressManager.getInstance().run(object : Task.Modal(project, title, canCancel) {
            override fun run(indicator: ProgressIndicator) {
                action(indicator)
            }
        })
    }

    fun runCommand(project: Project?, name: String, runnable: () -> Unit) {
        CommandProcessor.getInstance().executeCommand(project, runnable, name, Constants.GROUP_ID)
    }

    fun runReadAction(action: () -> Unit) {
        ApplicationManager.getApplication().runReadAction {
            action()
        }
    }

    fun ProgressIndicator.updateProgess(fraction: Double, text: String) {
        this.fraction = fraction
        this.text = text
    }
}
