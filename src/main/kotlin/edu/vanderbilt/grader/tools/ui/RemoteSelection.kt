package edu.vanderbilt.grader.tools.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import edu.vanderbilt.grader.tools.actions.RunAction.runInBackgroundWithProgress
import edu.vanderbilt.grader.tools.actions.RunAction.runModalWithProgress
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.persist.Config.Key
import edu.vanderbilt.grader.tools.ui.BaseTable.CheckBoxTableModel
import edu.vanderbilt.grader.tools.ui.RemoteSelection.RemoteRepo
import edu.vanderbilt.grader.tools.utils.*
import org.gitlab.api.GitlabAPI
import org.gitlab.api.models.GitlabProject
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URL
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import kotlin.properties.Delegates
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

typealias RemoteRepoProp = KProperty1<RemoteRepo, Any?>

class RemoteSelection(val project: Project) : DialogWrapper(project), Disposable {
    private var component: JComponent
    private lateinit var searchTextField: SearchTextField
    private lateinit var table: BaseTable<RemoteRepo>

    private val model: RemoteRepoTableModel
        get() = table.model as RemoteRepoTableModel

    private val config: Config
        get() = project.config

    lateinit var status: Status
        private set

    private lateinit var statusPanel: JPanel
    private lateinit var statusTotal: JLabel
    private lateinit var statusChecked: JLabel
    private lateinit var statusSelected: JLabel

    private lateinit var localRepos: List<Repo>

    private var projects = emptyList<GitlabProject>()
    private var remoteRepos = emptyList<RemoteRepo>()
    private val site = config[Key.GitSite]
    private val token = config[Key.GitToken]

    init {
        component = buildComponent()
        setupListeners()
        super.init()
    }

    override fun getDimensionServiceKey(): String = SAVED_STATE_KEY
    override fun createCenterPanel(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = searchTextField

    fun showAndGetResult(localRepos: List<Repo>): List<Repo> {
        title = "Add Gitlab Repositories"

        this.localRepos = localRepos

        try {
            when {
                site.isBlank() -> {
                    config.notifyErrorAndFix(project, Key.GitSite) {
                        "To connect to a remote Git repository, you need to set a Git site"
                    }
                    return emptyList()
                }
                token.isBlank() -> {
                    config.notifyErrorAndFix(project, Key.GitToken) {
                        "To connect to a remote Git repository, you need to set a Git token"
                    }
                    return emptyList()
                }
            }

            val url = try {
                URL("https://$site")
            } catch (e: Exception) {
                config.notifyErrorAndFix(project, Key.GitSite) { "Invalid Git site: ${e.message}" }
                return emptyList()
            }

            // fetchProjects may take a long time is not really interruptible, so run
            // it in the background and popup a modal dialog that will responsively
            // detect if the user clicks "cancel". If this happens, the project selection
            // will return an empty list and this background task will eventually terminate
            // in the background.
            var fetching = true
            var cancelled = false

            runInBackgroundWithProgress(project, "Loading repositories", true) {
                projects = fetchProjects(url, token)
                if (cancelled) {
                    projects = emptyList()
                }
                fetching = false
            }

            runModalWithProgress(project, "Loading repositories", true) {
                try {
                    while (fetching) {
                        it.checkCanceled()
                    }
                } catch (t: Throwable) {
                    if (it.isCanceled) {
                        cancelled = true
                    }
                }
            }

            remoteRepos = projects.map { project ->
                RemoteRepo(project).apply {
                    localRepo = localRepos.firstOrNull { repo ->
                        userName == repo.user && path == repo.repo
                    }
                    localRepo?.date = project.lastActivityAt
                }
            }

            return if (remoteRepos.isNotEmpty()) {
                showAndGetRepos(remoteRepos)
            } else {
                emptyList()
            }
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun showAndGetRepos(remoteRepos: List<RemoteRepo>): List<Repo> {
        try {
            model.rows = remoteRepos.toMutableList()
            project.graderTools.status.total = model.rowCount

            isOKActionEnabled = false
            return if (showAndGet()) {
                val list = model.rows.filter {
                    it.selected
                }.map {
                    val dir = it.ownerName.trim().capitalize().replace("""[\s]*""".toRegex(), "")
                    Repo(
                        selected = true,
                        user = it.userName,
                        repo = it.path,
                        date = it.lastModified,
                        proj = "",
                        dir = dir,
                        status = Repo.Status.Remote,
                        grade = ""
                    ).also { repo ->
                        repo.project = project
                    }
                }
                list
            } else {
                emptyList()
            }
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun buildComponent(): JComponent {
        table = object : BaseTable<RemoteRepo>(project, RemoteRepoTableModel()) {
            override fun changeSelection(rowIndex: Int, columnIndex: Int, toggle: Boolean, extend: Boolean) {
                super.changeSelection(rowIndex, columnIndex, toggle, extend)
                if (!isEditing) {
                    project.graderTools.status.selected = selectedRowCount
                }
            }
        }.apply {
            emptyText.text = ""
            setDefaultRenderer(
                String::class.java,
                EnableWrapperTableCellRenderer(getDefaultRenderer(String::class.java))
            )
            setDefaultRenderer(
                Boolean::class.java,
                EnableWrapperTableCellRenderer(getDefaultRenderer(Boolean::class.java))
            )
            columnModel.getColumn(indexOf(RemoteRepo::ownerName)).cellRenderer =
                EnableWrapperTableCellRenderer(OwnerCellRenderer())
            columnModel.getColumn(indexOf(RemoteRepo::lastModified)).cellRenderer =
                EnableWrapperTableCellRenderer(DateCellRenderer())
        }

        val (filterPanel, searchTextField) = table.createFilterPanel(this::class.java.name)
        this.searchTextField = searchTextField
        val wrapperPanel = JPanel(BorderLayout())
        wrapperPanel.add(filterPanel, BorderLayout.WEST)
        wrapperPanel.add(buildStatusPanel(), BorderLayout.EAST)
        return JPanel(BorderLayout()).apply {
            add(wrapperPanel, BorderLayout.NORTH)
            add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
        }
    }

    private fun buildStatusPanel(): JPanel {
        statusPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 5)).apply {
            isOpaque = false
        }

        statusPanel.add(JBLabel("Total:"))
        val font = Font("Monospaced", Font.PLAIN, JBLabel("").font.size)

        statusTotal = JLabel("", SwingConstants.RIGHT).apply { this.font = font }
        statusPanel.add(statusTotal)

        statusPanel.add(JBLabel("   Checked:"))
        statusChecked = JLabel("", SwingConstants.RIGHT).apply { this.font = font }
        statusPanel.add(statusChecked)

        statusPanel.add(JBLabel("   Selected:"))
        statusSelected = JLabel("", SwingConstants.RIGHT).apply { this.font = font }
        statusSelected.preferredSize.width = 80
        statusSelected.preferredSize.height = 80
        statusPanel.add(statusSelected)

        status = Status()

        return statusPanel
    }

    private fun setupListeners() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger)
                    doPop(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger)
                    doPop(e)
            }

            private fun doPop(e: MouseEvent) {
                if (e.component?.isShowing == true) {
                    val row = table.rowAtPoint(e.point)
                    val column = table.columnAtPoint(e.point)
                    val popupComponent = createPopupMenu(row, column)?.component
                    popupComponent?.show(table, e.x, e.y)
                }
            }
        })
    }

    fun createPopupMenu(row: Int, column: Int): ActionPopupMenu? {
        val (modelRow, modelColumn) = table.modelRowAndColumn(row, column)
        val actionGroup = DefaultActionGroup()
        when (modelColumn) {
            BaseTable.CHECK_BOX_COLUMN -> Unit
            else -> {
                val hyperLink = model.getHyperLink(modelRow, modelColumn)
                actionGroup.add(NavigateToAction(hyperLink))
            }
        }

        return if (actionGroup.childrenCount > 0) {
            ActionManager.getInstance().createActionPopupMenu("RepositoriesPopup", actionGroup)
        } else {
            null
        }
    }

    private inner class NavigateToAction(val link: String?) :
        AnAction("Navigate", "Navigate to link", IconLoader.findIcon("/toolWindow/link.svg")), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            if (!link.isNullOrBlank()) {
                table.navigateTo(link)
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !link.isNullOrBlank()
        }
    }

    private inner class OwnerCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val myModel = table.model as RemoteRepoTableModel
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).apply {
                val repo = myModel.getObjectAt(table.convertRowIndexToModel(row))
                if (repo.selected) {
                    when {
                        myModel.findDuplicatesOf(table.convertRowIndexToModel(row)).isNotEmpty() -> {
                            border = BorderFactory.createLineBorder(Color.RED)
                            toolTipText = "User has already been selected"
                        }
                        repo.localRepo != null -> {
                            border = BorderFactory.createLineBorder(Color.RED)
                            toolTipText = "User has already been downloaded"
                        }
                        else -> {
                            border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
                        }
                    }
                } else {
                    border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
                }
            }
        }
    }

    class EnableWrapperTableCellRenderer(var delegate: TableCellRenderer) : TableCellRenderer {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component =
            delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).apply {
                with(table.model as RemoteRepoTableModel) {
                    isEnabled = getObjectAt(table.convertRowIndexToModel(row)).localRepo == null
                }
            }
    }

    fun fetchProjects(url: URL, token: String): MutableList<GitlabProject> {
        val api = GitlabAPI.connect(url.toString(), token)
        return api.membershipProjects
    }

    inner class FetchProjectsTask(val url: URL, val token: String) :
        Task.Modal(project, "Searching for remote repositories", true) {
        lateinit var foundText: String

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true
            projects = fetchProjects(url, token).let {
                indicator.checkCanceled()
                it
            }
            val count = projects.count()
            foundText = "Found $count ${"repository".ies(count)}"
            indicator.text = foundText
        }

        override fun onThrowable(error: Throwable) {
            error("unable to access remote gitlab repositories at $url: ${error.message}")
        }

        override fun onSuccess() {
            project.logView.println(foundText)
        }
    }

    /**
     * Passed rows array contains JTable view indices.
     */
    override fun dispose() {
        println("DISPOSE: RepoSelection")
        super.dispose()
    }

    inner class RemoteRepoTableModel
        : CheckBoxTableModel<RemoteRepo>(RemoteRepo::class.java, 0) {
        private var selectedCount = 0
        private var dupCount = 0

        override fun setValueAt(value: Any?, row: Int, column: Int) {
            when (propAt(column)) {
                RemoteRepo::selected -> {
                    val selected = value as Boolean
                    val wasSelected = getValueAt(row, column)
                    if (wasSelected != selected) {
                        trackSelections(selected, row)
                    }
                    val selectedAndDownloaded = selected && getObjectAt(row).localRepo != null
                    isOKActionEnabled = selectedCount > 0 && dupCount == 0 && !selectedAndDownloaded
                    super.setValueAt(value, row, column)
                    status.checked = checkedRows.count()
                }
                else -> super.setValueAt(value, row, column)
            }
        }

        private fun trackSelections(selected: Boolean, row: Int) {
            if (selected) selectedCount++ else selectedCount--
            val duplicates = findDuplicatesOf(row)
            if (selected) {
                selectedCount++
                if (duplicates.isNotEmpty()) {
                    dupCount++
                }
            } else {
                selectedCount--
                if (duplicates.isNotEmpty()) {
                    dupCount--
                }
            }
            duplicates.forEach {
                model.fireTableCellUpdated(it, indexOf(RemoteRepo::userName))
            }
        }

        override fun isCellEditable(row: Int, column: Int): Boolean =
            column == indexOf(RemoteRepo::selected) && getObjectAt(row).localRepo == null

        override fun getHyperLink(row: Int, column: Int): String? {
            if (row == -1 || column == -1) {
                return null
            }

            val repo = getObjectAt(row)
            return when (propAt(column)) {
                RemoteRepo::ownerName -> repo.remoteOwnerUrl
                RemoteRepo::path -> repo.remotePathUrl
                RemoteRepo::userName -> repo.remoteOwnerUrl
                else -> null
            }
        }

        fun findDuplicatesOf(modelRow: Int, selected: Boolean = true): Set<Int> {
            val userName = model.getObjectAt(modelRow).userName
            return model.rows
                .filterIndexed { row, repo -> row != modelRow && (!selected || repo.selected) }
                .filterIndexed { _, repo -> repo.userName == userName }
                .mapIndexed { row, _ -> row }.toSet()
        }
    }

    inner class RemoteRepo(proj: GitlabProject) {
        @TableColumnProp(index = 0, value = "", editable = true)
        var selected: Boolean = false

        @TableColumnProp(index = 1, value = "Owner", editable = false)
        val ownerName: String = proj.owner?.name ?: proj.name

        @TableColumnProp(index = 2, value = "UserID", editable = false)
        val userName: String = proj.owner?.username
            ?: proj.pathWithNamespace?.split("/")?.get(0)
            ?: "unknown"

        @TableColumnProp(index = 3, value = "Repository", editable = false)
        val path: String = proj.path
            ?: proj.pathWithNamespace?.split("/")?.get(1)
            ?: "unknown"

        @TableColumnProp(index = 4, value = "Last Activity", editable = false)
        val lastModified: Date = proj.lastActivityAt

        @TableColumnProp(index = 5, value = "Status", editable = false)
        val status: String
            get() = if (localRepo != null) "added" else ""

        var localRepo: Repo? = null

        val remoteOwnerUrl: String
            get() = "https://${project.config[Key.GitSite]}/${userName}"

        val remotePathUrl: String
            get() = "${remoteOwnerUrl}/$path"

    }

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

    companion object {
        private val SAVED_STATE_KEY = RemoteSelection::class.java.name

        fun indexOf(name: String): Int =
            RemoteRepo::class.declaredMemberProperties.firstOrNull { it.name == name }
                ?.findAnnotation<TableColumnProp>()?.index ?: -1

        fun indexOf(prop: RemoteRepoProp): Int = indexOf(prop.name)
    }
}
