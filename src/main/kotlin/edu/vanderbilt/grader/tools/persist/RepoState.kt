package edu.vanderbilt.grader.tools.persist

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import edu.vanderbilt.grader.tools.ui.Repo

@State(
    name = "RepoState",
    storages = [Storage(value = "grader-repos.xml")]
)
class RepoState : PersistentStateComponent<RepoState> {
    var repos: MutableList<Repo> = mutableListOf()

    override fun getState(): RepoState? {
        println("saving ${repos.count()} repositories -> $repos")
        return this
    }

    override fun loadState(state: RepoState) {
        repos.clear()
        repos.addAll(state.repos)
        println("loading ${state.repos.count()} repositories -> $repos")
    }
}