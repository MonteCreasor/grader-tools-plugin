package edu.vanderbilt.grader.tools.utils

import java.awt.Component
import java.awt.event.ItemListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumnModel


class CheckBoxHeader(itemListener: ItemListener?) : JCheckBox(), TableCellRenderer, MouseListener {
    private var rendererComponent: CheckBoxHeader = this
    var column = 0
        private set
    private var mousePressed = false

    override fun getTableCellRendererComponent(
        table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        table?.tableHeader?.apply {
            rendererComponent.foreground = foreground
            rendererComponent.background = background
            rendererComponent.font = font
            addMouseListener(rendererComponent)
        }
        this.column = column
        if (column == 0) {
            rendererComponent.text = ""
            rendererComponent.horizontalAlignment = JLabel.CENTER
        }
        border = UIManager.getBorder("TableHeader.cellBorder")

        return rendererComponent
    }

    private fun handleClickEvent(e: MouseEvent) {
        if (mousePressed) {
            mousePressed = false
            val header = e.source as JTableHeader
            val tableView: JTable = header.table
            val columnModel: TableColumnModel = tableView.columnModel
            val viewColumn = columnModel.getColumnIndexAtX(e.x)
            val column = tableView.convertColumnIndexToModel(viewColumn)
            if (viewColumn == this.column && e.clickCount == 1 && column != -1) {
                doClick()
            }
        }
    }

    override fun mouseClicked(e: MouseEvent) {
        handleClickEvent(e)
        (e.source as JTableHeader).repaint()
    }

    override fun mousePressed(e: MouseEvent) {
        mousePressed = true
    }

    override fun mouseReleased(e: MouseEvent) {}
    override fun mouseEntered(e: MouseEvent) {}
    override fun mouseExited(e: MouseEvent) {}

    init {
        rendererComponent.addItemListener(itemListener)
    }
}