package com.jimmy.ytcar

import android.app.Application
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 記錄最近一次當掉的原因，下次開啟手機端 YtCar 時顯示在診斷區 */
class YtCarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val t = SimpleDateFormat("MM/dd HH:mm:ss", Locale.US).format(Date())
                val trace = e.stackTraceToString().lines().take(6).joinToString("\n")
                getSharedPreferences("crash", Context.MODE_PRIVATE).edit()
                    .putString("last", "$t [${thread.name}]\n$trace").commit()
            }
            previous?.uncaughtException(thread, e)
        }
    }

    companion object {
        fun lastCrash(context: Context): String? =
            context.getSharedPreferences("crash", Context.MODE_PRIVATE).getString("last", null)
    }
}
