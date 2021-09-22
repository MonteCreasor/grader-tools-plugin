package edu.vanderbilt.grader.tools.ui

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.openapi.project.Project
import edu.vanderbilt.grader.tools.Constants
import edu.vanderbilt.grader.tools.Constants.GRADE_FILE_NAME
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.persist.Config.Key.RepoRoot
import edu.vanderbilt.grader.tools.utils.*
import java.io.File
import java.lang.Integer.max
import java.util.*
import javax.swing.Icon
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

typealias RepoProp = KProperty1<Repo, Any?>
typealias RepoPropList = List<RepoProp>

@JsonIgnoreProperties(ignoreUnknown = true)
class Repo(
    @JsonProperty
    @TableColumnProp(index = 0, value = "", editable = true)
    var selected: Boolean,

    @JsonProperty
    @TableColumnProp(index = 1, value = "User", editable = false)
    var user: String,

    @JsonProperty
    @TableColumnProp(index = 2, value = "Repository", editable = false)
    var repo: String,

    @JsonProperty
    @TableColumnProp(index = 3, value = "Last Update", editable = false)
    var date: Date?,

    @JsonProperty
    @TableColumnProp(index = 4, value = "Project", editable = true)
    var proj: String,

    @JsonProperty
    @TableColumnProp(index = 5, value = "Directory", editable = true)
    var dir: String,

    @JsonProperty
    @TableColumnProp(index = 6, value = "Status", editable = false)
    var status: Status,

    @JsonProperty
    @TableColumnProp(index = 7, value = "Grade", editable = false)
    var grade: String
) {
    /** Required for helper properties to build absolute paths */
    @Transient
    @com.intellij.util.xmlb.annotations.Transient
    @JvmField
    @JsonIgnore
    var project: Project? = null

    var lastPull: Date? = null

    @get:JsonIgnore
    val projects: MutableList<File> by lazy { findProjects().toMutableList() }

    /** Required default constructor for XML serialization */
    constructor() : this(false, "", "", null, "", "", Status.None, "")

    enum class Status(val title: String, val desc: String, val icon: Icon? = null) {
        Local("local", "Has been cloned."),
        Remote("remote", "Has not been cloned.", Constants.Icons.Gitlab),
        Graded("graded", "Has been successfully graded.", Constants.Icons.Graded),
        NoFiles("no files", "Remote repository is empty.", Constants.Icons.Error),
        NoRemote("no remote", "Remote repository does not exit.", Constants.Icons.Error),
        NoProject("no project", "Missing target project directory.", Constants.Icons.Error),
        NoGradle("no project", "Missing required Gradle build files.", Constants.Icons.Error),
        NoGrader("no grader", "Grader is not installed.", Constants.Icons.Error),
        NoInternet("no network", "Unable to reach remote Git website", Constants.Icons.Error),
        Error("error", "Grader failed.", Constants.Icons.Graded),
        Grading("grading", "Grader is running ...", Constants.Icons.Grade),
        Installing("installing", "Installing grader ...", Constants.Icons.Install),
        Committing("committing", "Committing changes ...", Constants.Icons.Commit),
        Adding("adding", "Adding files ...", Constants.Icons.Add),
        Reverting("Reverting", "Reverting changes ...", Constants.Icons.Revert),
        Posting("posting", "Posting (committing/pushing) changes ...", Constants.Icons.Post),
        Cloning("cloning", "Cloning remote repository ...", Constants.Icons.Clone),
        Deleting("deleting", "Deleting local repository ...", Constants.Icons.Delete),
        Refreshing("refreshing", "Refreshing ...", Constants.Icons.Refresh),
        Pulling("pulling", "Pulling changes from remote ...", Constants.Icons.Pull),
        Pushing("pushing", "Pushing changes to remote ...", Constants.Icons.Push),
        Running("running", "Running shell command ...", Constants.Icons.Run),
        Querying("querying", "Querying git status ...", Constants.Icons.Remote),
        None("", "No status");

        override fun toString(): String {
            return title
        }
    }

    @get:JsonIgnore
    val rootDirPath: String
        get() = project!!.config[RepoRoot].let { if (it.isNotBlank()) "$it/$dir" else dir }

    @get:JsonIgnore
    val projectPath: String
        get() = if (proj.isBlank()) "" else if (proj == ".") rootDirPath else "$rootDirPath/$proj"

    @get:JsonIgnore
    val rootDir: File
        get() = File(project!!.dir.asFile, rootDirPath)

    @get:JsonIgnore
    val projDir: File?
        get() = if (projectPath.isBlank()) null else File(project!!.dir.asFile, projectPath)

    @get:JsonIgnore
    val gradeFile: File
        get() = File(projDir, GRADE_FILE_NAME)

    @get:JsonIgnore
    val remoteUserHyperLink: String
        get() = "https://${project!!.config[Config.Key.GitSite]}/$user"

    @get:JsonIgnore
    val remoteRepoHyperLink: String
        get() = "${remoteUserHyperLink}/${repo}"

    @get:JsonIgnore
    val patternMap: Map<String, String>
        get() = mapOf(
            indexOf(Repo::selected).toString() to selected.toString(),
            indexOf(Repo::user).toString() to user,
            indexOf(Repo::repo).toString() to repo,
            indexOf(Repo::proj).toString() to proj,
            indexOf(Repo::dir).toString() to dir,
            indexOf(Repo::status).toString() to status.toString(),
            indexOf(Repo::grade).toString() to grade
        )

    @get:JsonIgnore
    val isGradableProject: Boolean
        get() = isGradleProject && isGraderInstalled

    @get:JsonIgnore
    val isGraderInstalled: Boolean
        get() = File(projDir, graderDirName).let {
            File(it, unitTestFileName).exists() || File(it, instrumentedTestFileName).exists()
        }

    @get:JsonIgnore
    val isGradleProject: Boolean
        get() = projDir?.isDirectory == true
                && File(projDir, "build.gradle").isFile
                && File(projDir, "settings.gradle").isFile

    @get:JsonIgnore
    val isGradedProject: Boolean
        get() = isGradleProject &&
                isGraderInstalled &&
                hasGrade

    @get:JsonIgnore
    val hasGraderOutput: Boolean
        get() = gradeFile.isFile

    @get:JsonIgnore
    val hasGrade: Boolean
        get() = gradeAsRatio != null

    /**
     * Grade must on first line of GRADE file; everything else is ignored.
     * If GRADE file contains only a ration of points to total points this
     * means the grader was run by the student and is therefore not an
     * official grade. Official grades will contain this ration but also
     * an appended official percent mark along with an explanation of the
     * percent calculation.
     */
    @get:JsonIgnore
    val gradeAsString: String?
        get() {
            val ratio = gradeAsRatio
            return if (ratio == null) {
                ""
            } else {
                val errors = ratio.second - ratio.first
                val percent = max(0, 100 - (errors * project!!.config[Config.Key.MarksOffPerFailedTest].toInt()))
                return "$percent% ($errors error${if (errors == 1) "" else "s"})"
            }
        }

    @get:JsonIgnore
    val gradeAsRatio: Pair<Int, Int>?
        get() {
            if (!gradeFile.isFile) {
                return null
            }

            val lines = gradeFile.readLines()
            if (lines.isEmpty()) {
                return null
            }

            val line = lines[0].trim()
            if (!line.matches(gradeRegex)) {
                return null
            }

            val matchResult = gradeRegex.matchEntire(line)
            val score = matchResult?.groupValues?.get(1)?.trim()?.toInt() ?: 0
            val points = matchResult?.groupValues?.get(2)?.trim()?.toInt() ?: 0
            return Pair(score, points)
        }

    @get:JsonIgnore
    val Triple<Int, Int, Float>.asRatio
        get() = "$first/$second"

    @get:JsonIgnore
    val Triple<Int, Int, Float>.asPercent
        get() = "$third%"

    /** Called when a repo cloned, deleted, or if master project is refactored/moved */
    fun refreshProjects() {
        projects.clear()
        projects.addAll(findProjects())
        if (projDir?.isGradleProject == false) {
            proj = ""
        }
    }

    fun refresh(refreshProjects: Boolean = false) {
        grade = ""

        if (refreshProjects) {
            refreshProjects()
        }

        when {
            status == Status.NoFiles -> Unit
            !rootDir.isDirectory -> {
                status = Status.Remote
                proj = ""
            }
            !isGradleProject -> {
                proj = ""
                status = Status.Local
            }
            !isGraderInstalled -> status = Status.NoGrader
            else -> {
                status = if (!hasGraderOutput) {
                    Status.Local
                } else {
                    grade = gradeAsString ?: "error"
                    if (grade != "error") {
                        Status.Graded
                    } else {
                        Status.Local
                    }
                }
            }
        }
    }

    fun findProjects(): List<File> {
        return if (rootDir.isDirectory) {
            if (rootDir.isGradleProject) {
                listOf(File("."))
            } else {
                rootDir.walk(FileWalkDirection.TOP_DOWN).filter {
                    it.isGradleProject
                }.map {
                    File(it.path.substringAfter("${rootDir.path}${File.separatorChar}"))
                }.toList()
            }
        } else {
            listOf()
        }
    }

    fun recordGradeAsPercent(marksOffPerFailedTest: Float) {
        if (hasGrade) {
            val ratio = gradeAsRatio
            val gradeString = gradeAsString

            if (ratio != null) {
                gradeFile.writeText("${ratio.first}/${ratio.second} Your grade is $gradeString.")
            }
        }
    }

    companion object {
        fun indexOf(name: String): Int =
            Repo::class.declaredMemberProperties.firstOrNull { it.name == name }
                ?.findAnnotation<TableColumnProp>()?.index ?: -1

        fun indexOf(prop: KProperty1<Repo, Any?>): Int = indexOf(prop.name)
    }
}
