package edu.vanderbilt.grader.tools

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory.SERVICE
import edu.vanderbilt.grader.tools.utils.graderTools

class GraderToolWindowFactory : ToolWindowFactory, DumbAware {
    // Create the tool window content.
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val graderTools = project.graderTools
        val content = SERVICE.getInstance().createContent(graderTools.component, null, false)
        toolWindow.contentManager.addContent(content)
    }
}