package app.droidle.plugin.ui.components

import com.intellij.ui.components.JBLabel
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.SwingConstants
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View

/**
 * A JBLabel for HTML content that dynamically computes its preferred height based on the
 * available horizontal width from its parent hierarchy, avoiding single-line text truncation
 * or clipping in Swing BoxLayouts and viewports.
 */
open class AutoWrappingHtmlLabel(text: String = "") : JBLabel(text) {

    init {
        alignmentX = Component.LEFT_ALIGNMENT
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.TOP
    }

    override fun getPreferredSize(): Dimension {
        val base = super.getPreferredSize()
        val availW = resolveAvailableWidth()
        val view = getClientProperty(BasicHTML.propertyKey) as? View
        if (view != null && availW > 0) {
            view.setSize(availW.toFloat(), 0f)
            val h = Math.ceil(view.getPreferredSpan(View.Y_AXIS).toDouble()).toInt() + insets.top + insets.bottom
            return Dimension(availW, h)
        }
        return base
    }

    override fun getMaximumSize(): Dimension {
        return Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun resolveAvailableWidth(): Int {
        val ownW = width - insets.left - insets.right
        if (ownW > 20) return ownW

        var p: Container? = parent
        while (p != null) {
            val pw = p.width - p.insets.left - p.insets.right
            if (pw > 20) {
                return (pw - insets.left - insets.right).coerceAtLeast(20)
            }
            p = p.parent
        }
        return 0
    }
}
