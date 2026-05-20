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
        // on non-fatal exceptions (e.g. rare Compose layout crashes, OOM in background threads).
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Let the system handle truly fatal errors (OOM on main thread, StackOverflow)
            val isFatal = throwable is OutOfMemoryError && thread == android.os.Looper.getMainLooper().thread
            if (isFatal) {
                defaultHandler?.uncaughtException(thread, throwable)
            } else {
                // Log silently and attempt to continue — prevents random closes
                android.util.Log.e("PhotoBook", "Uncaught exception on ${thread.name}", throwable)
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
