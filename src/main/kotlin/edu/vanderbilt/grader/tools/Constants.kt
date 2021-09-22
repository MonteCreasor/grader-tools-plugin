package edu.vanderbilt.grader.tools

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader

object Constants {
    const val TOOL_WINDOW_ID = "Vanderbilt Tools"
    const val GROUP_ID = TOOL_WINDOW_ID
    const val NOTIFICATION_TITLE = "Vanderbilt Tools"

    val BUILD = Constants::class.java.classLoader.getResource("build.txt")?.readText()?.trim() ?: ""
    val NOTIFICATION_SUB_TITLE = ""

    const val RUN_AUTOGARDER_TASK = "runAutograder"
    const val CONFIG_CHANGE_BUS_ID = "Vanderbilt Grader Configuration Change"
    const val GRADLE_COMMAND = "gradlew"
    const val DEFAULT_SAVE_FILE_NAME = "STUDENTS"
    const val DEFAULT_INSTALL_DIR_NAME = "INSTALL"
    const val DEFAULT_REPO_DIR_NAME = "REPOS"
    const val GRADE_FILE_NAME = "GRADE"

    object Icons {
        val Gitlab = IconLoader.getIcon("/toolWindow/gitlab.png")
        val Graded = AllIcons.General.InspectionsOK
        val Error = AllIcons.General.ExclMark
        val Load = AllIcons.Actions.Menu_open
        val Save = AllIcons.Actions.Menu_saveall
        val Clone = AllIcons.Actions.Download
        val Install = AllIcons.Actions.Copy
        val Grade = AllIcons.Actions.Execute
        val Commit = AllIcons.Actions.Commit
        val Pull = AllIcons.Actions.CheckOut
        val Push = AllIcons.Vcs.Push
        val Post = IconLoader.getIcon("/toolWindow/post.png")
        val Status = IconLoader.getIcon("/toolWindow/changelist.svg")
        val Remote = AllIcons.Actions.Diff
        val Refresh = AllIcons.Actions.Refresh
        val Delete = AllIcons.Actions.GC
        val Settings = AllIcons.General.Settings
        val Add = AllIcons.General.Add
        val Revert = AllIcons.Diff.Revert
        val Run = AllIcons.Actions.Run_anything
        val Find = AllIcons.Actions.Find
        val Stop = AllIcons.Actions.Suspend
    }
}