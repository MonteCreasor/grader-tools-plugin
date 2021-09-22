package edu.vanderbilt.grader.tools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

class CommitDialog(project: Project, message: String) : DialogWrapper(project) {
    private val textArea: JTextArea
    private val panel: JPanel

    init {
        title = "Commit changes"
        textArea = JTextArea(message)
        textArea.rows = 5
        textArea.preferredSize = Dimension(500, 100)
        textArea.lineWrap = true
        textArea.isEditable = true
        panel = JPanel(BorderLayout())
        panel.add(JLabel("Commit message"), BorderLayout.NORTH)
        panel.add(ScrollPaneFactory.createScrollPane(textArea), BorderLayout.CENTER)
        super.init()
    }

    override fun createCenterPanel(): JComponent {
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? = textArea

    fun showAndGetResult(): String? {
        return if (showAndGet()) {
            textArea.text
        } else {
            null
        }
    }
}