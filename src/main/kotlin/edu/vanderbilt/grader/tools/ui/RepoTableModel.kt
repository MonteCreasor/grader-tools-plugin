package edu.vanderbilt.grader.tools.ui

import com.intellij.openapi.project.Project
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.ui.BaseTable.CheckBoxTableModel
import edu.vanderbilt.grader.tools.utils.*
import edu.vanderbilt.grader.tools.utils.ThreadUtils.assertUiThread
import java.io.File

class RepoTableModel(val project: Project) : CheckBoxTableModel<Repo>(Repo::class.java, 0) {
    val config: Config
        get() = project.config

    override fun isCellEditable(row: Int, column: Int): Boolean {
        assertUiThread()
        return when {
            project.isRunning -> false
            column == Repo.indexOf(Repo::dir) -> {
                val repo = getObjectAt(row)
                !repo.rootDir.exists()
            }
            column == Repo.indexOf(Repo::proj) -> {
                val repo = getObjectAt(row)
                repo.projects.isNotEmpty()
            }
            else -> super.isCellEditable(row, column)
        }
    }

    override fun getHyperLink(row: Int, column: Int): String? {
        assertUiThread()
        if (row == -1 || column == -1) {
            return null
        }

        return getHyperLink(getObjectAt(row), column)
    }

    /**
     * Accepts ComboBoxProjectItem or path String for Repo::proj property.
     */
    override fun setValueAt(value: Any?, row: Int, column: Int) {
        assertUiThread()
        when (column) {
            fieldToIndex(Repo::proj) -> {
                // KLUDGE: checks if type is special ComboBoxProjectItem and replaces
                // passed value with actual relPath. This allows combo box to show
                // just project names while the backing data is actually a relative
                // path.
                val path = if (value is RepoView.ComboBoxProjectItem) value.relPath else value ?: ""
                super.setValueAt(path, row, column)
            }
            fieldToIndex(Repo::selected) -> {
                super.setValueAt(value, row, column)
                project.graderTools.status.checked = checkedRows.count()
            }

            else -> super.setValueAt(value, row, column)
        }
    }

    /**
     * Only returns hyperlink if there is a valid click destination.
     */
    fun getHyperLink(repo: Repo, column: Int): String? {
        assertUiThread()
        return when (propAt(column)) {
            Repo::user -> if (repo.remoteUserHyperLink.isNotBlank()) repo.remoteUserHyperLink else null
            Repo::repo -> if (repo.remoteRepoHyperLink.isNotBlank()) repo.remoteRepoHyperLink else null
            Repo::dir -> if (repo.rootDir.isDirectory) repo.rootDir.absolutePath else null
            Repo::proj -> repo.projDir?.absolutePath
            Repo::grade -> if (repo.gradeFile.isFile) repo.gradeFile.absolutePath else null
            Repo::status -> {
                when (repo.status) {
                    Repo.Status.Local -> when {
                        repo.projDir?.isDirectory == true -> repo.projDir?.absolutePath
                        repo.rootDir.isDirectory -> repo.rootDir.absolutePath
                        else -> null
                    }
                    Repo.Status.Remote -> if (config.remoteGitUrl.isNotBlank()) config.remoteGitUrl else null
                    Repo.Status.Graded, Repo.Status.Error -> {
                        val graderOutputDir = File(repo.projDir, submissionDirName)
                        val summaryFile = File(graderOutputDir, summaryFileName)
                        val unitTestFeedbackFile = File(graderOutputDir, unitTestFeedback)
                        when {
                            unitTestFeedbackFile.isFile -> unitTestFeedbackFile.absolutePath
                            summaryFile.isFile -> summaryFile.absolutePath
                            graderOutputDir.isDirectory -> graderOutputDir.absolutePath
                            else -> null
                        }
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    fun addAll(repos: List<Repo>): List<Repo> {
        assertUiThread()
        // Duplicates are not allowed.
        val newRepos = repos.filterNot { repo ->
            rows.any { row ->
                row.user == repo.user && row.repo == repo.repo
            }
        }

        return if (newRepos.isNotEmpty()) {
            val firstNewRow = rows.size
            val lastNewRow = firstNewRow + newRepos.count() - 1
            rows.addAll(newRepos)
            fireTableRowsInserted(firstNewRow, lastNewRow)
            check(rows == project.repoState.repos) {
                "rows != ReposState.repos"
            }
            check(rows.count() == project.repoState.repos.count()) {
                "rows != ReposState.repos"
            }
            newRepos
        } else {
            listOf()
        }
    }

    fun load(file: File): List<Repo> {
        assertUiThread()
        val json = file.readText()
        return if (!json.isBlank()) {
            json.fromJson<List<Repo>>() ?: emptyList()
        } else {
            listOf()
        }
    }

    fun save(file: File): Boolean {
        assertUiThread()
        val json = rows.toJson()
        if (!file.parentFile.isDirectory) {
            if (!file.parentFile.mkdirs())
                return false
        }
        file.writeText(json)
        return true
    }

    fun reset(repo: Repo, props: RepoPropList) {
        props.forEach { reset(repo, it) }
    }

    fun reset(repo: Repo, prop: RepoProp) {
        when (prop) {
            Repo::status -> setValueAt(Repo.Status.None, repo, prop)
            Repo::grade -> setValueAt("", repo, prop)
            Repo::date -> setValueAt(null, repo, prop)
            Repo::projects -> {
                repo.refreshProjects()
                fireTableCellUpdated(getRowFor(repo), prop.toColumn())
            }
        }
    }
}