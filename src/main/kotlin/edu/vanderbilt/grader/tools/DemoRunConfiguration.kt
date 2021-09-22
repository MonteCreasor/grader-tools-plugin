package edu.vanderbilt.grader.tools

import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.execution.Executor


class DemoRunConfiguration(project: Project, factory: ConfigurationFactory, name: String)
    : RunConfigurationBase<Any?>(project, factory, name) {
    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        TODO()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return null
    }

    override fun checkConfiguration() {
    }
}