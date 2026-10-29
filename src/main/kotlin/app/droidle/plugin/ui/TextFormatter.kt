package app.droidle.plugin.ui

import com.intellij.ui.JBColor

/**
 * Utility for formatting questions, options, and explanations into modern, cleanly styled
 * Swing HTML with syntax-highlighted inline code chips (`...`), proper entity escaping,
 * and left-aligned layout.
 */
object TextFormatter {

    fun formatInlineCode(raw: String): String {
        if (raw.isBlank()) return ""
        val isDark = !JBColor.isBright()
        // Modern syntax highlighting colors for inline code tokens (sky blue on dark/light zinc chip)
        val codeFg = if (isDark) "#38BDF8" else "#0369A1"
        val codeBg = if (isDark) "#1E293B" else "#F1F5F9"

        // 1. Escape HTML special characters to avoid parsing breakage (e.g. generics <T>)
        val escaped = raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // 2. Replace `code` with styled <code><b> snippet on a subtle background chip
        val formatted = escaped.replace(Regex("`([^`]+)`")) { match ->
            val code = match.groupValues[1]
            "<span style='background-color: $codeBg; color: $codeFg; padding: 1px 4px;'><code><b>$code</b></code></span>"
        }

        // 3. Preserve intentional line breaks
        return formatted.replace("\n", "<br/>")
    }

    fun formatQuestionHtml(raw: String): String {
        val content = formatInlineCode(raw)
        return "<html><div align='left' style='text-align: left;'>$content</div></html>"
    }

    fun formatOptionHtml(raw: String): String {
        val content = formatInlineCode(raw)
        return "<html><div align='left' style='text-align: left;'>$content</div></html>"
    }

    fun formatExplanationHtml(raw: String): String {
        val content = formatInlineCode(raw)
        return "<html><div align='left' style='text-align: left;'>$content</div></html>"
    }
}
