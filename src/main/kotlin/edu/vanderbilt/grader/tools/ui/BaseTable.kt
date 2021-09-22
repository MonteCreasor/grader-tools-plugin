package edu.vanderbilt.grader.tools.ui

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInEditorManager
import com.intellij.ide.SelectInManager.PROJECT
import com.intellij.ide.SelectInManager.findSelectInTarget
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import edu.vanderbilt.grader.tools.utils.modelRowAndColumn
import edu.vanderbilt.grader.tools.utils.*
import edu.vanderbilt.grader.tools.utils.ResizeProperty.GROW
import edu.vanderbilt.grader.tools.utils.ResizeProperty.SHRINK
import java.awt.Cursor.*
import java.awt.event.*
import java.io.File
import kotlin.math.max

open class BaseTable<T : Any>(val project: Project, model: CheckBoxTableModel<T>) : JBTable(model) {
    var lastMouseEvent: MouseEvent? = null

    @Suppress("UNCHECKED_CAST")
    val myModel: CheckBoxTableModel<T>
        get() = model as CheckBoxTableModel<T>

    private var linkRow: Int? = null
    private var linkColumn: Int? = null
    private var sizeToFit = false
    private var checkBoxPreferredWidth: Int = 20

    private val resizeCallback = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            optimizeColumnWidths(resizeType = GROW and SHRINK)
        }
    }

    /**
     * Trick to support cells that only enter edit mode when double-clicked.
     * This is a nicer UI for this table where clicking is primarily used to
     * select rows and not to edit cells (used in RepoView).
     */
    var clickCount = 0

    init {
        buildTable()
    }

    private fun buildTable() {
        setupListeners()
        setPreferredCheckBoxWidth()
        setDefaults(true)
    }

    private fun setPreferredCheckBoxWidth() {
        val column = convertColumnIndexToView(CHECK_BOX_COLUMN)
        val margin = 5
        val cellRenderer = getCellRenderer(column)
        val rendererComponent = cellRenderer.getTableCellRendererComponent(
            this@BaseTable,
            true,
            false,
            false,
            0,
            column
        )
        val headerWidth = getHeaderWidth(convertColumnIndexToView(CHECK_BOX_COLUMN)) + margin
        val preferredWidth = rendererComponent.preferredSize.getWidth().toInt() + margin
        val minWidth = rendererComponent.minimumSize.getWidth().toInt() + margin
        preferredWidth.coerceAtLeast(headerWidth)
        columnModel.getColumn(CHECK_BOX_COLUMN).minWidth = minWidth.coerceAtLeast(headerWidth)
        columnModel.getColumn(CHECK_BOX_COLUMN).maxWidth = preferredWidth.coerceAtLeast(minWidth)
        columnModel.getColumn(CHECK_BOX_COLUMN).width = preferredWidth.coerceAtLeast(minWidth)
    }

    /**
     * Kludge to optimize column widths to show as much of each column
     * as possible without allowing columns that show everything already
     * to steal any new real estate allocated by default layout strategy.
     */
    override fun doLayout() {
        if (tableHeader?.resizingColumn == null) {
            // This is a table resize operation (not a column resize)
            // so optimizing rows is not too obtrusive.
            optimizeColumnWidths(resizeType = GROW and SHRINK)
        }

        // Do default handling first
        super.doLayout()
    }

    private fun setupListeners() {
        addPropertyChangeListener { prop ->
            if ("tableCellEditor" == prop.propertyName && editingRow != -1) {
                val modelColumn = convertColumnIndexToModel(editingColumn)
                when (modelColumn) {
                    CHECK_BOX_COLUMN -> {
                        if (isRowSelected(editingRow)) {
                            val modelRow = convertRowIndexToModel(editingRow)
                            updateAllSelectedRows(myModel.isChecked(modelRow))
                        }
                    }
                    else -> Unit
                }
            }
        }

        addKeyListener(
            object : KeyListener {
                override fun keyTyped(e: KeyEvent) {
                }

                override fun keyPressed(e: KeyEvent) {
                    if (e.keyChar == ' ') {
                        val row = getSelectionModel().leadSelectionIndex
                        val column = getColumnModel().selectionModel.leadSelectionIndex
                        if (row != -1 && column != -1) {
                            val toggle = !myModel.isChecked(convertRowIndexToModel(row))
                            updateAllSelectedRows(toggle)
                            e.consume()
                        }
                    }
                }

                override fun keyReleased(e: KeyEvent?) {
                }
            }
        )

        addMouseListener(
            object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val (modelRow, modelColumn) = modelRowAndColumn(e)
                    if (myModel.getHyperLink(modelRow, modelColumn) != null) {
                        UIUtil.setCursor(this@BaseTable, getPredefinedCursor(HAND_CURSOR))
                        linkRow = modelRow
                        linkColumn = modelColumn
                        lastMouseEvent = e
                        myModel.fireTableCellUpdated(modelRow, modelColumn)
                    } else {
                        if (linkRow != null) {
                            val oldRow = linkRow!!
                            val oldColumn = linkColumn!!
                            linkRow = null
                            linkColumn = null
                            lastMouseEvent = null
                            myModel.fireTableCellUpdated(oldRow, oldColumn)
                            UIUtil.setCursor(this@BaseTable, getPredefinedCursor(DEFAULT_CURSOR))
                        }
                    }

                    super.mouseMoved(e)
                }

                override fun mouseClicked(e: MouseEvent) {
                    val (modelRow, modelColumn) = modelRowAndColumn(e)
                    when {
                        modelRow == -1 || modelColumn == -1 -> {
                            // Always clear selections when clicking outside the table.
                            clearSelection()
                            setColumnSelectionInterval(1, 1)
                        }
                        e.clickCount == 2 -> {
                            navigateTo(modelRow, modelColumn)
                            return
                        }
                    }
                    super.mouseClicked(e)
                }
            })
    }

    /**
     * Trick to support cells that only enter edit mode when double-clicked.
     * This is a nicer UI for this table where clicking is primarily used to
     * select rows and not to edit cells (used in RepoView).
     */
    override fun processMouseEvent(e: MouseEvent) {
        clickCount = e.clickCount
        super.processMouseEvent(e)
    }

//    override fun onTableChanged(e: TableModelEvent) {
//        super.onTableChanged(e)
//
//        ApplicationManager.getApplication().invokeLater {
//            validateIndexes {
//                super.onTableChanged(e)
//                if (sizeToFit) {
//                    resizeColumnWidths()
//                }
//            }
//        }
//    }

    override fun changeSelection(rowIndex: Int, columnIndex: Int, toggle: Boolean, extend: Boolean) {
        if (CHECKBOX_SELECTS_ROW) {
            val modelColumn = convertColumnIndexToModel(columnIndex)
            if (model.getColumnClass(modelColumn) != Boolean::class.javaObjectType) {
                super.changeSelection(rowIndex, columnIndex, toggle, extend)
            }
        }
    }

    fun navigateTo(modelRow: Int, modelColumn: Int) {
        myModel.getHyperLink(modelRow, modelColumn)?.let { navigateTo(it) }
    }

    private fun updateAllSelectedRows(checked: Boolean) {
        val list = selectedRows.map {
            convertRowIndexToModel(it)
        }.filter {
            myModel.isCellEditable(it, CHECK_BOX_COLUMN)
        }.filterNot {
            myModel.isChecked(it) == checked
        }
        if (list.isNotEmpty()) {
            myModel.setChecked(checked, list)
        }
    }

    fun setDefaults(fit: Boolean = true) {
        sizeToFit = fit
        if (sizeToFit) {
            //autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        }

        fillsViewportHeight = true
        rowHeight = 24

        adjustRowHeight()
        autoCreateColumnsFromModel = false
    }

    fun sizeColumnsToFit() {
        optimizeColumnWidths()
    }

    private fun adjustRowHeight() {
        try {
            for (row in 0 until rowCount) {
                var rowHeight = rowHeight
                for (column in 0 until columnCount) {
                    val comp = prepareRenderer(getCellRenderer(row, column), row, column)
                    rowHeight = max(rowHeight, comp.preferredSize.height)
                }
                setRowHeight(row, rowHeight)
            }
        } catch (e: ClassCastException) {
        }
    }

    open class CheckBoxTableModel<T : Any>(clazz: Class<T>, val column: Int) : ReflectionTableModel<T>(clazz) {
        private var pauseCellUpdates = false

        override fun fireTableCellUpdated(row: Int, column: Int) {
            if (!pauseCellUpdates) {
                super.fireTableCellUpdated(row, column)
            }
        }

        override fun fireTableRowsInserted(firstRow: Int, lastRow: Int) {
            if (!pauseCellUpdates) {
                super.fireTableRowsInserted(firstRow, lastRow)
            }
        }

        val checkedRows: List<Int>
            get() {
                val list = mutableListOf<Int>()
                for (i in 0 until rowCount) {
                    if (isChecked(i)) {
                        list.add(i)
                    }
                }
                return list
            }

        fun removeCheckedRows(): Int {
            val list = checkedRows.reversed()
            checkedRows.reversed().forEach { removeRow(it) }
            return list.size
        }

        fun setChecked(check: Boolean, row: Int) {
            val wasSelected = getValueAt(row, CHECK_BOX_COLUMN) as Boolean
            if (wasSelected != check) {
                setValueAt(check, row, CHECK_BOX_COLUMN)
                fireTableCellUpdated(row, CHECK_BOX_COLUMN)
            }
        }

        fun setChecked(check: Boolean, rows: List<Int>) {
            pauseCellUpdates = true
            try {
                rows.forEach { setChecked(check, it) }
            } finally {
                pauseCellUpdates = false
                if (rowCount > 0) {
                    fireTableRowsUpdated(0, rowCount - 1)
                }
            }
        }

        fun isChecked(row: Int) = getValueAt(row, CHECK_BOX_COLUMN) as Boolean

        open fun getHyperLink(row: Int, column: Int): String? = null
    }

    fun navigateTo(link: String) {
        if (link.startsWith("https://")) {
            WebUtils.openWebPage(link)
        } else {
            VirtualFileWrapper(File(link)).virtualFile?.let { file ->
                val context: SelectInContext = FileSelectInContext(project, file, null)
                //TODO: why is this call made when selectInEditor (below) is also called? Bug??
                findSelectInTarget(PROJECT, project)?.selectIn(context, true)
                if (file.asFile.isFile) {
                    //TODO: why is this call made when selectIn (above) is also called? Bug??
                    SelectInEditorManager.getInstance(project).selectInEditor(file, 0, 0, false, false)
                }
            }
        }
    }

    fun canNavigateTo(link: String?): Boolean =
        when {
            link.isNullOrBlank() -> false
            link.startsWith("https://") -> true
            else -> VirtualFileWrapper(File(link)).virtualFile?.asFile?.isFile ?: false
        }

    companion object {
        const val HEADER_HEIGHT = 24
        const val ROW_HEIGHT = 24 // Defined in form
        const val DEFAULT = "default"
        const val CHECK_BOX_COLUMN = 0
        const val STATUS_COLUMN = 5
        const val CHECKBOX_SELECTS_ROW = true
    }
}
