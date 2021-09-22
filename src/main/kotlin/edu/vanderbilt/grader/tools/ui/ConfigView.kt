package edu.vanderbilt.grader.tools.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.PathUtil
import com.intellij.util.ui.LocalPathCellEditor
import com.intellij.util.ui.UIUtil
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.persist.Config.Companion.debug
import edu.vanderbilt.grader.tools.utils.*
import edu.vanderbilt.grader.tools.persist.Config.Key
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.io.File
import java.lang.Boolean.TRUE
import java.util.*
import javax.swing.*
import javax.swing.BorderFactory.createLineBorder
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor

class ConfigView(project: Project) : ProjectContext(project) {
    val component: JComponent
        get() = panel

    enum class Column {
        Key,
        Description,
        Value;

        val col: Int
            get() = ordinal
    }

    internal var table: JBTable
    private var panel: JPanel = JPanel(BorderLayout())
    private val model: TableModel
        get() = table.model as TableModel

    private var linkRow: Int? = null
    private var linkColumn: Int? = null

    val linkLabel = LinkLabel<String>("TODO", null)

    companion object {
        var focusKey: Key? = null
        // Just use an empty string to prevent error message string
        // from being used as a path name.
        const val INVALID_PATH = ""
    }

    init {
        table = buildTable()
        panel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
        panel.add(buildToolbar(), BorderLayout.EAST)

        setupListeners()
        table.autoCreateRowSorter = false

        // Doesn't stop editing when toolbar buttons clicked
        // so added stopEditing() calls to all actions.
        table.putClientProperty("terminateEditOnFocusLost", TRUE)
    }

    private fun buildTable(): JBTable {
        val model = TableModel()
        val table = object : JBTable(model) {
            override fun doLayout() {
                if (tableHeader?.resizingColumn == null) {
                    // This is a table resize operation (not a column resize)
                    // so optimizing rows is not too obtrusive.
                    optimizeColumnWidths()
                }

                // Do default handling first
                super.doLayout()
            }
        }

        table.cellSelectionEnabled = false
        table.setDefaultRenderer(String::class.javaObjectType, ConfigCellRenderer())
        table.columnModel.getColumn(Column.Value.ordinal).cellEditor = MyTableCellEditor()

        PathUtil.toSystemDependentName("")
        return table
    }

    private fun JLabel.underline(on: Boolean = true) {
        val font = font
        val attributes = HashMap<TextAttribute, Any>(font.attributes)
        if (on) {
            attributes[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
        } else {
            if (font.attributes[TextAttribute.UNDERLINE] != null) {
                font.attributes.remove(TextAttribute.UNDERLINE)
            }
        }
        setFont(font.deriveFont(attributes))
    }

    private fun buildToolbar(): JComponent {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(ResetAction())
        actionGroup.add(LoadAction(this))
        actionGroup.add(SaveAction(this))
        actionGroup.add(DebugAction())
        return createToolbar(actionGroup)
    }

    private fun setupListeners() {
        Config.ConfigurationChange.subscribe(project) { _, _, _ ->
            refreshData()
        }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val (row, column) = table.modelRowAndColumn(e)
                if (column == Column.Description.col && (row != -1 && Key.values()[row] == Key.GitToken)) {
                    val url = WebUtils.generateTokenUrl(config[Key.GitSite])
                    url?.let { WebUtils.openWebPage(url) }
                }
                super.mouseClicked(e)
            }

            override fun mouseMoved(e: MouseEvent) {
                val (row, column) = table.modelRowAndColumn(e)
                if (column == Column.Description.col && (row != -1 && Key.values()[row] == Key.GitToken)) {
                    linkLabel.entered(e)
                    UIUtil.setCursor(table, getPredefinedCursor(HAND_CURSOR))
                    linkRow = row
                    linkColumn = column
                    model.fireTableCellUpdated(row, column)
                } else if (linkRow != null) {
                    val oldRow = linkRow!!
                    val oldColumn = linkColumn!!
                    linkRow = null
                    linkColumn = null
                    linkLabel.exited(e)
                    model.fireTableCellUpdated(oldRow, oldColumn)
                    UIUtil.setCursor(table, getPredefinedCursor(DEFAULT_CURSOR))
                }

                super.mouseMoved(e)
            }
        })
    }

    fun loadFromFile(virtualFile: VirtualFile? = null): Boolean {
        val file = virtualFile?.asFile ?: chooseFile()?.asFile ?: return false
        config.loadConfigFromFile(project, file)
        return true
    }

    fun saveToFile(virtualFile: VirtualFile? = null): Boolean {
        val file = virtualFile?.asFile ?: chooseSaveFile(project) ?: return false
        config.saveConfigToFile(file)
        return true
    }

    fun refreshData() {
        (table.model as TableModel).fireTableDataChanged()
    }

    /**
     * TODO: Doesn't work when called when dialog is first created!
     */
    fun editKey(key: Key) {
        focusKey = key
        ApplicationManager.getApplication().invokeLater {
            table.requestFocus()
            startEditKeyValue(key)
        }
    }

    private fun chooseFile(): VirtualFile? {
        val descriptor = createSingleFileDescriptor(PlainTextFileType.INSTANCE).apply {
            isHideIgnored = true
            isForcedToUseIdeaFileChooser = true
        }
        val file = config.configFilePath?.virtualFile ?: project.dir
        return FileChooser.chooseFile(descriptor, null, file)
    }

    private fun chooseSaveFile(): File? {
        val descriptor = FileSaverDescriptor("Save Configuration", "").apply {
            isHideIgnored = true
            isForcedToUseIdeaFileChooser = true
        }
        FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project).also { dialog ->
            val file = config.configFilePath?.let { File(it) }
                ?: File(project.dir.asFile, Config.DEFAULT_CONFIG_FILE_NAME)
            val dir = VirtualFileWrapper(file.parentFile).virtualFile
            return dialog.save(dir, file.name)?.file
        }
    }

    private fun createToolbar(actions: ActionGroup): JComponent {
        val actionToolbar: ActionToolbar =
            ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, false)
        return actionToolbar.component
    }

    private inner class ResetAction :
        AnAction("Reset", "Reset all properties to default values", AllIcons.Actions.Refresh),
        DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            stopEditing()
            config.resetToDefaults(project)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = config.isDefaultConfig
        }
    }

    private inner class DebugAction :
        AnAction("Debug", "Shows/hides all system configuration properties.", AllIcons.Actions.Expandall),
        DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            stopEditing()
            toggleShowAll()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = true
            e.presentation.icon = if (debug) {
                AllIcons.Actions.Collapseall
            } else {
                AllIcons.Actions.Expandall
            }
        }
    }

    private inner class LoadAction(val configTable: ConfigView) :
        AnAction("Load", "Load properties from a file", AllIcons.Actions.Menu_open),
        DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            stopEditing()
            try {
                if (configTable.loadFromFile()) {
                    info("Configuration loaded.")
                } else {
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

    private inner class SaveAction(val configTable: ConfigView) :
        AnAction("Save", "Save properties to a file", AllIcons.Actions.Menu_saveall),
        DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            stopEditing()
            try {
                if (configTable.saveToFile()) {
                    info("Configuration saved.")
                } else {
                    info("Operation cancelled.")
                }
            } catch (e: Exception) {
                error("Save exception: ${e.message}.")
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = true
        }
    }

    private fun stopEditing() {
        if (table.isEditing) {
            table.cellEditor.stopCellEditing()
        }
    }

    private inner class TableModel : AbstractTableModel() {
        override fun getColumnCount() = Column.values().count()
        override fun getColumnName(column: Int): String = Column.values()[column].name
        override fun getRowCount(): Int {
            return config.configMap.count {
                debug || it.key.visible
            }
        }

        override fun getColumnClass(column: Int): Class<*> = String::class.javaObjectType
        override fun isCellEditable(row: Int, column: Int): Boolean =
            column == Column.Value.col && !project.isRunning

        override fun getValueAt(row: Int, column: Int): Any? {
            if (row >= rowCount) {
                throw ArrayIndexOutOfBoundsException(
                    "ConfigView model getValue at [$row!, $column]"
                )
            }

            val key = config.configMap.keys.toList()[row]
            return when (column) {
                Column.Key.col -> key
                Column.Description.col -> key.description
                Column.Value.col -> config.configMap[key]
                else -> throw IndexOutOfBoundsException("Invalid configuration column $column")
            }
        }

        override fun setValueAt(value: Any, row: Int, column: Int) {
            if (row >= rowCount) {
                throw ArrayIndexOutOfBoundsException(
                    "ConfigView model getValue at [$row!, $column]"
                )
            }

            if (!isCellEditable(row, column)) {
                throw IllegalStateException("Attempt to update value in uneditable cell [$row, $column].")
            }

            val key = config.configMap.keys.toList()[row]
            when (column) {
                Column.Value.col -> config[key] = value as String
                else -> throw IndexOutOfBoundsException()
            }
            fireTableCellUpdated(row, column)
        }
    }

    private inner class ConfigCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val modelRow = table.convertRowIndexToModel(row)
            val modelColumn = table.convertColumnIndexToModel(column)
            val key = Key.values()[modelRow]
            return if (key == Key.GitToken && modelColumn == Column.Description.col) {
                val label = super.getTableCellRendererComponent(
                    table, text, isSelected, hasFocus, row, column
                ) as JLabel

                linkLabel.apply {
                    text = value.toString()
                    // Looks better without thin black cell border
                    background = label.background
                    border = label.border
                    font = label.font
                    insets.left = label.insets.left
                    verticalAlignment = label.verticalAlignment
                    horizontalAlignment = label.horizontalAlignment
                    verticalTextPosition = label.verticalTextPosition
                    horizontalTextPosition = label.horizontalTextPosition
                    size = label.size
                }
            } else {
                // Make sure that sensitive data is masked.
                val string = config.hidePasswords(value.toString())
                // Return default cell renderer with adjustments.
                super.getTableCellRendererComponent(table, string, isSelected, hasFocus, row, column).apply {
                    foreground = UIManager.getColor("Label.foreground")
                    background = UIManager.getColor("Label.background")
                    toolTipText = null
                    // Sets colors and tooltips for missing configuration values.
                    handleMissingValues(modelRow, modelColumn, value, key)
                }
            }
        }
    }

    private fun DefaultTableCellRenderer.handleMissingValues(
        row: Int, column: Int, value: Any?, key: Key
    ) {
        when (key) {
            Key.GitUser,
            Key.SdkDir,
            Key.JdkDir,
            Key.GitSite,
            Key.GitToken -> {
                when (column) {
                    Column.Key.col, Column.Description.col ->
                        if (table.model.getValueAt(row, Column.Value.col).toString().trim().isBlank()) {
                            toolTipText = "${key.name} value is required"
                        }
                    Column.Value.col ->
                        if (key == focusKey && value.toString().trim().isBlank()) {
                            toolTipText = "${key.name} value is required"
                            border = createLineBorder(Color.RED)
                        }
                }
            }
            else -> Unit
        }
    }

    internal inner class MyTableCellEditor : AbstractCellEditor(), TableCellEditor {
        private var component: Component? = null
        private lateinit var pathCellEditor: LocalPathCellEditor
        private lateinit var key: Key

        override fun getTableCellEditorComponent(
            table: JTable, value: Any?, isSelected: Boolean, rowIndex: Int, colIndex: Int
        ): Component {
            key = Key.values()[table.convertRowIndexToView(rowIndex)]
            return when (key) {
                Key.Run -> {
                    LocalPathCellEditor()
                        .fileChooserDescriptor(createSingleFileDescriptor())
                        .normalizePath(true)
                        .also { pathCellEditor = it }
                        .getTableCellEditorComponent(table, value, isSelected, rowIndex, colIndex)
                }
                Key.SdkDir,
                Key.JdkDir -> {
                    LocalPathCellEditor()
                        .fileChooserDescriptor(createSingleFolderDescriptor())
                        .normalizePath(true)
                        .also { pathCellEditor = it }
                        .getTableCellEditorComponent(table, value, isSelected, rowIndex, colIndex)
                }
                Key.InstallDir,
                Key.RepoRoot -> {
                    LocalPathCellEditor()
                        .fileChooserDescriptor(createSingleFolderDescriptor().apply {
                            withRoots(project.dir)
                            withShowFileSystemRoots(false)
                            withTreeRootVisible(true)
                            withShowHiddenFiles(false)
                        })
//                        .normalizePath(true)
                        .also { pathCellEditor = it }
                        .getTableCellEditorComponent(table, value, isSelected, rowIndex, colIndex)
                }
                else -> {
                    JTextField().apply {
                        text = value as String?
                    }
                }
            }.also {
                component = it
            }
        }

        override fun getCellEditorValue(): Any {
            if (component is JTextField) {
                return (component as JTextField).text
            }

            val value = pathCellEditor.cellEditorValue
            if (value == null || value.toString().isBlank()) {
                return ""
            }
            val file = File(value.toString())
            return when (key) {
                Key.InstallDir -> config.getInstallDir(project, value.toString())?.path ?: INVALID_PATH
                Key.RepoRoot -> config.getRepoRootDir(project, value.toString())?.path ?: INVALID_PATH
                else -> {
                    if (!file.isDirectory) {
                        error(
                            "${key.name} must be a path relative to the project root directory.",
                            config.fixAction(project, key)
                        )
                    }
                    value
                }
            }
        }
    }

    private fun startEditKeyValue(key: Key) {
        with(table) {
            clearSelection()
            setRowSelectionInterval(key.ordinal, key.ordinal)
            setColumnSelectionInterval(Column.Value.col, Column.Value.col)
            val row = table.convertRowIndexToView(key.ordinal)
            val column = table.convertColumnIndexToView(Column.Value.col)
            editCellAt(row, column)
            parent.repaint()
            editorComponent?.let { editor ->
                scrollRectToVisible(editor.bounds)
            }
        }
    }

    fun toggleShowAll() {
        debug = !debug
        refreshData()
    }
}
