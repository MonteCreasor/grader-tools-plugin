package edu.vanderbilt.grader.tools.unused

import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoResult
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableConvertor
import com.intellij.util.containers.Convertor
import java.io.IOException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object GitUtils {
    fun <T> computeValueInModal(
        project: Project,
        caption: String,
        task: ThrowableConvertor<ProgressIndicator?, T, IOException?>
    ): T {
        val dataRef = Ref<T>()
        val exceptionRef = Ref<Throwable>()
        ProgressManager.getInstance().run(object : Task.Modal(project, caption, true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    dataRef.set(task.convert(indicator))
                } catch (e: Throwable) {
                    exceptionRef.set(e)
                }
            }
        })
        if (!exceptionRef.isNull) {
            val e = exceptionRef.get()
            if (e is IOException) {
                throw e
            }
            if (e is RuntimeException) {
                throw e
            }
            if (e is Error) {
                throw e
            }
            throw RuntimeException(e)
        }
        return dataRef.get()
    }

    fun <T> computeValueInModal(
        project: Project,
        caption: String,
        task: Convertor<ProgressIndicator?, T>
    ): T {
        return computeValueInModal(
            project,
            caption,
            true,
            task
        )
    }

    fun <T> computeValueInModal(
        project: Project,
        caption: String,
        canBeCancelled: Boolean,
        task: Convertor<ProgressIndicator?, T>
    ): T {
        val dataRef = Ref<T>()
        val exceptionRef = Ref<Throwable>()
        ProgressManager.getInstance()
            .run(object : Task.Modal(project, caption, canBeCancelled) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        dataRef.set(task.convert(indicator))
                    } catch (e: Throwable) {
                        exceptionRef.set(e)
                    }
                }
            })
        if (!exceptionRef.isNull) {
            val e = exceptionRef.get()
            if (e is RuntimeException) {
                throw e
            }
            if (e is Error) {
                throw e
            }
            throw RuntimeException(e)
        }
        return dataRef.get()
    }

    @Throws(IOException::class)
    fun <T> runInterruptable(
        indicator: ProgressIndicator,
        task: ThrowableComputable<T, IOException?>
    ): T {
        var future: ScheduledFuture<*>? = null
        return try {
            val thread = Thread.currentThread()
            future =
                addCancellationListener(indicator, thread)
            task.compute()
        } finally {
            future?.cancel(true)
            Thread.interrupted()
        }
    }

    private fun addCancellationListener(
        indicator: ProgressIndicator,
        thread: Thread
    ): ScheduledFuture<*> {
        return addCancellationListener(Runnable {
            if (indicator.isCanceled) {
                thread.interrupt()
            }
        })
    }

    private fun addCancellationListener(run: Runnable): ScheduledFuture<*> {
        return JobScheduler.getScheduler().scheduleWithFixedDelay(run, 1000, 300, TimeUnit.MILLISECONDS)
    }

    @YesNoResult
    fun showYesNoDialog(
        project: Project?,
        title: String,
        message: String
    ): Boolean {
        return Messages.YES == Messages.showYesNoDialog(
            project,
            message,
            title,
            Messages.getQuestionIcon()
        )
    }
}
