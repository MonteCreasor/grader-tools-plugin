package edu.vanderbilt.grader.tools.utils

import com.intellij.ui.TableSpeedSearch
import java.awt.Component
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

fun JTable.disableReordering() {
    tableHeader.reorderingAllowed = false
}

fun JTable.modelRowAndColumn(row: Int, column: Int): Pair<Int, Int> {
    try {
        val modelRow = if (row != -1) convertRowIndexToModel(row) else -1
        val modelColumn = if (column != -1) convertColumnIndexToModel(column) else -1
        return Pair(modelRow, modelColumn)
    } catch (e: Exception) {
        println("modelRowAndColumn exception: $e")
        throw IllegalStateException("modelRowAndColumn exception", e)
    }
}

fun JTable.modelRowAndColumn(e: MouseEvent): Pair<Int, Int> =
    modelRowAndColumn(rowAtPoint(e.point), columnAtPoint(e.point))

open class DateCellRenderer(val format: SimpleDateFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")) :
    DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val newValue = if (value is Date) format.format(value) else value
        return super.getTableCellRendererComponent(table, newValue, isSelected, hasFocus, row, column)
    }
}

//var speedSearchText: String? = null
//
//fun JTable.setupSpeedSearch() {
//    if (rowSorter == null) {
//        autoCreateRowSorter = true
//    }
//
//    val speedSearch = TableSpeedSearch(this)
//    speedSearch.comparator = SpeedSearchComparator(false, false)
//    speedSearch.addChangeListener {
//        speedSearchText = (it.newValue as? String)
//        with(rowSorter as TableRowSorter<*>) {
//            if (speedSearchText.isNullOrBlank()) {
//                setRowFilter(null)
//            } else {
//                setRowFilter(regexFilter("(?i)$speedSearchText"))
//            }
//        }
//    }
//}
