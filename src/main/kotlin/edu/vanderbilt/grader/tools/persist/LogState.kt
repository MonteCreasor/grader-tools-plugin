package edu.vanderbilt.grader.tools.persist

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "LogState",
    storages = [Storage(value = "grader.log")]
)
class LogState : PersistentStateComponent<LogState> {
    var text = ""

    override fun getState(): LogState {
        return this
    }

    override fun loadState(state: LogState) {
        text = state.text
    }
}