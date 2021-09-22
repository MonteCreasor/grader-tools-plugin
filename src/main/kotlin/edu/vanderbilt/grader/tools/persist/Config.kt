package edu.vanderbilt.grader.tools.persist

import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil
import edu.vanderbilt.grader.tools.Constants
import edu.vanderbilt.grader.tools.Constants.DEFAULT_INSTALL_DIR_NAME
import edu.vanderbilt.grader.tools.Constants.DEFAULT_REPO_DIR_NAME
import edu.vanderbilt.grader.tools.Constants.DEFAULT_SAVE_FILE_NAME
import edu.vanderbilt.grader.tools.Constants.GRADLE_COMMAND
import edu.vanderbilt.grader.tools.persist.Config.ConfigurationChange.ConfigEvent
import edu.vanderbilt.grader.tools.ui.ConfigView
import edu.vanderbilt.grader.tools.ui.ConfigView.Companion.focusKey
import edu.vanderbilt.grader.tools.ui.Repo
import edu.vanderbilt.grader.tools.utils.*
import java.awt.BorderLayout
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URLEncoder
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel


/** Update this value whenever the enum class changes */
const val CONFIG_VERSION = 1

@State(
    name = "edu.vanderbilt.grader.tools.persist.config.${CONFIG_VERSION}",
    storages = [Storage(value = "grader-config.xml")]
)
class Config : PersistentStateComponent<Config> {
    enum class Key(val description: String, val default: String, val visible: Boolean = true) {
        GitSite("Git server URL", "gitlab.com"),
        GitUser("Username", "douglascraigschmidt"),
        GitToken("Token", ""),
        SdkDir("Android SDK location", ""),
        JdkDir("JDK location", ""),
        InstallDir("Directory containing grader installation files", DEFAULT_INSTALL_DIR_NAME),
        SavedReposFileName("Default file name for save operation", DEFAULT_SAVE_FILE_NAME),
        CommitMessage("Commit message", "{UserCol}:{RepoCol}:{ProjCol} graded by {GitUser}."),
        ShowCommitMessage("Show commit message", "false"),
        MarksOffPerFailedTest("Percent deduction for each failed test", "5"),
        RepoRoot("Location for cloned repos (relative to project root).", DEFAULT_REPO_DIR_NAME),
        Verbose("Verbose output", "false", false),
        GraderDir("Grader directory name", "grader", false),
        Https("HTTP Git locator prefix", "https://{GitUser}:{GitToken}@{GitSite}/", false),
        GitUserHyperLink("Hyperlink prefix", "https:/{GitSite}/{GitUser}/", false),
        Ssh("SSH Git locator prefix", "git@{GitSite}:", false),
        GitLocator("Git locator prefix", "{Https}", false),
        Clone(
            "Clone selected projects",
            "git submodule add --force '{GitLocator}{UserCol}/{RepoCol}.git' {DirCol}'",
            false
        ),
        Delete(
            "Delete selected projects",
            "git submodule deinit -f '{DirCol}';" +
                    "git rm -rf '{DirCol}'",
            false
        ),
        Grade(
            "Grade selected projects",
            "{DirCol}/$GRADLE_COMMAND -Psdk.dir='{sdkDir}' -Dorg.gradle.jvmargs=-Xmx512M",
            false
        ),
        Commit("Commit selected projects", "git commit -a -m'{CommitMessage}'", false),
        Add("Add untracked files for selected repositories", "git add --all", false),
        Revert(
            "Revert all tracked and untracked files in selected projects",
            "git reset --hard HEAD; git clean -f -d",
            false
        ),
        Pull("Pull selected projects", "git pull", false),
        Push("Push selected projects", "git push", false),
        Status("Show status of selected projects", "git status -s", false),
        Diff("Show difference between remote and local selected repositories", "git fetch --dry-run", false),
        Run("Run bash commands in the root directory of selected projects", "pwd; ls -l", false),

        // Must match column initial ordering in RepoView JTable.
        SelCol("Repository table selection column", "{0}", false),
        UserCol("Repository table user column", "{1}", false),
        RepoCol("Repository table repository column", "{2}", false),
        DateCol("Repository table date column", "{3}", false),
        ProjCol("Repository table project column", "{4}", false),
        DirCol("Repository table directory column", "{5}", false),
        StatusCol("Repository table status column", "{6}", false),
        GradeCol("Repository table grade column", "{7}", false);
    }

    private val default = Key.values().map { it to it.default }.toMap()

    //TODO
//    @Transient
//    @com.intellij.util.xmlb.annotations.Transient
//    @com.fasterxml.jackson.annotation.JsonIgnore
//    lateinit var project: Project

    @JvmField
    var configMap = default.toMutableMap()

    @JvmField
    var firstRun = true

    @JvmField
    var selectedTabIndex = 0

    @JvmField
    var configFilePath: String? = null

    @JvmField
    var deleteTableEntries: Boolean = false

    @JvmField
    val logFilePath: String? = null

    val isDefaultConfig: Boolean
        get() = configMap != default

    override fun getState(): Config = this
    override fun loadState(state: Config) {
        try {
            XmlSerializerUtil.copyBean(state, this)
            default.forEach { entry ->
                // Add all key/value pairs that exist in the default map
                // but do not exist in the saved configuration.
                if (!configMap.containsKey(entry.key)) {
                    configMap[entry.key] = entry.value
                }
                // Remove all key/value pairs that exist in the saved
                // configuration but no longer exist in the default
                // key map.
            }
            configMap.forEach { entry ->
                // Remove all key/value pairs that exist in the saved
                // configuration but no longer exist in the default
                // key map.
                @Suppress("SENSELESS_COMPARISON")
                if (entry.key != null && !default.containsKey(entry.key)) {
                    configMap.remove(entry.key)
                }
            }
        } catch (t: Throwable) {
            notify("Unable to load last saved configuration.", type = ERROR)
        }
    }

    fun loadConfigFromFile(project: Project, file: File) {
        try {
            val oldConfig = configMap
            configMap = file.readLines().map {
                it.split("=")
            }.filter { list ->
                list.size in 1..2 && Key.values().any { it.name == list[0] }
            }.map { list ->
                Pair(Key.valueOf(list[0]), list[1])
            }.toMap().toMutableMap()

            configMap.filterNot {
                oldConfig[it.key] == it.value
            }.forEach {
                ConfigurationChange.post(project, ConfigEvent(it.key, oldConfig[it.key], it.value))
            }
        } catch (t: Throwable) {
            notify("Unable to load configuration (version too old).", type = ERROR)
        }
    }

    fun saveConfigToFile(file: File) {
        val text = configMap.map {
            "${it.key}=${it.value}\n"
        }.reduce { acc, next ->
            "$acc\n$next"
        }
        file.writeText("$FILE_COMMENT\n$text")
        configFilePath = file.path
    }

    fun resetToDefaults(project: Project) {
        val oldConfig = configMap
        configMap = default.toMutableMap()
        selectedTabIndex = 0
        configFilePath = null
        firstRun = true

        configMap.filterNot {
            oldConfig[it.key] == it.value
        }.forEach {
            ConfigurationChange.post(project, ConfigEvent(it.key, oldConfig[it.key], it.value))
        }
    }

    interface ConfigurationChange {
        data class ConfigEvent(val key: Key, val oldValue: String?, val newValue: String?)

        fun onEvent(event: ConfigEvent)

        companion object {
            var TOPIC = Topic.create(
                Constants.CONFIG_CHANGE_BUS_ID, ConfigurationChange::class.java,
                Topic.BroadcastDirection.TO_PARENT
            )

            fun subscribe(
                project: Project,
                handler: (key: Key, oldValue: String?, newValue: String?) -> Unit
            ) {
                project.messageBus.connect(project)
                    .subscribe(TOPIC, object : ConfigurationChange {
                        override fun onEvent(event: ConfigEvent) {
                            handler(event.key, event.oldValue, event.newValue)
                        }
                    })
            }

            fun subscribe(
                project: Project,
                disposable: Disposable,
                key: Key,
                handler: (oldValue: String?, newValue: String?) -> Unit
            ) {
                project.messageBus.connect(disposable)
                    .subscribe(TOPIC, object : ConfigurationChange {
                        override fun onEvent(event: ConfigEvent) {
                            check(event.oldValue != event.newValue) {
                                "key oldvalue and newvalue equal (${event.oldValue})"
                            }
                            if (event.key == key) {
                                handler(event.oldValue, event.newValue)
                            }
                        }
                    })
            }

            internal fun post(project: Project, event: ConfigEvent) {
                project.messageBus.syncPublisher(TOPIC).onEvent(event)
            }
        }
    }

    operator fun get(key: Key): String = configMap[key]!!
    operator fun set(key: Key, value: String) {
        if (configMap[key] != value) {
            //val oldValue = configMap[key]
            configMap[key] = value
            //TODO: ConfigurationChange.post(project, ConfigEvent(key, oldValue, value))
        }
    }

    /**
     * Executes formatted command(s) substituting {[i]} with argList[i]
     * and all other {key} markers with config[key] values.
     */
    fun buildCmdList(key: Key, repo: Repo): List<List<String>> {
        val format = this[key]
        val map = mutableMapOf<String, String>()
        map.putAll(repo.patternMap)
        map.putAll(toMap())
        return parseCmd(format, map)
    }

    private fun toMap(): Map<String, String> =
        configMap.map {
            val value = when (it.key) {
                Key.GitToken, Key.GitUser -> {
                    URLEncoder.encode(it.value, "UTF-8")
                }
                else -> it.value
            }

            it.key.name to value
        }.toMap()

    /**
     * Parses command string into list of commands where each {key}
     * pattern is replaced by it's associated map[key] value.
     */
    private fun parseCmd(format: String, propMap: Map<String, String> = emptyMap()): List<List<String>> {
        return resolve(format, propMap).split(";").filterNot { it.isBlank() }.map { cmd ->
            // Splits command into file and non-file parts
            // where each file must be enclosed in single quotes.
            val parts = cmd
                .split("'")
                .filterNot { it.isBlank() }
                .map { it.trim() }
            val args = parts[0].split("[\\s]+".toRegex()).toMutableList()
            val rest = parts.takeLast(parts.size - 1)
            args.apply { addAll(rest) }.toList()
        }
    }

    private fun resolve(format: String, map: Map<String, String> = emptyMap()): String {
        val pattern = """.*(\{[^}]+}).*""".toRegex()
        var string = format
        while (pattern.matches(string)) {
            val matchResult = pattern.matchEntire(string)
            val match = matchResult?.groupValues?.get(1) ?: break
            val name = match.substring(1, match.lastIndex)
            val value = map[name] ?: try {
                this[Key.valueOf(name)]
            } catch (e: Exception) {
                throw Exception("$string: configuration property '$name' is not defined.")
            }
            string = string.replace(match, value)
        }
        return string
    }

    val remoteGitUrl: String
        get() = resolve(get(Key.Https))

    fun hidePasswords(input: String?): String = input?.hidePassword(this[Key.GitToken]) ?: ""

    /**
     * Supports a fixed replacement string [length] of 1..20. A [length] of 0
     * will result in a mask with same length as each found password.
     */
    fun String.hidePassword(match: String, c: Char = '*', length: Int = 4): String =
        when {
            debug -> {
                this
            }
            match.isNotBlank() && isNotBlank() -> {
                check(length in 0..20) { "Limit must be in range 1..20" }
                val count = if (length == 0) match.length else length
                replace(match, c.toString().repeat(count))
            }
            else -> {
                this
            }
        }

    fun validateGitUser(project: Project): String? {
        val user = this[Key.GitUser]
        return if (user.isBlank()) {
            notifyErrorAndFix(project, Key.GitUser) {
                "Git commands require a Git user name to be set."
            }
            null
        } else {
            user
        }
    }

    fun validateGitToken(project: Project): String? {
        val token = this[Key.GitToken]
        return if (token.isBlank()) {
            notifyErrorAndFix(project, Key.GitToken) {
                "Git commands require a Git token to be set."
            }
            null
        } else {
            token
        }
    }

    fun getJdkDir(project: Project): String? {
        // First try config settings
        var dir = this[Key.JdkDir]
        if (isValidJdkDir(dir)) {
            return dir
        }

        if (dir.isNotBlank()) {
            notifyErrorAndFix(project, Key.JdkDir) {
                "Directory $dir is not a valid JDK location."
            }
        }

        dir = jdkEnvVar.getenv ?: ""

        if (isValidJdkDir(dir)) {
            this[Key.JdkDir] = dir
            return dir
        }

        if (dir.isBlank()) {
            notifyErrorAndFix(project, Key.JdkDir) {
                "JDK directory has not been set."
            }
            return null
        }

        return dir
    }

    fun getSdkDir(project: Project): String? {
        // First try config settings
        var sdkDir = this[Key.SdkDir]
        if (isValidSdkDir(sdkDir)) {
            return sdkDir
        }

        if (sdkDir.isNotBlank()) {
            notifyErrorAndFix(project, Key.SdkDir) {
                "Directory $sdkDir is not a valid SDK location."
            }
        }

        // Now try local.properties.
        sdkDir = try {
            val file = File(project.dir.asFile, localProperties)
            // Requires `java-gradle-plugin` in build.gradle plugins block
            val props = loadProperties(file)
            props.getProperty("sdk.dir") ?: ""
        } catch (t: Throwable) {
            ""
        }

        if (isValidSdkDir(sdkDir)) {
            this[Key.SdkDir] = sdkDir
            return sdkDir
        }

        sdkDir = sdkEnvVar.getenv ?: ""

        if (isValidSdkDir(sdkDir)) {
            this[Key.SdkDir] = sdkDir
            return sdkDir
        }

        if (sdkDir.isBlank()) {
            notifyErrorAndFix(project, Key.SdkDir) {
                "SDK directory has not been set."
            }
            return null
        }

        return sdkDir
    }

    private fun loadProperties(propertyFile: File): Properties {
        return try {
            val inputStream = FileInputStream(propertyFile)
            inputStream.use {
                Properties().apply { load(it) }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

    }

    private fun checkInstallPath(project: Project, path: String = "INSTALL"): File? {
        var file = File(path)
        val projPath = project.dir.asFile.canonicalPath
        if (!file.isAbsolute) {
            file = File("$projPath/${file.path}")
        } else if (!file.canonicalPath.startsWith(projPath)) {
            project.error(
                "Install directory must be a relative path.",
                fixAction(project, Key.InstallDir)
            )
            return null
        }

        return when {
            file.isDirectory || file.mkdirs() -> file
            else -> {
                project.error(
                    "Unable to create install directory $file.",
                    fixAction(project, Key.InstallDir)
                )
                null
            }
        }
    }

    private fun checkRepoRootPath(project: Project, path: String = DEFAULT_REPO_DIR_NAME): File? {
        var file = File(path)
        val projPath = project.dir.asFile.canonicalPath
        if (!file.isAbsolute) {
            file = File("$projPath/${file.path}")
        } else if (!file.canonicalPath.startsWith(projPath)) {
            project.error(
                "Repository directory must be a relative path.",
                fixAction(project, Key.InstallDir)
            )
            return null
        }

        return when {
            file.isDirectory || file.mkdirs() -> file
            else -> {
                project.error(
                    "Unable to create repository directory $file.",
                    fixAction(project, Key.InstallDir)
                )
                null
            }
        }
    }

    fun getInstallDir(project: Project, path: String = this[Key.InstallDir]): File? {
        val file = checkInstallPath(project, path) ?: return null
        return project.dir.asFile.toPath().relativize(file.toPath()).toFile()
    }

    fun getRepoRootDir(project: Project, path: String = this[Key.RepoRoot]): File? {
        val file = checkRepoRootPath(project, path) ?: return null
        return project.dir.asFile.toPath().relativize(file.toPath()).toFile()
    }

    fun isValidSdkDir(dir: String): Boolean =
        File(dir).isDirectory && File("${dir}/platform-tools").isDirectory

    fun isValidJdkDir(dir: String): Boolean =
        File(dir).isDirectory && File("${dir}/bin").isDirectory

    fun notifyErrorAndFix(project: Project, key: Key, message: () -> String) {
        project.error(message(), fixAction(project, key))
    }

    fun notifyWarnAndFix(project: Project, key: Key, message: () -> String) {
        project.warn(message(), fixAction(project, key))
    }

    fun notifyInfoAndFix(project: Project, key: Key, message: () -> String) {
        project.info(message(), fixAction(project, key))
    }

    fun notifyAndFix(project: Project, key: Key, message: () -> String) {
        project.info(message(), fixAction(project, key))
    }

    fun fixAction(project: Project, key: Key) = simpleAction("Fix") {
        showUI(project, key)
    }

    fun showUI(project: Project, focusKey: Key? = null) {
        ConfigDialog(project, focusKey).show()
    }

    class ConfigDialog(project: Project, private var key: Key? = null) : DialogWrapper(project) {
        private val panel: JPanel
        private val configView = ConfigView(project).apply { focusKey = key }

        init {
            title = "Vanderbilt Grader Settings"
            panel = JPanel(BorderLayout())
            panel.add(configView.component, BorderLayout.CENTER)
            super.init()
        }

        private fun updateSize() {
            setSize(900, 400)
        }

        override fun createCenterPanel(): JComponent {
            return panel
        }

        override fun getPreferredFocusedComponent(): JComponent {
            updateSize()
            return configView.table
        }
    }

    companion object {
        const val DEFAULT_CONFIG_FILE_NAME = "CONFIG.txt"
        const val DEFAULT_LOG_FILE_NAME = "grader.log"
        var debug: Boolean = false

        val FILE_COMMENT = """
            # List of properties used by Vanderbilt grader-tools plugin where
            # each entry must be in the form
            #
            #     <key> = <value>
            #
            # Lines beginning with a '#' will be ignored.
            #
            """.trimIndent()
    }
}
