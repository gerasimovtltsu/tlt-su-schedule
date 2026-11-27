package ru.tltsu.schedule.update

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import ru.tltsu.schedule.R

internal object UpdateManager {
    private const val INTERVAL = 86_400_000L
    private const val CHANNEL = "app_updates"
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    private fun storage(context: Context) =
        context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    fun installed(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    fun check(activity: Activity, manual: Boolean = false) {
        val context = activity.applicationContext
        if (!manual && !due(context)) return
        if (!running.compareAndSet(false, true)) {
            if (manual) Toast.makeText(activity, "Проверка уже выполняется", Toast.LENGTH_SHORT).show()
            return
        }
        if (manual) Toast.makeText(activity, "Проверяем обновления…", Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val result = runCatching { load(context) }
                main.post {
                    try {
                    if (activity.isFinishing || activity.isDestroyed) {
                        result.getOrNull()?.let { notifyIfNew(context, it) }
                        return@post
                    }
                    result.onSuccess { release ->
                        val newer = ReleaseVersion.newer(release.version, installed(context))
                        if (newer || manual) show(activity, release, newer)
                    }.onFailure {
                        if (manual) Toast.makeText(activity, "Не удалось проверить обновления GitHub", Toast.LENGTH_LONG).show()
                    }
                    } finally { running.set(false) }
                }
            } catch (e: Exception) {
                running.set(false)
                android.util.Log.w("UpdateManager", "Update check failed", e)
            }
        }
    }

    fun enqueueBackground(context: Context) {
        val app = context.applicationContext
        executor.execute { checkInBackground(app) }
    }

    /** Called by the existing periodic schedule job; no additional background service. */
    fun checkInBackground(context: Context) {
        if (!due(context) || !running.compareAndSet(false, true)) return
        try {
            runCatching { load(context) }.getOrNull()?.let { notifyIfNew(context, it) }
        } finally {
            running.set(false)
        }
    }

    private fun due(context: Context): Boolean {
        val last = storage(context).getLong("last_check", 0)
        val now = System.currentTimeMillis()
        return last > now || now - last >= INTERVAL
    }

    private fun load(context: Context): AppRelease {
        val release = GitHubReleaseApi.latest()
        storage(context).edit().putLong("last_check", System.currentTimeMillis()).apply()
        return release
    }

    private fun show(activity: Activity, release: AppRelease, newer: Boolean) {
        val notes = release.notes.trim().ifBlank { "Описание изменений для этого релиза не опубликовано." }
        val palette = ru.tltsu.schedule.ui.AppPalette.current(activity)
        fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
        val content = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(16))
        }
        fun label(value: CharSequence, size: Float, color: Int, bold: Boolean = false) =
            android.widget.TextView(activity).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            }
        content.addView(label("Установлено: " + installed(activity), 14f, palette.muted))
        content.addView(label("Последний релиз: " + release.version, 14f, palette.muted))
        content.addView(label(release.tag, 12f, palette.muted))
        content.addView(label("Что нового", 17f, palette.text, true).apply {
            setPadding(0, dp(18), 0, dp(8))
        })
        // GitHub release notes are Markdown; preserve all text and paragraph breaks.
        // Do not execute HTML or interpret remote content as a WebView page.
        val readable = notes.lineSequence().joinToString("\n") { line ->
            line.replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^\\s*[-*]\\s+"), "• ")
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        }
        content.addView(label(readable, 14f, palette.text).apply {
            setTextIsSelectable(true)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (newer) "Доступно обновление" else "Установлена актуальная версия")
            .setView(android.widget.ScrollView(activity).apply {
                addView(content)
                isVerticalScrollBarEnabled = true
            })
            .setNeutralButton("GitHub") { _, _ ->
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse(GitHubReleaseApi.RELEASES + "/tag/" + Uri.encode(release.tag)))) }
            }
        if (newer) {
            dialog.setPositiveButton("Скачать APK") { _, _ -> ApkDownloader.start(activity, release) }
                .setNegativeButton("Позже", null)
        } else {
            dialog.setPositiveButton("Закрыть", null)
        }
        dialog.show()
    }

    private fun notifyIfNew(context: Context, release: AppRelease) {
        if (!ReleaseVersion.newer(release.version, installed(context))) return
        if (storage(context).getString("notified_tag", "") == release.tag) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Обновления приложения", NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val intent = Intent(context, ru.tltsu.schedule.MainActivity::class.java).putExtra("check_update", true)
        val pending = PendingIntent.getActivity(context, 1701, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Доступно обновление Расписание ТГУ")
            .setContentText("Версия " + release.version + " · Скачать APK")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            manager.notify(1701, notification)
            storage(context).edit().putString("notified_tag", release.tag).apply()
        }
    }
}
