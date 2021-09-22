package edu.vanderbilt.grader.tools.ui

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
class RepoTest(
    @JsonProperty
    var selected: Boolean,

    @JsonProperty
    var user: String,

    @JsonProperty
    var repo: String,

    @JsonProperty
    var proj: String,

    @JsonProperty
    var dir: String,

    @JsonProperty
    var status: Status,

    @JsonProperty
    var grade: String
) {
    @JvmField
    @JsonIgnore
    var project: String? = null

    @get:JsonIgnore
    val projects: MutableList<File> by lazy {
        mutableListOf(File("1"), File("1"), File("1"))
    }

    /** Required default constructor for XML serialization */
    constructor() : this(false, "", "", "", "", Status.None, "")

    enum class Status(val title: String, val desc: String) {
        Local("local", "Has been cloned."),
        Remote("remote", "Has not been cloned."),
        Graded("graded", "Has been successfully graded."),
        NoFiles("no files", "Remote repository is empty."),
        NoProject("no project", "Missing target project directory."),
        NoGradle("no project", "Missing required Gradle build files."),
        NoGrader("no grader", "Grader is not installed."),
        NoInternet("no network", "Unable to reach remote Git website"),
        Error("error", "Grader failed."),
        Grading("grading", "Grader is running ..."),
        Installing("installing", "Installing grader ..."),
        Committing("committing", "Committing changes ..."),
        Cloning("cloning", "Cloning remote repository ..."),
        Deleting("deleting", "Deleting local repository ..."),
        Refreshing("refreshing", "Refreshing ..."),
        Pushing("pushing", "Pushing changes to remote ..."),
        Pulling("pulling", "Pulling changes from remote ..."),
        Querying("querying", "Querying git status ..."),
        None("", "No status");

        override fun toString(): String {
            return title
        }
    }

    @get:JsonIgnore
    val rootDirPath: String?
        get() = "rootDirPath"

    @get:JsonIgnore
    val projectPath: String
        get() = "projectPath"

    @get:JsonIgnore
    val rootDir: File
        get() = File("rootDir")

    @get:JsonIgnore
    val projDir: File?
        get() = null

    @get:JsonIgnore
    val gradeFile: File
        get() = File(projDir, "gradeFile")

    @get:JsonIgnore
    val remoteUserHyperLink: String
        get() = "https://remoteUserHyperLink"
}
