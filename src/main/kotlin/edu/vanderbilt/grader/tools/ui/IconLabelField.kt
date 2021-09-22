package edu.vanderbilt.grader.tools.ui

import com.intellij.ui.components.JBLabel
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JTextField
import javax.swing.border.Border

class IconLabelField(label: String = "") : JBLabel(label) {
    private var helper: IconComponentHelper = IconComponentHelper(this)

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        helper.onPaintComponent(graphics)
    }

//    fun setIcon(icon: Icon?) {
//        helper.onSetIcon(icon)
//    }

    fun setIconSpacing(spacing: Int) {
        helper.onSetIconSpacing(spacing)
    }

    override fun setBorder(border: Border) {
        helper.onSetBorder(border)
        super.setBorder(helper.border)
    }
}