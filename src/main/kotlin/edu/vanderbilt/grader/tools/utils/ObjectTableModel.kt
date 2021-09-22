package edu.vanderbilt.grader.tools.utils

import edu.vanderbilt.grader.tools.utils.ThreadUtils.assertUiThread
import java.lang.IllegalArgumentException
import javax.swing.table.AbstractTableModel
import kotlin.properties.Delegates

abstract class ObjectTableModel<T> : AbstractTableModel() {
    open var rows: MutableList<T> by Delegates.observable(mutableListOf()) { _, old, new ->
        if (old != new) {
            assertUiThread()
            fireTableDataChanged()
        }
    }

    abstract fun getValueAt(row: T, column: Int): Any?
    abstract fun setValueAt(value: Any?, row: T, column: Int): Any

    override fun getRowCount(): Int {
        assertUiThread()
        return rows.size
    }

    override fun getValueAt(row: Int, column: Int): Any? {
        assertUiThread()
        return getValueAt(rows[row], column)
    }

    override fun setValueAt(value: Any?, row: Int, column: Int) {
        assertUiThread()
        setValueAt(value, rows[row], column)
        fireTableCellUpdated(row, column)
    }

    fun getObjectAt(row: Int): T {
        assertUiThread()
        return rows[row]
    }

    fun removeRow(row: Int) {
        assertUiThread()
        rows.removeAt(row)
        fireTableRowsDeleted(row, row)
    }

    fun removeObject(obj: T) {
        assertUiThread()
        removeRow(getRowFor(obj))
    }

    fun getRowFor(obj: T, mustExist: Boolean = true): Int {
        val index = rows.indexOf(obj)
        if (mustExist && index !in 0 until rowCount) {
            throw IllegalArgumentException("getRowFor() - $obj")
        }
        return index
    }
}