package edu.vanderbilt.grader.tools.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInManager.PROJECT
import com.intellij.ide.SelectInManager.findSelectInTarget
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import edu.vanderbilt.grader.tools.ui.Repo
import java.io.File

///**
// * Converts passed Json [json] string to an instance of [clazz]
// */
//inline fun <reified T> fromJson(json: String, clazz: Class<T>): T? =
//    Gson().fromJson(json, clazz)

//TODO: put these in Constants object class.
val runnerTask = "runAutograder"
val installerDirName = "installer"
val graderDirName = "grader"
val submissionDirName = "AUTOGRADER_SUBMISSION"
val consolidatedLog = "consolidated.log"
val unitTestFeedback = "unit-test-feedback.txt"
val summaryFileName = "SUMMARY"
val instrumentedTestFileName = "test.apk"
val unitTestFileName = "test.jar"
val sdkEnvVar = "ANDROID_SDK_ROOT"
val jdkEnvVar = "JAVA_HOME"
val localProperties = "local.properties"
val linuxGradleCmd = "gradlew" // Linux and iOS
val windowsGradleCmd = "gradlew.bat"
val gradeRegex = """[\s]*([0-9]+)[\s]*/[\s]*([0-9]+)[\s]*(.*)""".toRegex()

val VirtualFile.asFile: File
    get() = File(canonicalPath!!)

val String.virtualFile: VirtualFile?
    get() = LocalFileSystem.getInstance().findFileByPath(this)

val String.isComment: Boolean
    get() = "^\\s*#.*$".toRegex().matches(this)

val Project.dir: VirtualFile
    get() = LocalFileSystem.getInstance().findFileByPath(basePath.toString())!!

fun readLines(virtualFile: VirtualFile): List<String> {
    val file = File(virtualFile.canonicalPath.toString())
    return file.readLines().filterNot {
        it.isEmpty() || it.isComment
    }.map {
        it.trim()
    }
}

val String.getenv: String?
    get() = System.getenv(this)
val isWindows: Boolean
    get() = System.getProperty("os.name").toLowerCase().contains("windows")

val File.isGradleProject: Boolean
    get() = isDirectory
            && File(this, "build.gradle").isFile
            && File(this, "settings.gradle").isFile

fun getResolveGradleScriptName(): String =
    if (isWindows) windowsGradleCmd else "./${linuxGradleCmd}"

fun writeText(virtualFile: VirtualFile, text: String) {
    File(virtualFile.canonicalPath.toString()).writeText(text)
}

fun chooseSaveFile(
    project: Project,
    title: String,
    defaultPath: String,
    vararg extensions: String
): File? {
    val descriptor = FileSaverDescriptor(title, "", *extensions).apply {
        isHideIgnored = true
        isForcedToUseIdeaFileChooser = true
    }
    FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project).also { dialog ->
        val file = File(defaultPath)
        val dir = if (file.parentFile?.isDirectory == true) file.parentFile else project.dir.asFile
        val name = file.name
        val virtualFile = VirtualFileWrapper(dir).virtualFile
        return dialog.save(virtualFile, name)?.file
    }
}

/**
 * Return typed object from Json (hides TypeToken<T>(){}.getType()).
 */
inline fun <reified T> String.fromJson(): T? =
    ObjectMapper().readValue(this, object : TypeReference<T>() {})

inline fun <reified T> T.toJson(): String {
    return ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(this)
}

fun File.navigateInProjectView(project: Project, requestFocus: Boolean): Boolean {
    return VirtualFileWrapper(this).virtualFile?.navigateInProjectView(project, requestFocus) == true
}

fun File.navigateInEditor(project: Project, requestFocus: Boolean): Boolean {
    return VirtualFileWrapper(this).virtualFile?.navigateInProjectView(project, requestFocus) == true
}

fun VirtualFile.navigateInProjectView(project: Project, requestFocus: Boolean): Boolean {
    val context = FileSelectInContext(project, this, null)
    val target = findSelectInTarget(PROJECT, project)
    return if (target?.canSelect(context) == true) {
        findSelectInTarget(PROJECT, project)?.selectIn(context, requestFocus)
        true
    } else {
        false
    }
}

fun chooseFile(project: Project, default: String? = null): VirtualFile? {
    val descriptor = createSingleFileDescriptor(PlainTextFileType.INSTANCE).apply {
        isHideIgnored = true
        isForcedToUseIdeaFileChooser = true
    }
    val file = default?.virtualFile ?: project.dir

    return FileChooser.chooseFile(descriptor, null, file)
}

fun chooseSaveFile(project: Project, title: String = "Save", default: String? = null): File? {
    val descriptor = FileSaverDescriptor(title, "").apply {
        isHideIgnored = true
        isForcedToUseIdeaFileChooser = true
    }
    FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project).also { dialog ->
        val file = default?.let { File(it) }
        val dir = file?.let { VirtualFileWrapper(it.parentFile).virtualFile } ?: project.dir
        return dialog.save(dir, file?.name)?.file
    }
}
