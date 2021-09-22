package edu.vanderbilt.grader.tools.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableSpeedSearch
import edu.vanderbilt.grader.tools.Constants.DEFAULT_SAVE_FILE_NAME
import edu.vanderbilt.grader.tools.actions.Actions
import edu.vanderbilt.grader.tools.actions.Actions.SelectionType
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.ui.BaseTable.Companion.CHECK_BOX_COLUMN
import edu.vanderbilt.grader.tools.ui.Repo.Status
import edu.vanderbilt.grader.tools.ui.Repo.Status.*
import edu.vanderbilt.grader.tools.utils.*
import edu.vanderbilt.grader.tools.utils.ActionResult.*
import edu.vanderbilt.grader.tools.utils.ResizeProperty.GROW
import edu.vanderbilt.grader.tools.utils.ResizeProperty.SHRINK
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeEvent
import javax.swing.table.*

class RepoView(project: Project) : ProjectContext(project), Disposable {
    private val panel: JPanel = JPanel(BorderLayout())
    lateinit var table: BaseTable<Repo>
    val component: JComponent
        get() = panel
    internal val model: RepoTableModel get() = table.model as RepoTableModel
    private lateinit var actionGroup: DefaultActionGroup

    private val repoSaveFile: File
        get() = with(config[Config.Key.SavedReposFileName]) {
            val name = if (isNotBlank()) {
                this
            } else {
                DEFAULT_SAVE_FILE_NAME
            }
            File(project.dir.asFile, name)
        }

    private var actions: Actions = Actions(this)

    init {
        buildView()
        setupListeners()
        table.autoResizeMode = AUTO_RESIZE_SUBSEQUENT_COLUMNS // same as default
        TableSpeedSearch(table)
        ApplicationManager.getApplication().invokeLater { loadTable(project.repoState.repos) }
    }

    private fun buildView() {
        val model = RepoTableModel(project)
        table = RepoTable(project, model)
        //TODO: table.setupSpeedSearch()

        panel.add(ScrollPaneFactory.createScrollPane(table))
        actionGroup = DefaultActionGroup()
        actionGroup.add(ResizeAction())
        actionGroup.addAll(actions.getActions(SelectionType.Checked))
        panel.add(createToolbar(actionGroup), BorderLayout.WEST)

        table.autoCreateRowSorter = true

        // Setup cell renderers
        table.columnModel.getColumn(model.fieldToIndex(Repo::status)).cellRenderer = StatusCellRendered()
        table.columnModel.getColumn(model.fieldToIndex(Repo::proj)).cellRenderer = ProjectCellRendered()
        table.columnModel.getColumn(model.fieldToIndex(Repo::date)).cellRenderer = LastUpdateCellRenderer()

        setupGradeColumn(table)
    }

    private fun setupGradeColumn(table: JTable) {
        val gradeColumn = model.fieldToIndex(Repo::grade)
        val alignment = SwingConstants.RIGHT

        table.columnModel.getColumn(gradeColumn).cellRenderer =
            DefaultTableCellRenderer().apply { horizontalAlignment = alignment }
        table.columnModel.getColumn(gradeColumn).headerRenderer = HeaderRenderer(table, alignment)

        val rowSorter = if (table.rowSorter != null) {
            table.rowSorter as TableRowSorter<*>
        } else {
            TableRowSorter<TableModel>(model).apply {
                table.rowSorter = this
            }
        }

        rowSorter.setComparator(gradeColumn,
            object : Comparator<String> {
                val gradeRegex = """[\s]*([0-9]+[.]?[0-9]+)%.*""".toRegex()
                override fun compare(o1: String?, o2: String?): Int {
                    val matches1 = gradeRegex.matchEntire(o1 ?: "0%")
                    val matches2 = gradeRegex.matchEntire(o2 ?: "0%")

                    val percent1 = if (matches1?.groupValues?.isNotEmpty() == true) {
                        matches1.groupValues[1].toDoubleOrNull() ?: 0.0
                    } else {
                        0.0
                    }

                    val percent2 = if (matches2?.groupValues?.isNotEmpty() == true) {
                        matches2.groupValues[1].toDoubleOrNull() ?: 0.0
                    } else {
                        0.0
                    }

                    return percent1.compareTo(percent2)
                }
            })
    }

    private fun buildProjectComboBox(row: Int): JComboBox<ComboBoxProjectItem> {
        val comboBox = ComboBox<ComboBoxProjectItem>()
        val repo = model.getObjectAt(row.toModelRow())
        repo.projects.sortedBy {
            it.name
        }.forEachIndexed { i, file ->
            comboBox.addItem(ComboBoxProjectItem(file.path))
            if (repo.proj == file.name) {
                comboBox.selectedIndex = i
            }
        }

        if (repo.proj.isBlank()) {
            comboBox.selectedIndex = -1
        }

        return comboBox
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

            //TODO: work on this -- change from list to yes/cancel popup
//            override fun mouseClicked(e: MouseEvent) {
//                val (_, modelColumn) = table.modelRowAndColumn(e)
//                if (modelColumn == model.fieldToIndex(Repo::proj)) {
//                    JBPopupFactory.getInstance().createConfirmation(
//                        "Local repository does not exist",
//                        "Clone",
//                        "Cancel",
//                        { },
//                        0
//                    ).apply {
//                        show(RelativePoint(e))
//                    }
//                }
//                super.mouseClicked(e)
//            }

            private fun doPop(e: MouseEvent) {
                if (e.component?.isShowing == true) {
                    val row = table.rowAtPoint(e.point)
                    val column = table.columnAtPoint(e.point)
                    val selected = table.selectedRowCount
                    val popupComponent = createPopupMenu(selected, row, column).component
                    popupComponent.show(table, e.x, e.y)
                }
            }
        })
    }

    /**
     * Trick function that will change all repo project column
     * values to match the project of the repo at the specified row.
     * This only occurs if the user changed a proj value and the row
     * is checked, in which case, all other checked rows will change
     * to the same project. This works even when the actual path to
     * the same projects differ because students can't follow directions
     * and use the same Gitlab repo structure.
     */
    fun onProjectChanged(row: Int, column: Int) {
        val (modelRow, modelColumn) = table.modelRowAndColumn(row, column)
        with(table.myModel) {
            val repo = getObjectAt(modelRow)
            repo.refresh()
            sizeColumnsToFit()
            if (repo.selected) {
                val projDir = repo.projDir
                checkedRows.forEach { row ->
                    if (row != modelRow) {
                        val nextRepo = getObjectAt(row)
                        if (nextRepo.projDir != projDir) {
                            val proj = nextRepo.projects.firstOrNull { it.name == projDir?.name }
                            setValueAt(proj?.path ?: "", row, modelColumn)
                            nextRepo.refresh()
                        }
                    }
                }
            }
        }
    }

    fun createPopupMenu(selected: Int, row: Int, column: Int): ActionPopupMenu {
        val (modelRow, modelColumn) = table.modelRowAndColumn(row, column)
        val actionGroup = DefaultActionGroup()
        when (modelColumn) {
            CHECK_BOX_COLUMN -> Unit
            else -> {
                val hyperLink = model.getHyperLink(modelRow, modelColumn)
                actionGroup.addSeparator("$selected selected")
                actionGroup.add(NavigateToAction(hyperLink))
                actionGroup.add(OpenAction(model.getObjectAt(modelRow)))
                actionGroup.addAll(actions.getActions(SelectionType.Selected).filterNot {
                    it.type == Actions.Type.Gitlab
                            || it.type == Actions.Type.Load
                            || it.type == Actions.Type.Stop
                })
            }
        }

        return ActionManager.getInstance()
            .createActionPopupMenu("RepositoriesPopup", actionGroup)
    }

    override fun dispose() {
        println("DISPOSE: RepoView")
    }

    private fun loadTable(repos: MutableList<Repo>) {

        // Set each repo project property and clear any status
        // that doesn't make sense when reloading from disk.
        repos.forEach { repo ->
            repo.project = project
            when (repo.status) {
                // Clear any status that doesn't make sense when reloading from disk.
                NoInternet, Grading, Installing, Committing, Cloning,
                Deleting, Refreshing, Pulling, Pushing, Querying -> {
                    repo.refresh(false)
                }
                else -> Unit
            }
        }

        // For proper Intellij save/restore state, there must be a single
        // ground truth for all repos which is maintained in RepoState and
        // is passed in to this function as a parameter.
        model.rows = repos

        project.graderTools.status.total = model.rowCount
        project.graderTools.status.checked = model.checkedRows.count()
    }

    private fun loadTable(file: File = repoSaveFile) {
        if (file.isFile) {
            loadFromFile(file)
        }
    }

    private inline fun <reified T> invokeAction(name: String, key: Key<T>? = null, data: T? = null) {
        ApplicationManager.getApplication().invokeLater {
            invoke(name, key, data)
        }
    }

    private inline fun <reified T> invokeActionAndWait(name: String, key: Key<T>? = null, data: T? = null) {
        ApplicationManager.getApplication().invokeAndWait {
            invoke(name, key, data)
        }
    }

    private inline fun <reified T> invoke(name: String, key: Key<T>? = null, data: T? = null) {
        val action = actionGroup.childActionsOrStubs.find { action: AnAction ->
            action.templatePresentation.text == name
        }
        checkNotNull(action) { "invoke($name): action does not exist!" }

        with(DataManager.getInstance()) {
            val dataContext = getDataContext(table)
            if (key != null && data != null) {
                saveInDataContext(dataContext, key, data)
            }
            action.actionPerformed(
                AnActionEvent.createFromAnAction(
                    action,
                    null,
                    ActionPlaces.TOOLBAR,
                    dataContext
                )
            )
        }
    }

    private fun loadFromFile(fromFile: File? = null): Int {
        val file = fromFile ?: chooseFile(project)?.asFile ?: return 0

        val repos = model.load(file)
        if (repos.isEmpty()) {
            return 0
        }

        val replace = if (model.rowCount > 0) {
            val response = MessageDialogBuilder.yesNoCancel(
                "Confirm Add or Replace",
                """|Repositories already exist.
                   |Choose Add to add the repositories
                   |or Replace to remove existing repositories 
                   |before adding the new ones.""".trimMargin("|")
            ).yesText("Add")
                .noText("Replace")
                .cancelText(CommonBundle.message("button.cancel"))
                .show(project)

            when (response) {
                Messages.YES -> false
                Messages.NO -> true
                else -> return 0
            }
        } else {
            true
        }

        if (replace) {
            clear()
        }

        repos.forEach { it.project = project }

        val added = model.addAll(repos)
        if (added.isNotEmpty()) {
            table.rowSorter.allRowsChanged()
            model.fireTableDataChanged()
            added.forEach { repo ->
                refreshStatus(repo)
            }
        }

        return added.count()
    }

    private fun saveToFile(file: File? = null): Boolean {
        val title = "Save Repositories"
        val saveFile = file ?: chooseSaveFile(project, title, repoSaveFile.path) ?: return false
        return model.save(saveFile)
    }

    private fun createToolbar(actions: ActionGroup): JComponent {
        val actionToolbar: ActionToolbar =
            ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, false)
        return actionToolbar.component
    }

    fun clear() {
        table.clearSelection()
        table.autoCreateColumnsFromModel = false
        with(table.model as RepoTableModel) {
            rows.clear()
            table.rowSorter.allRowsChanged()
            fireTableDataChanged()
        }
    }

    fun remove(repo: Repo) {
        ThreadUtils.runOnUiThread {
            model.removeObject(repo)
        }
    }

    fun refreshStatusAndWait(repo: Repo? = null, refreshProjects: Boolean = false, refreshRow: Boolean = true) {
        ThreadUtils.runOnUiThread(true) {
            refreshStatus(repo, refreshProjects, refreshRow)
        }
    }

    fun refreshStatus(
        repo: Repo? = null,
        refreshProjects: Boolean = false,
        fireRowUpdate: Boolean = true
    ) {
        /** Looks best when run in calling thread */
        if (repo == null) {
            // Recursively refresh all rows.
            model.rows.forEach { refreshStatus(it, refreshProjects, fireRowUpdate = false) }
            model.fireTableRowsUpdated(0, model.rowCount - 1)
        } else {
            // Refresh one row.
            repo.refresh(refreshProjects)
            if (fireRowUpdate) {
                val row = model.getRowFor(repo)
                model.fireTableRowsUpdated(row, row)
            }
        }
    }

    fun refresh() {
        ThreadUtils.runOnUiThread {
            if (model.rows.isNotEmpty()) {
                model.fireTableDataChanged()
            }
        }
    }

    fun getRepos(
        selectionType: SelectionType = SelectionType.All,
        cloned: Boolean = false,
        viewOrder: Boolean = true
    ): List<Repo> {

        val filteredRepos = model.rows.withIndex().filter {
            when (selectionType) {
                SelectionType.Checked -> it.value.selected
                SelectionType.Selected -> table.isRowSelected(table.convertRowIndexToView(it.index))
                SelectionType.All -> true
            }
        }.filter {
            !cloned || it.value.rootDir.isDirectory
        }

        return if (viewOrder) {
            filteredRepos.sortedBy {
                table.convertRowIndexToView(it.index)
            }
        } else {
            filteredRepos
        }.map { it.value }.toList()
    }

    private fun Int.toModelRow(): Int = table.convertRowIndexToModel(this)
    private fun Int.toModelCol(): Int = table.convertColumnIndexToModel(this)

    fun addAll(newRepos: List<Repo>): Int {
        // First check if there are any duplicate destination directories
        // If not, then just add all the repos; if so, then add them one
        // by one prompting to replace as required.
        val duplicates = newRepos.filter { newRepo ->
            model.rows.count { oldRepo ->
                oldRepo.user == newRepo.user
            } != 0
        }

        val oldCount = model.rowCount
        if (duplicates.isEmpty()) {
            model.addAll(newRepos)
        } else {
            var actionResult: ActionResult = Skip

            newRepos.forEach { newRepo ->
                actionResult = tryAdd(newRepo, actionResult)
                if (actionResult == Cancel) {
                    return@forEach
                }
            }
        }

        return model.rowCount - oldCount
    }

    fun sizeColumnsToFit() {
        ThreadUtils.runOnUiThread {
            table.sizeColumnsToFit()
        }
    }

    private fun tryAdd(repo: Repo, lastAction: ActionResult): ActionResult {
        check(lastAction != Cancel) {
            "Invalid lastAction parameter: ActionResult.Cancel"
        }

        val oldRepo = model.rows.firstOrNull { oldRepo ->
            // Conflict occurs if the repo has the same dest dir
            // as an existing entry, or if the repo has the same
            // user name and repo.
            oldRepo.dir == repo.dir || (oldRepo.user == repo.user && oldRepo.repo == repo.repo)
        }

        return if (oldRepo == null) {
            model.addAll(listOf(repo))
            lastAction
        } else when (lastAction) {
            ReplaceAll -> {
                invokeActionAndWait(Actions.Type.Delete.id, REPOS_KEY, listOf(oldRepo))
                model.addAll(listOf(repo))
                lastAction
            }
            SkipAll -> {
                logView.println("Skipping ${repo.user}:${repo.repo}.")
                lastAction
            }
            else -> {
                val action = ReplaceDialog(project, oldRepo, repo).showAndGetResult()
                when (action) {
                    Replace, ReplaceAll -> {
                        invokeActionAndWait(Actions.Type.Delete.id, REPOS_KEY, listOf(oldRepo))
                        model.addAll(listOf(repo))
                    }
                    Skip, SkipAll -> logView.println("Skipping ${repo.user}:${repo.repo}.")
                    else -> logView.println("Operation cancelled.")
                }
                action
            }
        }
    }

    /** Looks best when run in calling thread */
    fun setStatus(repo: Repo, status: Status, wait: Boolean = true) {
        ThreadUtils.runOnUiThread(wait) {
            model.setValueAt(status, repo, Repo::status)
        }
    }

    fun setDate(repo: Repo, date: Date?) {
        ThreadUtils.runOnUiThread(true) {
            model.setValueAt(date, repo, Repo::date)
        }
    }

    fun exists(repo: Repo): Boolean {
        return ThreadUtils.runOnUiThread(true) {
            model.getRowFor(repo, mustExist = false) != -1
        }!!
    }

    fun reset(repos: List<Repo>, props: RepoPropList) {
        ThreadUtils.runOnUiThread {
            repos.forEach {
                val repo = it
                model.reset(repo, props)
            }
        }
    }

    fun selectedCount(selectionType: SelectionType, cloned: Boolean = false): Int =
        getRepos(selectionType, cloned).count()

    private class LastUpdateCellRenderer(format: SimpleDateFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")) :
        DateCellRenderer(format) {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).apply {
                val repo = (table.model as RepoTableModel).getObjectAt(table.convertRowIndexToModel(row))
                foreground = if ((repo.lastPull ?: Date(0)) < (repo.date ?: (Date(0)))) {
                    toolTipText = "Remote changes detected"
                    Color.RED
                } else {
                    toolTipText = "No remote changes detected"
                    UIManager.getColor("Label.foreground")
                }
            }
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

    private inner class OpenAction(val repo: Repo) :
        AnAction("Open", "Open project", AllIcons.Actions.Menu_open), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            ProjectManager.getInstance().loadAndOpenProject(repo.projDir!!.absolutePath)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = table.selectedRowCount > 0 && repo.projDir?.isGradleProject == true
        }
    }

    private inner class SaveAction :
        AnAction("Save", "Save repositories to a file", AllIcons.Actions.Menu_saveall), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = try {
            if (saveToFile()) {
                val count = table.selectedRowCount
                info("${"repository".ies(count)} saved.")
            } else {
                info("Save operation cancelled.")
            }
        } catch (e: Exception) {
            error("Save exception: ${e.message}")
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = table.selectedRowCount > 0
        }
    }

    private inner class DeleteAction(row: Int) :
        AnAction("Delete", "Delete selected repository", AllIcons.General.Remove), DumbAware {
        val repo = model.getObjectAt(table.convertRowIndexToModel(row))

        override fun actionPerformed(e: AnActionEvent) {
            invokeAction(Actions.Type.Delete.id, REPOS_KEY, listOf(repo))
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled =
                table.selectedRowCount > 0 && project.isRunning == false
        }
    }

    private inner class RefreshAction(row: Int) :
        AnAction(
            "Refresh",
            "Refresh row",
            AllIcons.Actions.Refresh
        ), DumbAware {
        val repo = model.getObjectAt(table.convertRowIndexToModel(row))

        override fun actionPerformed(e: AnActionEvent) {
            refreshStatus(repo, refreshProjects = true)
            logView.println("Refreshed ${repo.user}:${repo.repo}.")
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled =
                table.selectedRowCount > 0 && project.isRunning == false
        }
    }

    private inner class ResizeAction :
        AnAction(
            "Optimize columns widths",
            "Optimize columns widths",
            IconLoader.findIcon("/toolWindow/grow.svg")
        ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            table.optimizeColumnWidths(resizeType = GROW and SHRINK)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = table.rowCount > 0
        }
    }

    private class HeaderRenderer(table: JTable, var alignment: Int) : TableCellRenderer {
        private val renderer = table.tableHeader.defaultRenderer
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            col: Int
        ): Component = (renderer.getTableCellRendererComponent(
            table,
            value,
            isSelected,
            hasFocus,
            row,
            col
        ) as JLabel).apply {
            horizontalAlignment = alignment
        }
    }

    /**
     * Trick used to wrap JLabel so that combo box items will show
     * up as project names without parent being specified, but actual
     * Repo.proj value will include a possible parent.
     */
    internal class ComboBoxProjectItem(val relPath: String) : JLabel(File(relPath).name) {
        override fun toString(): String {
            return File(relPath).name
        }
    }

    open inner class StatusCellRendered : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).apply {
                text = value.toString()
                val (modelRow, modelColumn) = table.modelRowAndColumn(row, column)
                val status = model.getValueAt(modelRow, modelColumn) as Status
                icon = status.icon
                foreground = when (status) {
                    NoFiles, NoProject, NoGradle, NoGrader, Error, NoInternet ->
                        Color.RED
                    Grading, Installing, Committing, Pulling, Cloning, Deleting, Refreshing, Pushing, Querying ->
                        Color.GREEN
                    else -> UIManager.getColor("Label.foreground")
                }
                toolTipText = status.desc
            }
        }
    }

    private inner class RepoTable(project: Project, model: RepoTableModel) : BaseTable<Repo>(project, model) {
        override fun getCellEditor(row: Int, column: Int): TableCellEditor {
            return if (column.toModelCol() == (model as RepoTableModel).fieldToIndex(Repo::proj)) {
                DefaultCellEditor(buildProjectComboBox(row)).apply {
                    addCellEditorListener(object : CellEditorListener {
                        override fun editingStopped(e: ChangeEvent) {
                            val value = (e.source as DefaultCellEditor).cellEditorValue
                            // If project has not been set yet and user opens combo box and
                            // chooses not to select any item, cellEditorValue will be null.
                            if (value != null) {
                                val item = value as ComboBoxProjectItem
                                val repo = myModel.getObjectAt(table.convertRowIndexToModel(row))
                                check(item.relPath == repo.proj)
                                onProjectChanged(row, column)
                            }
                        }

                        override fun editingCanceled(e: ChangeEvent) {
                        }
                    })
                }
            } else {
                super.getCellEditor(row, column)
            }
        }

        override fun changeSelection(rowIndex: Int, columnIndex: Int, toggle: Boolean, extend: Boolean) {
            super.changeSelection(rowIndex, columnIndex, toggle, extend)
            if (!isEditing) {
                project.graderTools.status.selected = selectedRowCount
            }
        }

        /**
         * Trick to support cells that only enter edit mode when double-clicked.
         * This is a nicer UI for this table where clicking is primarily used to
         * select rows and not to edit cells.
         */
        override fun isCellEditable(row: Int, column: Int): Boolean {
            val (_, modelColumn) = modelRowAndColumn(row, column)
            return super.isCellEditable(row, column) && when (modelColumn) {
                (model as RepoTableModel).fieldToIndex(Repo::selected) -> true
                else -> clickCount == 2
            }
        }
    }


    private inner class ProjectCellRendered : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            // Only show last part of project path.
            val name = if ((value as? String)?.isNotBlank() == true) File(value.toString()).name else ""
            return super.getTableCellRendererComponent(table, name, isSelected, hasFocus, row, column).apply {
                val repo = model.getObjectAt(table.convertRowIndexToModel(row))
                if (repo.proj.isBlank() && repo.rootDir.isDirectory) {
                    border = BorderFactory.createLineBorder(Color.RED)
                    toolTipText = "Double-click to select a project"
                } else {
                    toolTipText = value as? String ?: ""
                }
            }
        }
    }
}

