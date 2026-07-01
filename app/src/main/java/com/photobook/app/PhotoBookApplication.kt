package com.photobook.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import com.photobook.app.util.LocalDiagnostics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class PhotoBookApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(imageLoader)

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
