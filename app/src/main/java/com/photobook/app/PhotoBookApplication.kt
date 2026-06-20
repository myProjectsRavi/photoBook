package com.photobook.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhotoBookApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(imageLoader)

        // Install a global uncaught exception handler that prevents the app from closing
        // on non-fatal background exceptions (e.g. background tasks or image prefetching),
        // but always crashes properly on main thread errors to prevent permanent ANR freezes.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isMainThread = thread == android.os.Looper.getMainLooper().thread
            val isFatal = throwable is VirtualMachineError || throwable is LinkageError || throwable is AssertionError
            if (isMainThread || isFatal) {
                defaultHandler?.uncaughtException(thread, throwable)
            } else {
                // Log silently and attempt to continue background execution
                android.util.Log.e("PhotoBook", "Uncaught non-fatal exception on background thread ${thread.name}", throwable)
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
