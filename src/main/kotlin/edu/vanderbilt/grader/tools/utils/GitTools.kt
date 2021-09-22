package edu.vanderbilt.grader.tools.utils

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersion
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository


object GitTools {
    //    private val settingsState: SettingsState = SettingsState.getInstance()
    var gitlabServers: MutableList<GitlabServer> = mutableListOf()

    class GitlabServer {
        val apiUrl = ""
        val apiToken = ""
        val repositoryUrl = ""
        val preferredConnection =
            CheckoutType.SSH
        val removeSourceBranch = true
        override fun toString(): String {
            return apiUrl
        }

        enum class CheckoutType {
            SSH, HTTPS
        }
    }

    fun getGitRepository(project: Project, file: VirtualFile?): GitRepository? {
        val manager = GitUtil.getRepositoryManager(project)
        val repositories = manager.repositories
        if (repositories.isEmpty()) {
            return null
        }

        if (repositories.size == 1) {
            return repositories[0]
        }

        if (file != null) {
            val repository = manager.getRepositoryForFile(file)
            if (repository != null) {
                return repository
            }
        }

        return manager.getRepositoryForFile(ProjectUtil.getBaseDir().virtualFile!!)
    }

    fun testGitExecutable(project: Project): Boolean {
        //val settings = GitVcsApplicationSettings.getInstance()
        val executable = GitExecutableManager.getInstance().pathToGit
        val version: GitVersion
        version = try {
            GitExecutableManager.getInstance().identifyVersion(executable)
        } catch (e: java.lang.Exception) {
            showErrorDialog(project, "Cannot find git executable.", "Cannot Find Git")
            return false
        }
        if (!version.isSupported) {
            showErrorDialog(project, "Your version of git is not supported.", "Cannot Find Git")
            return false
        }
        return true
    }

    fun findGitLabRemoteUrl(repository: GitRepository): String? {
        val remote = findGitLabRemote(repository) ?: return null
        return remote.getSecond()
    }

    fun currentGitlabServer(gitRepository: GitRepository): GitlabServer? {
        for (gitRemote: GitRemote in gitRepository.remotes) {
            for (remoteUrl: String in gitRemote.urls) {
                for (server: GitlabServer in gitlabServers) {
                    if (remoteUrl.contains(server.repositoryUrl)) {
                        return server
                    }
                }
            }
        }
        return null
    }

    fun getGitlabServers(): Collection<GitlabServer?>? {
        return gitlabServers
    }

    fun findGitLabRemote(repository: GitRepository): Pair<GitRemote, String>? {
        for (gitRemote in repository.remotes) {
            for (remoteUrl in gitRemote.urls) {
                val repo = currentGitlabServer(repository)
                if (repo != null && remoteUrl.contains(repo.repositoryUrl)) {
                    return Pair.create(gitRemote, gitRemote.name)
                }
            }
        }
        return null
    }

    fun runGitCommand() {
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().runReadAction {
                // do whatever you need to do
            }
        }
    }
}
