package edu.vanderbilt.grader.tools.ui

import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.border.Border

class IconComponentHelper(private val component: JComponent) {
    var border: Border
        private set
    private var icon: Icon? = null
    private var origBorder: Border
    private var spacing: Int = ICON_SPACING

    fun onPaintComponent(g: Graphics?) {
        icon?.run {
            val iconInsets = origBorder.getBorderInsets(component)
            paintIcon(component, g, iconInsets.left, iconInsets.top)
        }
    }

    fun onSetBorder(border: Border) {
        origBorder = border
        this.border = icon?.let {
            val margin = BorderFactory.createEmptyBorder(0, it.iconWidth + spacing, 0, 0)
            BorderFactory.createCompoundBorder(border, margin)
        } ?: border
    }

    fun onSetIcon(icon: Icon?) {
        this.icon = icon
        resetBorder()
    }

    private fun resetBorder() {
        component.border = origBorder
    }

    fun onSetIconSpacing(spacing: Int) {
        this.spacing = spacing
    }

    companion object {
        private const val ICON_SPACING = 4
    }

    init {
        origBorder = component.border
        border = origBorder
    }
}