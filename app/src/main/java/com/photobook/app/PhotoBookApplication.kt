package com.photobook.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import com.photobook.app.feature.editor.EditorOutputPublisher
import com.photobook.app.feature.vault.VaultService
import com.photobook.app.util.LocalDiagnostics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

@HiltAndroidApp
class PhotoBookApplication : Application(), Configuration.Provider {

    private val applicationIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(imageLoader)

        // Vault previews are temporary plaintext. Remove leftovers from abrupt process
        // termination before protected UI can be reopened.
        applicationIoScope.launch {
            try {
                VaultService(this@PhotoBookApplication).clearStalePreviewCacheAtStartup()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                LocalDiagnostics.record(
                    context = this@PhotoBookApplication,
                    area = "vault-preview-cleanup",
                    message = "Unable to clear stale Vault previews",
                    throwable = error,
                )
            }
        }

        // Recover only app-owned interrupted editor publications recorded in the
        // private operation journal. This runs off Main and never scans/deletes
        // unrelated gallery rows.
        applicationIoScope.launch {
            try {
                EditorOutputPublisher(this@PhotoBookApplication).recoverInterruptedOutputs()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                LocalDiagnostics.record(
                    context = this@PhotoBookApplication,
                    area = "editor-publication-recovery",
                    message = "Unable to reconcile interrupted editor publication",
                    throwable = error,
                )
            }
        }

        // Keep crash diagnostics local-only, then delegate to Android's normal crash path.
        // Swallowing background crashes hides indexing/database/file bugs and makes failures
        // impossible to repair without cloud crash reporting.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LocalDiagnostics.record(
                context = this,
                area = "uncaught-${thread.name}",
                message = "Unhandled exception",
                throwable = throwable,
            )
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(10)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val vaultService = VaultService(this)
        val vaultPreviewGeneration = vaultService.invalidatePreviewCache()
        vaultService.schedulePreviewCacheCleanup(vaultPreviewGeneration)

        val cache = imageLoader.memoryCache ?: return
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                cache.clear()
            }

            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                cache.clear()
            }

            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                cache.clear()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
