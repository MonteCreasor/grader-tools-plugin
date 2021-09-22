package edu.vanderbilt.grader.tools.actions

import com.intellij.CommonBundle
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileWrapper
import edu.vanderbilt.grader.tools.Constants
import edu.vanderbilt.grader.tools.actions.Actions.Type.*
import edu.vanderbilt.grader.tools.persist.Config
import edu.vanderbilt.grader.tools.persist.Config.Key.CommitMessage
import edu.vanderbilt.grader.tools.persist.Config.Key.ShowCommitMessage
import edu.vanderbilt.grader.tools.ui.RemoteSelection
import edu.vanderbilt.grader.tools.ui.Repo
import edu.vanderbilt.grader.tools.ui.RepoProp
import edu.vanderbilt.grader.tools.ui.RepoView
import edu.vanderbilt.grader.tools.utils.*
import git4idea.GitUtil
import git4idea.GitVcs
import org.gitlab.api.GitlabAPI
import java.io.File
import java.net.UnknownHostException
import java.nio.charset.Charset
import javax.swing.Icon
import kotlin.math.max

@Suppress("UNUSED_PARAMETER") // for unused e: AnActionEvent parameter in many functions
class Actions(repoView: RepoView) : ProjectContext(repoView.project) {
    enum class SelectionType {
        Checked,
        Selected,
        All
    }

    private fun validateGitUserAndGitToken(): Boolean =
        !config.validateGitUser(project).isNullOrBlank()
                && !config.validateGitToken(project).isNullOrBlank()

    private val resizeAfter = { repoView.sizeColumnsToFit() }
    private val resizeEach = { repoView.sizeColumnsToFit() }
    private var startTime = 0L
    private var actionType: Type? = null

    private val verbose: Boolean
        get() = config[Config.Key.Verbose].toBoolean()

    // Always use absolute path to repo root directory.
    private val reposRootDir: File
        get() = File(
            project.dir.asFile,
            config.getRepoRootDir(project)?.path ?: Constants.DEFAULT_REPO_DIR_NAME
        )

    fun getActions(selectionType: SelectionType): List<GraderAction> =
        values().map { GraderAction(it, selectionType) }

    inner class GraderAction(val type: Type, private val selectionType: SelectionType) :
        AnAction(type.id, type.desc, type.icon), DumbAware {
        override fun actionPerformed(actionEvent: AnActionEvent) {
            try {
                when (type) {
                    Gitlab -> projects(actionEvent, selectionType)
                    Load -> load(actionEvent, selectionType)
                    Save -> save(actionEvent, selectionType)
                    Refresh -> refresh(actionEvent, selectionType)
                    Clone -> clone(actionEvent, selectionType)
                    Install -> install(actionEvent, selectionType)
                    Grade -> grade(actionEvent, selectionType)
                    Commit -> commit(actionEvent, selectionType)
                    Pull -> pull(actionEvent, selectionType)
                    Push -> push(actionEvent, selectionType)
                    Post -> post(actionEvent, selectionType)
                    Status -> status(actionEvent, selectionType)
                    Remote -> remote(actionEvent, selectionType)
                    Delete -> delete(actionEvent, selectionType)
                    Add -> add(actionEvent, selectionType)
                    Revert -> revert(actionEvent, selectionType)
                    Run -> run(actionEvent, selectionType)
                    Find -> find(actionEvent, selectionType)
                    Settings -> settings()
                    Stop -> stop(actionEvent, selectionType)
                }
            } catch (e: Exception) {
                if (e is UnknownHostException) {
                    error("$type: Unable to reach ${config[Config.Key.GitSite]}")
                } else {
                    error("$type encountered an exception: $e")
                }
                actionEvent.project?.stopProcessing = true
                actionEvent.project?.isRunning = false
                repoView.refresh()
            }
        }

        override fun update(actionEvent: AnActionEvent) {
            if (actionEvent.project?.isRunning == true) {
                actionEvent.presentation.isEnabled = type == Stop
                if (type == Settings) {
                    actionEvent.presentation.isEnabled = true
                }
            } else {
                val repos = repoView.getRepos(selectionType)
                val selected = repos.count()
                val cloned = selected > 0 && selected == repos.count { it.rootDir.isDirectory }
                val project = selected > 0 && selected == repos.count { it.projDir?.isDirectory == true }

                actionEvent.presentation.isEnabled = when (type) {
                    Status, Remote -> cloned
                    Install, Grade -> project
                    Add, Pull, Push, Commit, Post, Revert -> cloned
                    Clone -> selected > 0 && !cloned
                    Delete, Save, Refresh -> selected > 0
                    Find -> selected == 1
                    Gitlab, Load, Settings, Run -> true
                    Stop -> false
                }
            }
        }
    }

    fun validateGitEnabled(): Boolean =
        if (GitUtil.isGitRoot(project.dir.path)) {
            true
        } else {
            error("Git commands require VCS Git integration.",
                simpleAction("Enable VCS Git integration") {
                    GitVcs.getInstance(project).enableIntegration()
                }
            )
            false
        }

    fun actionCompleted() {
        stopProcessing = false
        isRunning = false
        if (startTime != 0L) {
            val milliseconds = (System.currentTimeMillis() - startTime).toInt()
            val duration = (milliseconds / 1000.0).toInt()
            val msg = if (duration < 1) {
                "$milliseconds ${"millisecond".s(milliseconds)}"
            } else if (duration < 60) {
                "$duration ${"second".s(duration)}"
            } else {
                val min = duration.div(60)
                val sec = duration.rem(60)
                val minMsg = "$min ${"minute".s(min)}"
                if (sec > 0) {
                    "$minMsg ${"second".s(sec)}"
                } else {
                    minMsg
                }
            }
            logView.println("Total time: $msg")
        }
    }

    fun actionStarted(type: Type) {
        startTime = System.currentTimeMillis()
        isRunning = true
        actionType = type
        logView.processing()
    }

    fun load(e: AnActionEvent, selectionType: SelectionType) {
        val action = Load

        val file = chooseFile(project)?.asFile
        if (file == null) {
            info("${action.name} cancelled.")
            return
        }

        val json = file.readText()
        val repos = if (!json.isBlank()) {
            json.fromJson<List<Repo>>() ?: emptyList()
        } else {
            listOf()
        }

        if (repos.isEmpty()) {
            warn("${action.name}: file is empty.")
            return
        }

        val replace = if (repoView.model.rowCount > 0) {
            val response = MessageDialogBuilder.yesNoCancel(
                "Confirm Add or Replace",
                """|Repositories already exist.
                   |Choose Add to add the repositories or Replace 
                   |to remove all existing repositories before 
                   |adding the new ones.""".trimMargin("|")
            ).yesText("Add")
                .noText("Replace")
                .cancelText(CommonBundle.message("button.cancel"))
                .show(project)

            when (response) {
                Messages.YES -> false
                Messages.NO -> true
                else -> {
                    info("${action.name} cancelled.")
                    return
                }
            }
        } else {
            true
        }

        if (replace) {
            repoView.clear()
        }

        repos.forEach { it.project = project }

        val added = repoView.model.addAll(repos)
        if (added.isNotEmpty()) {
            repoView.table.rowSorter.allRowsChanged()
            repoView.model.fireTableDataChanged()
            added.forEach { repo ->
                repoView.refreshStatus(repo)
            }
        }

        val count = added.count()
        if (count > 0) {
            info("$count ${"repository".ies(count)} loaded.")
        } else {
            info("Load cancelled.")
        }

        check(repoView.model.rows == project.repoState.repos) {
            "rows != ReposState.repos"
        }
        check(repoView.model.rows.count() == project.repoState.repos.count()) {
            "rows != ReposState.repos"
        }
    }

    fun save(e: AnActionEvent, selectionType: SelectionType) {
        val action = Save
        val repos = repoView.getRepos(selectionType)
        if (repos.isEmpty()) {
            warn("${action.name}: No selected repositories.")
            return
        }

        val count = repos.count()
        val title = "Save Repository".ies(count)
        val saveFile = with(config[Config.Key.SavedReposFileName]) {
            val name = if (isNotBlank()) {
                this
            } else {
                Constants.DEFAULT_SAVE_FILE_NAME
            }
            File(project.dir.asFile, name)
        }

        val file = chooseSaveFile(project, title, saveFile.path)
        if (file == null) {
            info("${action.name} cancelled.")
            return
        }

        config[Config.Key.SavedReposFileName] = file.path

        val json = repos.toJson()
        if (!file.parentFile.isDirectory) {
            if (!file.parentFile.mkdirs())
                error("${action.name}: unable to create parent directory (${file.parentFile.path}")
            return
        }

        file.writeText(json)
        info("${"$count entry".ies(count)} saved.")
    }

    /**
     * Shows a modal dialog allowing user to add or replace
     * current repositories with new remote repositories.
     */
    fun projects(e: AnActionEvent, selectionType: SelectionType) {
        // Changing model data outside of normal table scope
        // requires deselecting any selected table rows to
        // prevent IndexOutOfBounds exceptions.
        repoView.table.clearSelection()

        if (!validateGitUserAndGitToken()) {
            return
        }

        val newRepos = RemoteSelection(project).showAndGetResult(repoView.getRepos())
        if (newRepos.isEmpty()) {
            return
        }

        val duplicates = newRepos.groupingBy { it.user }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            val list = newRepos.filter { duplicates.containsKey(it.user) }.sortedBy { it.user }
            if (!showDuplicatesDialog(list)) {
                return
            }
        }

        val curRepos = repoView.getRepos()
        val replace = if (curRepos.isNotEmpty()) {
            when (showAddOrReplaceDialog()) {
                Messages.YES -> false
                Messages.NO -> true
                else -> return
            }
        } else {
            true
        }

        if (replace) {
            repoView.clear()
        }

        val added = repoView.addAll(newRepos)

        info("$added ${"repository".ies(added)} added.")
    }

    fun clone(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        if (!reposRootDir.isDirectory) {
            reposRootDir.mkdirs()
        }

        val repos = repoView.getRepos(selectionType)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(Clone, repos, Repo.Status.Cloning, resizeAfter) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Clone, repo).map { line ->
                repo.rootDir.parentFile?.mkdirs()
                GeneralCommandLine(line)
                    .withWorkDirectory(reposRootDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach { commandLine ->
                val (exitCode, output) = runCommandLineWithOutput(commandLine)
                repo.refreshProjects()
                asyncRefresh(repo.rootDir)
                if (exitCode == 0) {
                    repo.lastPull = repo.date
                } else {
                    output.alert()
                }
            }
            resizeEach()
        }
    }

    fun install(e: AnActionEvent, selectionType: SelectionType) {
        val action = Install

        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        val installDirPath = config[Config.Key.InstallDir]
        if (installDirPath.isBlank()) {
            config.notifyErrorAndFix(project, Config.Key.InstallDir) {
                "Grader installation source directory has not been set."
            }
            return
        }

        val installDir = File(installDirPath).let { file ->
            if (file.isAbsolute) file else File("${project.dir.asFile.path}/$installDirPath")
        }

        if (!installDir.exists()) {
            config.notifyErrorAndFix(project, Config.Key.InstallDir) {
                "Install directory ${installDir.path} does not exist."
            }
            return
        }

        if (!installDir.isDirectory) {
            config.notifyErrorAndFix(project, Config.Key.InstallDir) {
                "Install directory ${installDir.path} is not a directory."
            }
            return
        }

        val fileList = installDir.listFiles()
        if (fileList.isNullOrEmpty()) {
            config.notifyErrorAndFix(project, Config.Key.InstallDir) {
                "Install directory ${installDir.path} is empty."
            }
            return
        }

        runActionOnRepos(action, repos, Repo.Status.Installing, resizeAfter) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            val toProjectDir = repo.projDir
            debug("Starting install for ${repo.user}:${repo.repo}")

            val fromProjectDir = File(installDir, repo.proj)

            when {
                toProjectDir == null -> "SKIPPING: no project has been selected for ${repo.user}:${repo.repo}\n".alert()
                !toProjectDir.isDirectory -> "SKIPPING: selected project directory $toProjectDir does not exist.\n".alert()
                !fromProjectDir.isDirectory -> {
                    "SKIPPING: Install directory ${installDir.name}/${repo.proj} does not exist.\n".alert()
                }
                fromProjectDir.listFiles().isNullOrEmpty() -> {
                    "SKIPPING: Install directory ${fromProjectDir.path} is empty.\n".alert()
                }
                else -> {
                    val files = fromProjectDir.listFiles()?.map { it.name }?.reduce { acc, name -> "$acc, $name" }
                    val fromPath = "${installDir.name}/${fromProjectDir.name}"
                    logView.println(
                        "copying $fromPath/[$files]\n" +
                                "   into ${repo.rootDir.name}/${toProjectDir.name}/"
                    )
                    FileUtil.copyDirContent(fromProjectDir, toProjectDir)
                    asyncRefresh(repo.rootDir)
                    resizeEach()
                }
            }
        }
    }

    fun refresh(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken()) {
            return
        }

        val refreshProps = listOf(Repo::date, Repo::status)
        fun refreshProp(prop: RepoProp) = refreshProps.contains(prop)

        val repos = repoView.getRepos(selectionType)
        if (repos.isEmpty()) {
            return
        }

        repoView.reset(repos, refreshProps)

        runActionOnRepos(Refresh, repos, Repo.Status.Refreshing, resizeAfter) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            if (refreshProp(Repo::projects)) {
                repo.refreshProjects()
            }

            if (refreshProp(Repo::status) || refreshProp(Repo::date)) {
                val gitSite = config.remoteGitUrl
                val api = GitlabAPI.connect(gitSite, config[Config.Key.GitToken])
                val gitProject = api.getProject(repo.user, repo.repo)
                when {
                    gitProject == null -> {
                        debug("$gitSite/${repo.user}/${repo.repo}: does not exists")
                        repoView.setStatus(repo, Repo.Status.NoRemote)
                    }
                    gitProject.defaultBranch.isNullOrBlank() -> {
                        debug("$gitSite/${repo.user}/${repo.repo}: no files")
                        repoView.setStatus(repo, Repo.Status.NoFiles)
                    }
                    else -> {
                        debug("$gitSite/${repo.user}/${repo.repo}: OK")
                        repoView.setDate(repo, gitProject.lastActivityAt)
                    }
                }
            }

            resizeEach()
        }
    }

    fun debug(msg: String) {
        if (verbose) {
            logView.println(msg)
        }
    }

    fun grade(e: AnActionEvent, selectionType: SelectionType) {
        val action = Grade
        val repos = repoView.getRepos(selectionType, cloned = true)
        val sdkDir = config.getSdkDir(project)
        val jdkDir = config.getJdkDir(project)
        if (repos.isEmpty() || sdkDir.isNullOrBlank() || jdkDir.isNullOrBlank()) {
            return
        }

        // Remove all grade files
        repos.forEach { repo ->
            if (repo.gradeFile.exists()) {
                repo.gradeFile.delete()
                repoView.refreshStatus(repo, true)
            }
        }

        runActionOnRepos(action, repos, Repo.Status.Grading, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            val commandName = if (isWindows) {
                "${Constants.GRADLE_COMMAND}.bat"
            } else {
                Constants.GRADLE_COMMAND
            }

            val gradleCommandFile = File(repo.projDir, commandName)

            // Necessary for Mac/Linux when gradlew is not executable.
            if (!gradleCommandFile.canExecute()) {
                gradleCommandFile.setExecutable(true)
            }

            val commandLine = GeneralCommandLine()
                .withExePath("${gradleCommandFile.absolutePath}")
                .withWorkDirectory(repo.projDir)
                .withEnvironment("ANDROID_SDK_ROOT", sdkDir)
                .withEnvironment("JAVA_HOME", jdkDir)
                .withCharset(Charset.forName("UTF-8"))
                .withParameters(
                    Constants.RUN_AUTOGARDER_TASK,
                    "-Psdk.dir='$sdkDir'",
                    "-Dedu.vanderbilt.grader.log.level=WARNING",
                    "-Dorg.gradle.jvmargs=-Xmx512M"
                )
            runCommandLine(commandLine)
            repo.recordGradeAsPercent(config[Config.Key.MarksOffPerFailedTest].toFloat())
            resizeEach()
            asyncRefresh(repo.rootDir)
        }
    }

    /**
     * Not currently used ... now performed as part of the commit command.
     */
    private fun add(actionEvent: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        val action = Add
        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(action, repos, Repo.Status.Adding, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Add, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.rootDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                runCommandLineWithOutput(it, verbose = true)
            }
        }
    }

    fun commit(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        if (config[ShowCommitMessage].toBoolean()) {
            val message = CommitDialog(project, config[CommitMessage]).showAndGetResult()
            if (message.isNullOrBlank()) {
                info("${Commit.name} was cancelled.")
                return
            }

            if (message != config[CommitMessage]) {
                config[CommitMessage] = message
            }
        }

        val action = Commit
        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(action, repos, Repo.Status.Committing, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Commit, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.projDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                runCommandLine(it)
            }
        }
    }

    fun pull(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(Pull, repos, Repo.Status.Pulling, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Pull, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.rootDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                val (exitCode, output) = runCommandLineWithOutput(it)
                when {
                    exitCode != 0 -> {
                        repo.refreshProjects()
                        asyncRefresh(repo.rootDir, true)
                    }
                    output.replace("Already up to date.", "").trim().isBlank() -> {
                        repo.lastPull = repo.date
                        "No changes\n".logNormal()
                    }
                    else -> {
                        repo.lastPull = repo.date
                        "$output\n".alert()
                        repo.refreshProjects()
                        asyncRefresh(repo.rootDir, true)
                    }
                }
            }
        }
    }

    fun push(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(Push, repos, Repo.Status.Pushing, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Push, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.rootDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                val (exitCode, output) = runCommandLineWithOutput(it)
                if (exitCode != 0) {
                    "$output\n".alert()
                } else if (output.isNotBlank()) {
                    repo.refresh()
                    "$output\n".logNormal()
                }
            }
        }
    }

    /**
     * Post performs both commit and push as one operation.
     */
    fun post(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        if (config[ShowCommitMessage].toBoolean()) {
            val message = CommitDialog(project, config[CommitMessage]).showAndGetResult()
            if (message.isNullOrBlank()) {
                info("${Commit.name} was cancelled.")
                return
            }

            if (message != config[CommitMessage]) {
                config[CommitMessage] = message
            }
        }

        val action = Post
        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(action, repos, Repo.Status.Posting, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            val addList = config.buildCmdList(Config.Key.Add, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.projDir)
                    .withCharset(Charset.forName("UTF-8"))
            }
            val commitList = config.buildCmdList(Config.Key.Commit, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.projDir)
                    .withCharset(Charset.forName("UTF-8"))
            }
            val pushList = config.buildCmdList(Config.Key.Push, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.projDir)
                    .withCharset(Charset.forName("UTF-8"))
            }

            addList.forEach {
                runCommandLine(it)
            }
            commitList.forEach {
                runCommandLine(it)
            }
            pushList.forEach {
                runCommandLine(it)
            }
        }
    }

    private fun revert(actionEvent: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        val action = Revert
        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(action, repos, Repo.Status.Reverting, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Revert, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.projDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                runCommandLine(it)
            }
        }
    }

    fun status(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(Status, repos, Repo.Status.Querying, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Status, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.rootDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                val (exitCode, output) = runCommandLineWithOutput(it)
                if (exitCode == 0) {
                    if (output.isBlank()) {
                        "No changes\n".logNormal()
                    } else {
                        "Local changes detected\n".alert()
                        "$output\n".logNormal()
                    }
                } else if (output.isNotBlank()) {
                    "$output\n".alert()
                }
            }
        }
    }

    fun remote(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(Remote, repos, Repo.Status.Querying, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Diff, repo).map { line ->
                GeneralCommandLine(line)
                    .withWorkDirectory(repo.rootDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                val (exitCode, output) = runCommandLineWithOutput(it)
                if (exitCode == 0) {
                    if (output.isBlank()) {
                        "No changes\n".logNormal()
                        repo.lastPull = repo.date
                    } else {
                        "Remote changes detected\n".alert()
                        repo.lastPull = null
                    }
                } else if (output.isNotBlank()) {
                    "$output\n".alert()
                }
            }
        }
    }

    fun settings() {
        config.showUI(project)
    }

    fun stop(e: AnActionEvent, selectionType: SelectionType) {
        if (isRunning) {
            stopProcessing = true
        }
    }

    fun showAddOrReplaceDialog(): Int =
        MessageDialogBuilder.yesNoCancel(
            "Confirm Add or Replace",
            """|Local repositories already exist.
               |
               |Choose Add to add the selected repositories
               |or Replace to remove existing local repositories 
               |before adding the new ones.""".trimMargin("|")
        ).yesText("Add").noText("Replace").cancelText(
            CommonBundle.message("button.cancel")
        ).show(project)

    fun showDuplicatesDialog(duplicates: List<Repo>): Boolean {
        fun stringList(list: List<Repo>): String = with(StringBuilder()) {
            list.forEach {
                append("|${it.user}: ${it.repo}\n")
            }
            toString()
        }

        return MessageDialogBuilder.yesNo(
            "Duplicate Users Found",
            """|The following users have been included more than once:
               |
               ${stringList(duplicates)}
               |
               |Choose OK to continue and add these repositories anyway 
               |(you will be prompted before overwriting existing repositories)
               |or Cancel to stop this operation.""".trimMargin("|")
        ).yesText("OK")
            .noText(CommonBundle.message("button.cancel"))
            .ask(project)
    }

    fun showOverwriteDialog(oldRepo: Repo, newRepo: Repo): Int {
        return MessageDialogBuilder.yesNoCancel(
            "Overrwrite existing repository?",
            """|User ${oldRepo.user} already has a local repository.
               |
               |Would you like to replace the existing repository
               |
               |    ${oldRepo.user}:${oldRepo.repo} 
               |
               |with this one?
               |
               |    ${newRepo.user}:${newRepo.repo}
               |.""".trimMargin("|")
        ).show(project)
    }

    fun find(actionEvent: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.count() == 1) {
            project.logView.find(repos.first().user)
        }
    }

    fun run(actionEvent: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitUserAndGitToken() || !validateGitEnabled()) {
            return
        }

        val action = Run
        val repos = repoView.getRepos(selectionType, cloned = true)
        if (repos.isEmpty()) {
            return
        }

        runActionOnRepos(action, repos, Repo.Status.Running, {}) { repo, status ->
            if (status.isCanceled) {
                return@runActionOnRepos
            }

            config.buildCmdList(Config.Key.Run, repo).map { line ->
                val command = if (line.count() == 1 && File(line[0]).isFile) {
                    // Run file as a bash script.
                    listOf("bash", line[0])
                } else {
                    // Run string as a bash command
                    line
                }

                GeneralCommandLine(command)
                    .withWorkDirectory(repo.projDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                runCommandLine(it)
            }
        }
    }

    fun delete(e: AnActionEvent, selectionType: SelectionType) {
        if (!validateGitEnabled()) {
            return
        }

        // Check if repositories are passed from a simulated invoked action event (from code).
        var repos: List<Repo> =
            DataManager.getInstance().loadFromDataContext(e.dataContext, REPOS_KEY)
                ?: emptyList()

        val runAction = if (repos.isNotEmpty()) {
            // Delete is part of another operation so perform a modal delete action with no confirmation.
            ::runModalActionOnRepos
        } else {
            // Delete is invoked by user so perform an asynchronous delete action with confirmation.
            repos = repoView.getRepos(selectionType)
            if (repos.isEmpty() || !confirmDelete(repos.count())) {
                return
            }
            ::runActionOnRepos
        }

        val relativeReposRootDir = project.dir.asFile.toPath().relativize(reposRootDir.toPath()).toFile().path
        val gitModulesDir = File("${project.dir.path}/.git/modules", relativeReposRootDir)

        val afterAll = {
            resizeAfter()
            asyncRefresh(reposRootDir)
        }

        runAction(Delete, repos, Repo.Status.Deleting, afterAll) { repo, _ ->
            config.buildCmdList(Config.Key.Delete, repo).map { lines ->
                GeneralCommandLine(lines)
                    .withWorkDirectory(reposRootDir)
                    .withCharset(Charset.forName("UTF-8"))
            }.forEach {
                try {
                    runCommandLineWithOutput(it)

                    if (repo.rootDir.isDirectory) {
                        repo.rootDir.deleteRecursively()
                    }
                    with(File(gitModulesDir, repo.dir)) {
                        if (isDirectory) {
                            deleteRecursively()
                        }
                    }
                } catch (t: Throwable) {
                }

                repo.lastPull = null
            }

            val remove = config.deleteTableEntries
            if (remove) {
                repoView.remove(repo)
            } else {
                repoView.refreshStatusAndWait(repo, refreshProjects = true)
            }
        }
    }

    private fun runCommandLineWithOutput(
        commandLine: GeneralCommandLine,
        verbose: Boolean = this.verbose
    ): Pair<Int, String> {
        var exitCode = 0
        var output = ""

        with(OSProcessHandler(commandLine)) {
            ProcessTerminatedListener.attach(this)
            addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    exitCode = event.exitCode
                }

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (event.text != null) {
                        output += event.text
                    }
                }
            })

            startNotify()

            // TODO: handle output manually instead of attaching to process?
            // console.attachToProcess(this)

            while (!isProcessTerminated && !waitFor(100L)) {
                if (stopProcessing) {
                    destroyProcess()
                }
            }
        }

        // Strip off command line and normal exit code strings.
        val result = output
            .replace("${commandLine.commandLineString}\n", "")
            .replace("""\nProcess finished with exit code [0-9]+\n$""".toRegex(), "")
            .trim()

        if (result.isNotBlank() && (exitCode != 0 || verbose)) {
            logView.print(output)
        }

        return Pair(exitCode, result)
    }

    private fun runCommandLine(commandLine: GeneralCommandLine): Int {
        var status = 0
        with(OSProcessHandler(commandLine)) {
            ProcessTerminatedListener.attach(this)
            addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    if (event.exitCode != 0) {
                        status = event.exitCode
                    }
                }

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                }
            })

            debug(commandLine.commandLineString)

            startNotify()

            console.attachToProcess(this)
            while (!isProcessTerminated && !waitFor(100L)) {
                if (stopProcessing) {
                    destroyProcess()
                }
            }
        }

        return status
    }

    fun runModalActionOnRepos(
        type: Type,
        repos: List<Repo>,
        busyStatus: Repo.Status,
        after: () -> Unit,
        block: (repo: Repo, indicator: ProgressIndicator) -> Unit
    ) {
        actionStarted(type)
        RunAction.runModalWithProgress(project, type.name) { indicator ->
            try {
                indicator.isIndeterminate = false
                repos.forEachIndexed { i, repo ->
                    runActionOnRepo(type, repo, busyStatus, indicator, block)
                    if (indicator.isCanceled) {
                        info("${type.name} was cancelled.")
                        return@runModalWithProgress
                    }
                    indicator.fraction = (i + 1) / repos.count().toDouble()
                }
                val count = repos.count()
                logView.println("-".repeat(90))
                info("${type.name} has completed for $count ${"project".s(count)}.")
                after()
            } finally {
                actionCompleted()
            }
        }
    }

    fun runActionOnRepos(
        type: Type,
        repos: List<Repo>,
        busyStatus: Repo.Status,
        after: () -> Unit,
        block: (repo: Repo, indicator: ProgressIndicator) -> Unit
    ) {
        actionStarted(type)
        RunAction.runInBackgroundWithProgress(project, type.name) { indicator ->
            try {
                indicator.isIndeterminate = false
                repos.forEachIndexed { i, repo ->
                    runActionOnRepo(type, repo, busyStatus, indicator, block)
                    if (indicator.isCanceled) {
                        info("${type.name} was cancelled.")
                        return@runInBackgroundWithProgress
                    }
                    indicator.fraction = (i + 1) / repos.count().toDouble()
                }
                val count = repos.count()
                logView.println("-".repeat(80))
                info("${type.name} has completed for $count ${"project".s(count)}.")
            } finally {
                after()
                actionCompleted()
            }
        }
    }

    fun runActionOnRepo(
        type: Type,
        repo: Repo,
        busyStatus: Repo.Status,
        indicator: ProgressIndicator,
        block: (repo: Repo, indicator: ProgressIndicator) -> Unit
    ) {
        try {
            logView.println("${type.name}: ${repo.user} [${repo.proj}]".center(80))

            if (indicator.isCanceled || stopProcessing) {
                indicator.cancel()
                return
            }
            // Run in UI thread and wait
            repoView.setStatus(repo, busyStatus, wait = true)
            block(repo, indicator)
            if (repoView.exists(repo)) {
                repoView.refreshStatusAndWait(repo, refreshRow = true)
                when (type) {
                    Clone, Delete -> repo.refreshProjects()
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            repoView.setStatus(repo, Repo.Status.Error, wait = true)
            if (e is UnknownHostException) {
                error("${type.name}: Unable to reach ${config[Config.Key.GitSite]}")
                return
            } else {
                error("${type.name} encountered an exception: ${config.hidePasswords(e.message)}")
                return
            }
        }
    }

    fun asyncRefresh(file: File, recursive: Boolean = false) {
        if (file.exists()) {
            VirtualFileWrapper(file).virtualFile?.refresh(
                true,
                file.isDirectory && recursive
            )
        }
    }

    fun confirmDelete(count: Int): Boolean {
        return MessageDialogBuilder.yesNo(
            "Confirm Delete Repository",
            "Delete $count ${"repository".ies(count)}?"
        ).yesText("Delete").noText(
            CommonBundle.message("button.cancel")
        ).doNotAsk(
            // Piggy back on "do not ask" checkbox which requires
            // negating all results for this remove row checkbox.
            object : DialogWrapper.DoNotAskOption {
                override fun isToBeShown() = !config.deleteTableEntries
                override fun setToBeShown(value: Boolean, exitCode: Int) {
                    config.deleteTableEntries = !value
                }

                override fun canBeHidden() = true
                override fun shouldSaveOptionsOnCancel() = true
                override fun getDoNotShowMessage() = "Also remove table ${"entry".ies(count)}?"
            }
        ).ask(project)
    }

    private fun String.logNormal() {
        logView.print(this, ConsoleViewContentType.LOG_INFO_OUTPUT)
    }

    private fun String.alert() {
        logView.print(this, ConsoleViewContentType.LOG_ERROR_OUTPUT)
    }

    private fun String.center(width: Int, padChar: Char = '-') =
        padStart(max(0, (width + length) / 2), padChar).padEnd(80, padChar)

    enum class Type(val id: String, val desc: String, val icon: Icon) {
        Settings(
            "Settings",
            "Open settings dialog",
            Constants.Icons.Settings
        ),
        Gitlab(
            "Open GitLab",
            "Select remote repositories to include in this project",
            Constants.Icons.Gitlab
        ),
        Load(
            "Load Repo List",
            "Load repositories from a file",
            Constants.Icons.Load
        ),
        Save(
            "Save Repo List",
            "Save selected repositories",
            Constants.Icons.Save
        ),
        Clone(
            "Git Clone",
            "Clone selected Gitlab repositories",
            Constants.Icons.Clone
        ),
        Install(
            "Install Files",
            "Install files into selected repositories",
            Constants.Icons.Install
        ),
        Grade(
            "Grade",
            "Run the grader in the selected repositories",
            Constants.Icons.Grade
        ),
        Add(
            "Git Add",
            "Add untracked files in selected repositories",
            Constants.Icons.Add
        ),
        Commit(
            "Git Commit",
            "Commit changes for the selected repositories",
            Constants.Icons.Commit
        ),
        Push(
            "Git Push",
            "Push changes for selected repositories",
            Constants.Icons.Push
        ),
        Post(
            "Git Commit and Push",
            "Commits and pushes all changes for the selected repositories",
            Constants.Icons.Post
        ),
        Pull(
            "Git Pull",
            "Pull remote changes for selected repositories",
            Constants.Icons.Pull
        ),
        Revert(
            "Git Revert",
            "Revert all tracked and untracked files in selected repositories",
            Constants.Icons.Revert
        ),
        Status(
            "Git Status",
            "Git status the currently selected repositories",
            Constants.Icons.Status
        ),
        Remote(
            "Refresh Last Modified Column",
            "Refresh last modified column for selected repositories",
            Constants.Icons.Remote
        ),
        Refresh(
            "Refresh Column Values",
            "Refresh all repository status values",
            Constants.Icons.Refresh
        ),
        Delete(
            "Delete",
            "Delete selected repositories from this project",
            Constants.Icons.Delete
        ),
        Find(
            "Find in Log View",
            "Find the selected user in the log view.",
            Constants.Icons.Find
        ),
        Run(
            "Run Custom Commands",
            "Run custom commands in project root directory of selected repositories",
            Constants.Icons.Run
        ),
        Stop(
            "Stop",
            "Stop the currently running process",
            Constants.Icons.Stop
        )
    }
}
