package edu.vanderbilt.grader.tools.utils

import com.intellij.ui.SearchTextField
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.TableRowSorter

fun JTable.createFilterPanel(historyPropertyName: String? = null): Pair<JPanel, SearchTextField> {
    val panel = JPanel(BorderLayout())
    val label = JLabel("Filter: ")
    val textField = createSearchTextField(historyPropertyName)
    panel.add(label, BorderLayout.WEST)
    panel.add(textField, BorderLayout.EAST)

    return Pair(panel, textField)
}

fun JTable.createSearchTextField(historyPropertyName: String?): SearchTextField {
    if (rowSorter == null) {
        autoCreateRowSorter = true
    }

    val textField = if (historyPropertyName.isNullOrBlank()) {
        SearchTextField(true)
    } else {
        SearchTextField(true, historyPropertyName)
    }

    textField.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) {
            update()
        }

        override fun removeUpdate(e: DocumentEvent) {
            update()
        }

        override fun changedUpdate(e: DocumentEvent) {
            update()
        }

        private fun update() {
            val text = textField.text
            with(rowSorter as TableRowSorter<*>) {
                if (text.isNullOrBlank()) {
                    setRowFilter(null)
                } else {
                    setRowFilter(RowFilter.regexFilter("(?i)$text"))
                }
            }
        }
    })

    return textField
}

//class HighlightRenderer(private val searchField: SearchTextField) : DefaultTableCellRenderer() {
//    override fun getTableCellRendererComponent(
//        table: JTable,
//        value: Any,
//        selected: Boolean,
//        hasFocus: Boolean,
//        row: Int,
//        column: Int
//    ): Component {
//        val c = super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column)
//
//        val original: JLabel = c as JLabel
//        val label = LabelHighlighted()
//        label.font = original.font
//        label.text = text
//        label.background = original.background
//        label.foreground = original.foreground
//        label.horizontalTextPosition = original.horizontalTextPosition
//        label.highlightText(searchField.text)
//        return label
//    }
//}
//
//class LabelHighlighted : DefaultTableCellRenderer() {
//    private val rectangles = mutableListOf<Rectangle2D>()
//    private val colorHighlight: Color = Color.RED
//
//    private fun reset() {
//        rectangles.clear()
//        repaint()
//    }
//
//    fun highlightText(string: String?) {
//        val textToHighlight = string?.trim() ?: return
//        reset()
//
//        if (textToHighlight.isEmpty()) {
//            return
//        }
//
//        if (text.contains(textToHighlight, ignoreCase = true)) {
//            val fontMetrics = getFontMetrics(font)
//            var width = -1f
//            val height = fontMetrics.height - 1.toFloat()
//            var i = 0
//
//            while (true) {
//                i = text.indexOf(textToHighlight, i, ignoreCase = true)
//                if (i == -1) {
//                    break
//                }
//                if (width == -1f) {
//                    val matchingText = text.substring(i, i + textToHighlight.length)
//                    width = fontMetrics.stringWidth(matchingText).toFloat()
//                }
//                val preText = text.substring(0, i)
//                val x = fontMetrics.stringWidth(preText).toFloat() + 2f
//                rectangles.add(Rectangle2D.Float(x, 1f, width, height))
//                i += textToHighlight.length
//            }
//            repaint()
//        }
//    }
//
//    override fun paint(g: Graphics?) {
//        super.paint(g)
//        if (rectangles.isNotEmpty()) {
//            paintHighlights(g as Graphics2D)
//        }
//    }
//
//    private fun paintHighlights(graphics: Graphics2D) {
//        if (rectangles.size > 0) {
//            val savedColor = graphics.color
//            graphics.color = colorHighlight
//            for (rectangle in rectangles) {
//                //graphics.fill(rectangle)
//                //graphics.color = Color.LIGHT_GRAY
//                graphics.draw(rectangle)
//            }
//            graphics.color = savedColor
//        }
//    }
//}