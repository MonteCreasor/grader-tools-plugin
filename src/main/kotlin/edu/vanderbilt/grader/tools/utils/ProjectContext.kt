package edu.vanderbilt.grader.tools.utils

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

open class ProjectContext(val project: Project) {
    var isRunning: Boolean
        get() = project.isRunning
        set(value) {
            project.isRunning = value
        }

    var stopProcessing: Boolean
        get() = project.stopProcessing
        set(value) {
            project.stopProcessing = value
        }

    val console by lazy { project.console }

    val config by lazy { project.config }

    val repoView by lazy { project.repoView }

    val logView by lazy { project.logView }

    fun log(message: String) {
        project.log(message)
    }

    fun info(message: String, action: NotificationAction? = null) {
        project.info(message, action)
    }

    fun warn(message: String, action: NotificationAction? = null) {
        project.warn(message, action)
    }

    fun error(message: String, action: NotificationAction? = null) {
        project.error(message, action)
    }

    fun show(message: String, type: NotificationType, action: NotificationAction? = null) {
        project.showAndGetResult(message, type, action)
    }
}