package edu.vanderbilt.grader.tools.utils

import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.*
import com.intellij.notification.NotificationType.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import edu.vanderbilt.grader.tools.Constants.NOTIFICATION_SUB_TITLE
import edu.vanderbilt.grader.tools.Constants.NOTIFICATION_TITLE
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.persist.LogState
import edu.vanderbilt.grader.tools.persist.RepoState
import edu.vanderbilt.grader.tools.services.GraderTools
import edu.vanderbilt.grader.tools.ui.*

val STOP_PROCESSING_KEY = Key<Boolean>("GraderStopProcessing")
val PROCESSING_KEY = Key<Boolean>("GraderProcessing")
val REPOS_KEY = Key<List<Repo>>("ReposKey")
//val REFRESH_KEY = Key<Actions.Refresh>("RefreshKey")

val Project.console: ConsoleView
    get() = logView.consoleView

/** True if any grader action is running */
var Project.isRunning: Boolean
    get() = getUserData(PROCESSING_KEY) == true
    set(value) {
        putUserData(PROCESSING_KEY, value)
    }

var Project.stopProcessing: Boolean
    get() = getUserData(STOP_PROCESSING_KEY) == true
    set(value) {
        putUserData(STOP_PROCESSING_KEY, value)
    }

val Project.repoView: RepoView
    get() = graderTools.repoView

val Project.logView: LogView
    get() = graderTools.logView

val Project.config: Config
    get() = ServiceManager.getService(this, Config::class.java)

val Project.repoState: RepoState
    get() = ServiceManager.getService(this, RepoState::class.java)

val Project.logState: LogState
    get() = ServiceManager.getService(this, LogState::class.java)

val Project.graderTools: GraderTools
    get() = ServiceManager.getService(this, GraderTools::class.java)

fun Project.log(message: String) {
    PluginManager.getLogger().info(message)
}

fun Project.info(message: String, action: NotificationAction? = null) {
    showAndGetResult(message, INFORMATION, action)
    logView.println(message)
}

fun Project.warn(message: String, action: NotificationAction? = null) {
    showAndGetResult(message, WARNING, action)
    logView.println("WARNING: $message")
}

fun Project.error(message: String, action: NotificationAction? = null) {
    showAndGetResult(message, ERROR, action)
    logView.println("ERROR: $message")
}

private val balloonDisplayGroup =
    // For Kotlin 1.3
    NotificationGroup(
        "Grader notifications",
        NotificationDisplayType.BALLOON,
        true,
        null,
        null
    )
 //for Idea 2020.2 and Kotlin 1.4
//    NotificationGroup(
//        displayId = "Grader notifications",
//        displayType = NotificationDisplayType.BALLOON,
//        isLogByDefault = true,
//        toolWindowId = null,
//        icon = null
//    )

fun Project.showAndGetResult(message: String, type: NotificationType, action: NotificationAction? = null) {
    notify(message, this, type, action)
}

fun notify(
    message: String,
    project: Project? = null,
    type: NotificationType = INFORMATION,
    action: NotificationAction? = null
) {
    ApplicationManager.getApplication().invokeLater {
        val notification = balloonDisplayGroup.createNotification(
            NOTIFICATION_TITLE, NOTIFICATION_SUB_TITLE, message, type
        ).apply {
            if (type == ERROR) {
                isImportant = true
            }
            if (action != null) {
                addAction(action)
            }
        }

        Notifications.Bus.notify(notification, project)
    }
}

fun simpleAction(text: String, action: () -> Unit): NotificationAction =
    NotificationAction.createSimple(text, action)
