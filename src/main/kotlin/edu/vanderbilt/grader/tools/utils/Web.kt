package edu.vanderbilt.grader.tools.utils

import java.awt.Desktop
import java.net.URI

object WebUtils {
    fun openWebPage(uri: String) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(URI(uri))
            } catch (ignored: Exception) {
            }
        }
    }

    fun generateTokenUrl(gitSite: String): String? {
        val hostText = "https://$gitSite"
        val helpUrl = StringBuilder()
        helpUrl.append(hostText)
        if (!hostText.endsWith("/")) {
            helpUrl.append("/")
        }
        helpUrl.append("-/profile/personal_access_tokens")
        return helpUrl.toString()
    }
}
