package edu.vanderbilt.grader.tools.utils

import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.LineBorder
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumnModel

/**
 * The ButtonColumn class provides a renderer and an editor that looks like a
 * JButton. The renderer and editor will then be used for a specified column
 * in the table. The TableModel will contain the String to be displayed on
 * the button.
 *
 * The button can be invoked by a mouse click or by pressing the space bar
 * when the cell has focus. Optionally a mnemonic can be set to invoke the
 * button. When the button is invoked the provided Action is invoked. The
 * source of the Action will be the table. The action command will contain
 * the model row number of the button that was clicked.
 *
 */
class ButtonColumn(private val table: JTable, private val action: Action, column: Int) :
    AbstractCellEditor(), TableCellRenderer, TableCellEditor, ActionListener, MouseListener {
    /**
     * The mnemonic to activate the button when the cell has focus
     *
     * @param mnemonic the mnemonic
     */
    var mnemonic = 0
        set(mnemonic) {
            field = mnemonic
            renderButton.mnemonic = mnemonic
            editButton.mnemonic = mnemonic
        }
    private val originalBorder: Border?
    private var focusBorder: Border? = null
    private val renderButton: JButton
    private val editButton: JButton
    private var editorValue: Any? = null
    private var isButtonColumnEditor = false
    /**
     * Get foreground color of the button when the cell has focus
     *
     * @return the foreground color
     */
    fun getFocusBorder(): Border? {
        return focusBorder
    }

    /**
     * The foreground color of the button when the cell has focus
     *
     * @param focusBorder the foreground color
     */
    fun setFocusBorder(focusBorder: Border) {
        this.focusBorder = focusBorder
        editButton.border = focusBorder
    }

    override fun getTableCellEditorComponent(
        table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int
    ): Component {
        editButton.setContents(value)
        editorValue = value
        return editButton
    }

    override fun getCellEditorValue(): Any? {
        return editorValue
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        if (isSelected) {
            renderButton.foreground = table.foreground
            //renderButton.foreground = table.selectionForeground
            renderButton.background = table.selectionBackground
        } else {
            renderButton.foreground = table.foreground
            renderButton.background = UIManager.getColor("Button.background")
        }

        renderButton.setContents(value)
        renderButton.border = if (hasFocus) focusBorder else originalBorder

        return renderButton
    }

    private fun JButton.setContents(value: Any?) {
        when (value) {
            null -> {
                text = ""
                icon = null
            }
            is Icon -> {
                text = ""
                icon = value
            }
            else -> {
                text = value.toString()
                icon = null
            }
        }
    }

    /*
	 *	The button has been pressed. Stop editing and invoke the custom Action
	 */
    override fun actionPerformed(e: ActionEvent?) {
        val row = table.convertRowIndexToModel(table.editingRow)
        val event = ActionEvent(table, ActionEvent.ACTION_PERFORMED, "" + row)
        fireEditingStopped()
        action.actionPerformed(event)
    }

    /*
	 *  When the mouse is pressed the editor is invoked. If you then then drag
	 *  the mouse to another cell before releasing it, the editor is still
	 *  active. Make sure editing is stopped when the mouse is released.
	 */
    override fun mousePressed(e: MouseEvent) {
        if (table.isEditing && table.cellEditor === this) {
            isButtonColumnEditor = true
        }
    }

    override fun mouseReleased(e: MouseEvent) {
        if (isButtonColumnEditor && table.isEditing) {
            table.cellEditor.stopCellEditing()
        }
        isButtonColumnEditor = false
    }

    override fun mouseClicked(e: MouseEvent?) {}
    override fun mouseEntered(e: MouseEvent?) {}
    override fun mouseExited(e: MouseEvent?) {}

    /**
     * Create the ButtonColumn to be used as a renderer and editor. The
     * renderer and editor will automatically be installed on the TableColumn
     * of the specified column.
     *
     * @param table the table containing the button renderer/editor
     * @param action the Action to be invoked when the button is invoked
     * @param column the column to which the button renderer/editor is added
     */

    init {
        renderButton = JButton()
        renderButton.minimumSize = Dimension(40, 24)
        renderButton.preferredSize = renderButton.minimumSize
        editButton = JButton()
        editButton.minimumSize = Dimension(40, 24)
        editButton.preferredSize = editButton.minimumSize
        editButton.isFocusPainted = false
        editButton.addActionListener(this)
        originalBorder = editButton.border
        setFocusBorder(LineBorder(Color.BLUE))
        val columnModel: TableColumnModel = table.columnModel
        columnModel.getColumn(column).cellRenderer = this
        columnModel.getColumn(column).cellEditor = this
        table.addMouseListener(this)
    }
}