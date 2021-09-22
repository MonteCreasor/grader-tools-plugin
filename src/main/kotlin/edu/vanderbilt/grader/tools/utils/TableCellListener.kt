package edu.vanderbilt.grader.tools.utils

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JTable
import javax.swing.SwingUtilities

/**
 * USAGE:
 *
 *      val action = object: AbstractAction() {
 *          override fun actionPerformed(e: ActionEvent) {
 *              val tcl = e.source as TableCellListener
 *              println("Row   : ${tcl.row}")
 *              println("Column: ${tcl.column}")
 *              println("Old   : ${tcl.oldValue}")
 *              println("New   : ${tcl.newValue}")
 *          }
 *      }
 *
 *      tcl = TableCellListener(table, action)
 */
class TableCellListener(
    val table: JTable,
    private val callback: (listener: TableCellListener) -> Unit
) : PropertyChangeListener {
    var row = -1
        private set
    var column = -1
        private set
    var oldValue: Any? = null
        private set
    var newValue: Any? = null
        private set

    init {
        this.table.addPropertyChangeListener(this)
    }

    private constructor(table: JTable, row: Int, column: Int, oldValue: Any?, newValue: Any?) : this(table, {}) {
        this.row = row
        this.column = column
        this.oldValue = oldValue
        this.newValue = newValue
    }

    override fun propertyChange(e: PropertyChangeEvent) { //  A cell has started/stopped editing
        if ("tableCellEditor" == e.propertyName) {
            if (table.isEditing) {
                //  editingRow an editingColumn after returning from this
                //  PropertyChangeEvent post a runnable to process the event.
                SwingUtilities.invokeLater {
                    if (table.editingRow != -1) {
                        row = table.convertRowIndexToModel(table.editingRow)
                        column = table.convertColumnIndexToModel(table.editingColumn)
                        oldValue = table.model.getValueAt(row, column)
                        newValue = null
                    }
                }
            } else {
                newValue = table.model.getValueAt(row, column)
                if (newValue != oldValue) {
                    callback.invoke(TableCellListener(table, row, column, oldValue, newValue))
                }
            }
        }
    }
}
