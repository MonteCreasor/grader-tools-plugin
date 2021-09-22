package edu.vanderbilt.grader.tools.utils

import com.jetbrains.rd.util.EnumSet
import edu.vanderbilt.grader.tools.utils.ResizeProperty.GROW
import edu.vanderbilt.grader.tools.utils.ResizeProperty.SHRINK
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import kotlin.math.max

enum class ResizeProperty {
    GROW,
    SHRINK;

    infix fun and(other: ResizeProperty): ResizeProperties = ResizeProperties.of(this, other)
}

typealias ResizeProperties = EnumSet<ResizeProperty>

infix fun ResizeProperties.allOf(other: ResizeProperties): Boolean = this.containsAll(other)
infix fun ResizeProperties.and(other: ResizeProperty): ResizeProperties = ResizeProperties.of(other, *toTypedArray())

fun JTable.optimizeColumnWidths(columnMargin: Int = 5, resizeType: ResizeProperties = GROW and SHRINK) {
    resizeColumns(columnMargin, resizeType)
}

@Suppress("UNUSED_PARAMETER")
fun JTable.resizeColumns(columnMargin: Int = 5, resizeType: ResizeProperties = GROW and SHRINK) {
    val minWidths = IntArray(columnCount)
    val maxWidths = IntArray(columnCount)
    for (columnIndex in 0 until columnCount) {
        val headerWidth = getHeaderWidth(columnIndex)
        minWidths[columnIndex] = headerWidth + columnMargin
        val maxWidth = getMaximumPreferredColumnWidth(columnIndex, headerWidth)
        maxWidths[columnIndex] = max(maxWidth, minWidths[columnIndex]) + columnMargin
    }

    adjustMaximumWidths(minWidths, maxWidths)

    for (i in minWidths.indices) {
        with(columnModel.getColumn(i)) {
            if (minWidths[i] > 0) { // && resizeType.contains(SHRINK)) {
                if (minWidth != minWidths[i]) {
                    minWidth = minWidths[i]
                }
            }
            if (maxWidths[i] > 0) { // && resizeType.contains(GROW)) {
//                if (maxWidth != maxWidths[i]) {
//                    maxWidth = maxWidths[i]
//                }

                if (preferredWidth != maxWidths[i]) {
                    preferredWidth = maxWidths[i]
                }
            }
        }
    }
}

private fun JTable.adjustMaximumWidths(minWidths: IntArray, maxWidths: IntArray) {
    if (width > 0) {
        // to prevent infinite loops in exceptional situations
        var breaker = 0

        // keep stealing one pixel of the maximum width of the highest
        // column until we can fit in the width of the table
        if (sum(maxWidths) > width) {
            while (sum(maxWidths) > width && breaker < 10000) {
                val highestWidthIndex = findLargestIndex(maxWidths)
                maxWidths[highestWidthIndex] -= 1
                maxWidths[highestWidthIndex] =
                    maxWidths[highestWidthIndex].coerceAtLeast(minWidths[highestWidthIndex])
                breaker++
            }
        } else {
            val slack = width - sum(maxWidths)
            maxWidths[maxWidths.lastIndex] += slack
        }
    }
}

fun JTable.getMaximumPreferredColumnWidth(column: Int, headerWidth: Int): Int {
    val cellRenderer = getCellRenderer(column)
    var maxWidth = headerWidth

    for (row in 0 until rowCount) {
        val valueWidth = getPreferredWidth(row, column, cellRenderer)
        maxWidth = maxWidth.toDouble().coerceAtLeast(valueWidth).toInt()
    }

    return maxWidth
}

fun JTable.getCellRenderer(columnIndex: Int): TableCellRenderer {
    val column = columnModel.getColumn(columnIndex)
    return if (column.cellRenderer != null) {
        column.cellRenderer
    } else {
        val modelColumn = convertColumnIndexToModel(columnIndex)
        val columnClass = model.getColumnClass(modelColumn)
        if (columnClass != null) {
            getDefaultRenderer(columnClass)
        } else {
            DefaultTableCellRenderer()
        } ?: DefaultTableCellRenderer()
    }
}

fun JTable.getHeaderWidthOld(column: Int) =
    tableHeader
        ?.getFontMetrics(tableHeader.font)
        ?.stringWidth(getColumnName(column))
        ?: 0

fun JTable.getHeaderWidth(column: Int): Int {
    val renderer = tableHeader.columnModel.getColumn(column).headerRenderer ?: tableHeader.defaultRenderer
    val dimension = renderer
        .getTableCellRendererComponent(this, getColumnName(column), true, false, 0, column)
        .preferredSize
    return dimension.width
}

fun JTable.getPreferredWidth(row: Int, column: Int, cellRenderer: TableCellRenderer): Double {
    val modelRow = convertRowIndexToModel(row)
    val modelColumn = convertColumnIndexToModel(column)
    val rendererComponent = cellRenderer.getTableCellRendererComponent(
        this,
        model.getValueAt(modelRow, modelColumn),
        false,
        false,
        row,
        column
    )
    return rendererComponent.preferredSize.getWidth()
}

private fun findLargestIndex(widths: IntArray): Int {
    var largestIndex = 0
    var largestValue = 0
    for (i in widths.indices) {
        if (widths[i] > largestValue) {
            largestIndex = i
            largestValue = widths[i]
        }
    }
    return largestIndex
}

private fun sum(widths: IntArray): Int {
    var sum = 0
    for (width in widths) {
        sum += width
    }
    return sum
}
