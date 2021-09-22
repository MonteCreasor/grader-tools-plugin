package edu.vanderbilt.grader.tools.utils

import com.intellij.openapi.application.ApplicationManager
import javax.swing.SwingUtilities

object ThreadUtils {
    fun assertUiThread() {
        check(SwingUtilities.isEventDispatchThread()) {
            "Attempt to access UI outside of EDT"
        }
    }

    inline fun <T> runOnUiThread(wait: Boolean = true, crossinline block: () -> T?): T? {
        return when {
            SwingUtilities.isEventDispatchThread() -> {
                block()
            }

            wait -> {
                var result: T? = null
                ApplicationManager.getApplication().invokeAndWait {
                    assertUiThread()
                    result = block()
                }
                result
            }

            else -> {
                ApplicationManager.getApplication().invokeLater {
                    assertUiThread()
                    block()
                }
                null
            }
        }
    }
}