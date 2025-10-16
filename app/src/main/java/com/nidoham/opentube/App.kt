package com.nidoham.opentube

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import org.schabi.newpipe.DownloaderApp
import org.schabi.newpipe.util.ServiceHelper
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.system.exitProcess
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException
import io.reactivex.rxjava3.exceptions.UndeliverableException
import java.io.InterruptedIOException

/**
 * Main Application class for OpenTube.
 * Initializes global services and manages application-wide states.
 *
 * Licensed under GNU General Public License (GPL) version 3 or later.
 */
class App : Application() {

    companion object {
        private const val TAG = "App"
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_FIRST_RUN = "key_first_run"
        private const val MAX_CRASH_REPORTS = 10

        @Volatile
        private var INSTANCE: App? = null

        /**
         * Provides the singleton instance of the Application.
         *
         * @return App instance
         * @throws IllegalStateException if accessed before initialization
         */
        fun getInstance(): App {
            return INSTANCE ?: throw IllegalStateException("Application instance not initialized yet.")
        }

        /**
         * Provides the application context globally.
         *
         * @return Application context
         * @throws IllegalStateException if accessed before initialization
         */
        fun getContext(): Context {
            return INSTANCE?.applicationContext
                ?: throw IllegalStateException("Application context not initialized yet.")
        }
    }

    private lateinit var downloaderApp: DownloaderApp
    private var isAppFirstRun: Boolean = false

    private val preferences: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val crashDirectory: File by lazy {
        File(filesDir, "crashes").apply { mkdirs() }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        setupCrashHandler()
        setupRxJavaErrorHandler() // RxJava undeliverable exception handling

        runCatching {
            initializeApplication()
        }.onFailure { exception ->
            Log.e(TAG, "Error during application initialization", exception)
            throw exception
        }
    }

    /**
     * Initializes the core application components.
     */
    private fun initializeApplication() {
        downloaderApp = DownloaderApp(this)
        ServiceHelper.initServices(this)
        isAppFirstRun = checkAndMarkFirstRun()
        Log.d(TAG, "Application initialized successfully")
    }

    /**
     * Sets up the custom crash handler for the application.
     */
    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(CustomCrashHandler())
    }

    /**
     * Handles undeliverable RxJava exceptions globally.
     */
    private fun setupRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler { e ->
            when {
                e is UndeliverableException && e.cause is IOException -> return@setErrorHandler
                e is IOException || e is SocketException -> return@setErrorHandler // Network interruption
                e is InterruptedException -> return@setErrorHandler               // Thread interrupted
                e is UndeliverableException && e.cause is InterruptedIOException -> return@setErrorHandler
                else -> Log.e(TAG, "RxJava Undeliverable exception", e)
            }
        }
    }

    /**
     * Custom crash handler implementation using Kotlin features.
     */
    private inner class CustomCrashHandler : Thread.UncaughtExceptionHandler {
        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        override fun uncaughtException(thread: Thread, exception: Throwable) {
            runCatching {
                Log.e(TAG, "Uncaught exception in thread ${thread.name}", exception)

                val crashReport = generateCrashReport(thread, exception)
                saveCrashReport(crashReport)
                startDebugActivity(crashReport)

            }.onFailure { error ->
                Log.e(TAG, "Error in custom crash handler", error)
                defaultHandler?.uncaughtException(thread, exception)
                return
            }

            // Terminate the process
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }

        private fun generateCrashReport(thread: Thread, exception: Throwable): String {
            val stackTrace = StringWriter().apply {
                exception.printStackTrace(PrintWriter(this))
            }.toString()

            return buildString {
                appendLine("OpenTube Crash Report")
                appendLine("=====================")
                appendLine("Time: ${Date()}")
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: ${exception.javaClass.simpleName}")
                appendLine("Message: ${exception.message ?: "No message provided"}")
                appendLine("First Run: $isAppFirstRun")
                appendLine("App Version: ${getAppVersionName()}")
                appendLine("Android Version: ${android.os.Build.VERSION.RELEASE}")
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine()
                appendLine("Stack Trace:")
                appendLine("============")
                append(stackTrace)
            }
        }

        private fun getAppVersionName(): String {
            return runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull() ?: "Unknown"
        }

        private fun saveCrashReport(crashReport: String) {
            runCatching {
                val fileName = "crash_${System.currentTimeMillis()}.txt"
                val crashFile = File(crashDirectory, fileName)

                crashFile.writeText(crashReport)
                Log.d(TAG, "Crash report saved: ${crashFile.absolutePath}")

                cleanupOldCrashReports()

            }.onFailure { error ->
                Log.e(TAG, "Failed to save crash report", error)
            }
        }

        private fun cleanupOldCrashReports() {
            runCatching {
                crashDirectory.listFiles()?.let { files ->
                    if (files.size > MAX_CRASH_REPORTS) {
                        files.sortedBy { it.lastModified() }
                            .take(files.size - MAX_CRASH_REPORTS)
                            .forEach { it.delete() }
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to cleanup old crash reports", error)
            }
        }

        private fun startDebugActivity(crashReport: String) {
            runCatching {
                Class.forName("com.nidoham.opentube.error.DebugActivity")

                val intent = Intent(this@App, com.nidoham.opentube.error.DebugActivity::class.java).apply {
                    putExtra("CRASH_REPORT", crashReport)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                startActivity(intent)

            }.onFailure { error ->
                when (error) {
                    is ClassNotFoundException -> Log.d(TAG, "DebugActivity not found, skipping debug screen")
                    else -> Log.e(TAG, "Failed to start debug activity", error)
                }
            }
        }
    }

    /**
     * Checks if this is the application's first run after installation and marks it as completed.
     *
     * @return true if first run, false otherwise
     */
    private fun checkAndMarkFirstRun(): Boolean {
        val isFirstRun = preferences.getBoolean(KEY_FIRST_RUN, true)
        if (isFirstRun) {
            preferences.edit()
                .putBoolean(KEY_FIRST_RUN, false)
                .apply()
        }
        return isFirstRun
    }

    /**
     * Returns whether this is the first run of the app.
     *
     * @return true if first run, false otherwise
     */
    fun isFirstRun(): Boolean = isAppFirstRun

    /**
     * Gets all saved crash reports.
     *
     * @return Array of crash report files
     */
    fun getCrashReports(): Array<File> {
        return crashDirectory.takeIf { it.exists() }?.listFiles() ?: emptyArray()
    }

    /**
     * Clears all saved crash reports.
     */
    fun clearCrashReports() {
        runCatching {
            crashDirectory.listFiles()?.forEach { it.delete() }
        }.onFailure { error ->
            Log.e(TAG, "Failed to clear crash reports", error)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Clean up resources if necessary (rarely called)
    }

    /**
     * Override if handling disposed RxJava exceptions reporting is necessary.
     *
     * @return false by default
     */
    protected open fun isDisposedRxExceptionsReported(): Boolean = false
}