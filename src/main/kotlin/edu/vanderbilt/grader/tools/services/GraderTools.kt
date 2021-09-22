package edu.vanderbilt.grader.tools.services

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.wm.FocusWatcher
import com.intellij.ui.components.JBLabel
import edu.vanderbilt.grader.tools.Constants
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.ui.LogView
import edu.vanderbilt.grader.tools.ui.RepoView
import edu.vanderbilt.grader.tools.utils.config
import java.awt.*
import javax.swing.*
import javax.swing.SwingConstants.RIGHT
import kotlin.properties.Delegates

@Service
class GraderTools(val project: Project) : Disposable {
    val component: JComponent
        get() = panel

    lateinit var splitter: Splitter
    lateinit var repoView: RepoView
    lateinit var logView: LogView
    lateinit var statusTotal: JLabel
    lateinit var statusChecked: JLabel
    lateinit var statusSelected: JLabel

    inner class Status(total: Int = 0, checked: Int = 0, selected: Int = 0) {
        var total: Int by Delegates.observable(0) { _, old, new -> if (old != new) refreshTotal() }
        var checked: Int by Delegates.observable(0) { _, old, new -> if (old != new) refreshChecked() }
        var selected: Int by Delegates.observable(0) { _, old, new -> if (old != new) refreshSelected() }

        private fun refreshTotal() {
            statusTotal.text = total.toString().padStart(2)
        }

        private fun refreshChecked() {
            statusChecked.text = checked.toString().padStart(2)
        }

        private fun refreshSelected() {
            statusSelected.text = selected.toString().padStart(2)
        }

        init {
            this.total = total
            this.checked = checked
            this.selected = selected
            refreshTotal()
            refreshChecked()
            refreshSelected()
        }
    }

    lateinit var status: Status
        private set

    private lateinit var panel: JPanel
    private lateinit var statusPanel: JPanel

    private val config: Config
        get() = project.config

    /** Tab order must be the same as displayed in the IDE */
    enum class Tab {
        Repositories,
        Configuration;

        val index: Int
            get() = ordinal
    }

    init {
        buildView()
        installFocusWatcher()
    }

    private fun buildView() {
        panel = JPanel(BorderLayout())

        splitter = Splitter(false)

        repoView = RepoView(project)
        logView = LogView(project)

        splitter.firstComponent = repoView.component
        splitter.secondComponent = logView.component

        val font = Font("Monospaced", Font.PLAIN, JBLabel("").font.size)

        val headerPanel = JPanel(BorderLayout())

        statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        headerPanel.add(statusPanel, BorderLayout.WEST)
        headerPanel.add(JLabel("Build Version: ${Constants.BUILD}", RIGHT), BorderLayout.EAST)
        statusPanel.add(JBLabel("Total:"))
        statusTotal = JLabel("", RIGHT).apply { this.font = font }
        statusPanel.add(statusTotal)
        statusPanel.add(JBLabel("   Checked:"))
        statusChecked = JLabel("", RIGHT).apply { this.font = font }
        statusPanel.add(statusChecked)
        statusPanel.add(JBLabel("   Selected:"))
        statusSelected = JLabel("", RIGHT).apply { this.font = font }
        statusSelected.preferredSize.width = 80
        statusSelected.preferredSize.height = 80
        statusPanel.add(statusSelected)

        status = Status()

        //tabbedPane.add(Tab.Log.name, logView.component)
        panel.add(splitter, BorderLayout.CENTER)
        panel.add(headerPanel, BorderLayout.NORTH)
    }

    /**
     * Need to detect focus because framework refresh toolbar buttons
     * unless user clicks in window (not when tool window is opened).
     */
    private fun installFocusWatcher() {
        object : FocusWatcher() {
            override fun focusedComponentChanged(focusedComponent: Component?, cause: AWTEvent?) {
                if (focusedComponent != null && SwingUtilities.isDescendingFrom(focusedComponent, panel)) {
                    // Force tool windows to refresh enabled/disabled states.
                    ActivityTracker.getInstance().inc()
                }
            }
        }.install(panel)
    }

    override fun dispose() {
        println("DISPOSE: GraderTools")
    }
}
