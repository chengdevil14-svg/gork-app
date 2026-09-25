package com.lkcheng.savings

import android.app.Application
import android.os.Build
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val text = "版本 ${BuildConfigInfo.version(this)} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
                    android.util.Log.getStackTraceString(e)
                File(filesDir, "crash.txt").writeText(text)
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }
}

object BuildConfigInfo {
    fun version(app: Application): String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
