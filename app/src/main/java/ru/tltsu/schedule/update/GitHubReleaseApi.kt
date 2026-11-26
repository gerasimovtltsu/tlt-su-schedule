package ru.tltsu.schedule.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class AppRelease(
    val version: String,
    val tag: String,
    val notes: String,
    val downloadUrl: String
)

internal object GitHubReleaseApi {
    const val RELEASES = "https://github.com/gerasimovtltsu/tlt-su-schedule/releases"
    private const val ENDPOINT = "https://api.github.com/repos/gerasimovtltsu/tlt-su-schedule/releases/latest"

    fun latest(): AppRelease {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "ScheduleTGU-Android")
        try {
            check(connection.responseCode == 200) { "GitHub HTTP " + connection.responseCode }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val version = ReleaseVersion.parts(tag)?.joinToString(".") ?: error("Unknown release tag")
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") != "ScheduleTGU.apk") continue
                val url = asset.getString("browser_download_url")
                check(url.startsWith("$RELEASES/download/")) { "Unexpected APK URL" }
                return AppRelease(version, tag, json.optString("body"), url)
            }
            error("APK missing")
        } finally {
            connection.disconnect()
        }
    }
}
