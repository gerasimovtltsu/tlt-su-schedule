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
                    if (activity.isFinishing || activity.isDestroyed) {
                        result.getOrNull()?.let { notifyIfNew(context, it) }
                        return@post
                    }
                    result.onSuccess { release ->
                        if (ReleaseVersion.newer(release.version, installed(context))) {
                            show(activity, release)
                        } else if (manual) {
                            Toast.makeText(activity, "Установлена актуальная версия", Toast.LENGTH_LONG).show()
                        }
                    }.onFailure {
                        if (manual) Toast.makeText(activity, "Не удалось проверить обновления GitHub", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                running.set(false)
            }
        }
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

    private fun show(activity: Activity, release: AppRelease) {
        val notes = release.notes.trim().take(2500).ifBlank { "Описание изменений доступно на GitHub." }
        AlertDialog.Builder(activity)
            .setTitle("Доступно обновление")
            .setMessage("Установлено: " + installed(activity) + "\nНовая версия: " + release.version + "\n\nЧто нового:\n" + notes)
            .setPositiveButton("Скачать APK") { _, _ ->
                ApkDownloader.start(activity, release)
            }
            .setNeutralButton("GitHub") { _, _ ->
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GitHubReleaseApi.RELEASES))) }
            }
            .setNegativeButton("Позже", null)
            .show()
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
