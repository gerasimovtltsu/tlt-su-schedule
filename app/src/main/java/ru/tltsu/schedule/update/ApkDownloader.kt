package ru.tltsu.schedule.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object ApkDownloader {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    fun start(activity: Activity, release: AppRelease) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Разрешение на установку")
                .setMessage("Разрешите установку из этого приложения в системных настройках, затем повторите скачивание.")
                .setPositiveButton("Настройки") { _, _ ->
                    runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName))) }
                }.setNegativeButton("Отмена", null).show()
            return
        }
        if (!running.compareAndSet(false, true)) {
            Toast.makeText(activity, "Обновление уже скачивается", Toast.LENGTH_SHORT).show()
            return
        }
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
            setPadding(24, 24, 24, 24)
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Скачивание обновления")
            .setView(progress).setNegativeButton("Отмена") { _, _ -> cancelled.set(true) }.setCancelable(true).create()
        dialog.setOnCancelListener { cancelled.set(true) }
        dialog.show()
        executor.execute {
            val result = runCatching {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val temporary = File(dir, "download.tmp")
                val apk = File(dir, "ScheduleTGU.apk")
                val connection = URL(release.downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                try {
                    check(connection.responseCode == 200)
                    val total = connection.contentLengthLong
                    check(total <= 150_000_000L)
                    var count = 0L
                    var lastPercent = -1
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                check(!cancelled.get())
                                val size = input.read(buffer)
                                if (size < 0) break
                                count += size
                                check(count <= 150_000_000L)
                                output.write(buffer, 0, size)
                                if (total > 0) {
                                    val percent = (count * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        activity.runOnUiThread { if (!activity.isDestroyed && !cancelled.get()) { progress.isIndeterminate = false; progress.progress = percent } }
                                    }
                                }
                            }
                        }
                    }
                    check(!cancelled.get())
                    check(total <= 0 || count == total)
                    @Suppress("DEPRECATION")
                    val info = activity.packageManager.getPackageArchiveInfo(temporary.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES)
                    check(info?.packageName == activity.packageName) { "Unexpected package" }
                    check(ReleaseVersion.newer(info?.versionName.orEmpty(), UpdateManager.installed(activity)))
                    @Suppress("DEPRECATION")
                    val installed = activity.packageManager.getPackageInfo(activity.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
                    @Suppress("DEPRECATION")
                    check(info!!.signatures?.toSet() == installed.signatures?.toSet() && !installed.signatures.isNullOrEmpty()) { "Signature mismatch" }
                    @Suppress("DEPRECATION")
                    check(info.versionCode > installed.versionCode) { "Version code is not newer" }
                    check(temporary.renameTo(apk) || run { temporary.copyTo(apk, overwrite = true); temporary.delete(); true })
                    apk
                } finally {
                    connection.disconnect()
                    temporary.delete()
                }
            }
            activity.runOnUiThread {
                running.set(false)
                if (activity.isDestroyed || activity.isFinishing || cancelled.get()) return@runOnUiThread
                dialog.dismiss()
                result.onSuccess { apk ->
                    val uri = FileProvider.getUriForFile(activity, activity.packageName + ".updates", apk)
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { activity.startActivity(intent) }
                        .onFailure { Toast.makeText(activity, "Не удалось открыть установщик", Toast.LENGTH_LONG).show() }
                }.onFailure { Toast.makeText(activity, "Не удалось скачать или проверить APK", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
