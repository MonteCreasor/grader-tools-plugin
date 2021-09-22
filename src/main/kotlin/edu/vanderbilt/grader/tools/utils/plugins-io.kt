package edu.vanderbilt.grader.tools.utils

//import com.intellij.openapi.application.ApplicationManager
//import com.intellij.openapi.command.CommandProcessor
//import com.intellij.openapi.vfs.VfsUtil
//import liveplugin.LivePluginAppComponent.Companion.livePluginId
//import liveplugin.findFileByUrl
//import java.io.IOException
//
//private const val requestor = livePluginId
//
//fun createFile(parentPath: String, fileName: String, text: String) {
//    runIOAction("createFile") {
//        val parentFolder = VfsUtil.createDirectoryIfMissing(parentPath)
//            ?: throw IOException("Failed to create folder $parentPath")
//        if (parentFolder.findChild(fileName) == null) {
//            val file = parentFolder.createChildData(requestor, fileName)
//            VfsUtil.saveText(file, text)
//        }
//    }
//}
//
//fun delete(filePath: String) {
//    runIOAction("delete") {
//        val file = filePath.findFileByUrl() ?: throw IOException("Failed to find file $filePath")
//        file.delete(requestor)
//    }
//}
//
//private fun runIOAction(actionName: String, f: () -> Unit) {
//    var exception: IOException? = null
//    ApplicationManager.getApplication().runWriteAction {
//        CommandProcessor.getInstance().executeCommand(null, {
//            try {
//                f()
//            } catch (e: IOException) {
//                exception = e
//            }
//        }, actionName, livePluginId)
//    }
//
//    if (exception != null) throw exception!!
//}