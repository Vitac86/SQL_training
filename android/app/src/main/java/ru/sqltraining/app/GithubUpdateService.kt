package ru.sqltraining.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val RELEASES_API = "https://api.github.com/repos/Vitac86/SQL_training/releases/latest"
const val RELEASES_PAGE = "https://github.com/Vitac86/SQL_training/releases"
const val ACTIONS_PAGE = "https://github.com/Vitac86/SQL_training/actions/workflows/android.yml"
private const val APK_MIME = "application/vnd.android.package-archive"

data class AppRelease(
    val tag: String,
    val version: String,
    val notes: String,
    val htmlUrl: String,
    val apkUrl: String,
    val sha256: String?,
    val apkBytes: Long
)

sealed interface ReleaseCheck {
    data class Available(val release: AppRelease) : ReleaseCheck
    data class Current(val release: AppRelease) : ReleaseCheck
    data object NoReleases : ReleaseCheck
}

data class DownloadSnapshot(
    val status: Int,
    val bytesReceived: Long,
    val totalBytes: Long,
    val failureReason: Int = 0
) {
    val percentage: Int?
        get() = if (totalBytes > 0) (bytesReceived * 100L / totalBytes).toInt().coerceIn(0, 100) else null
}

sealed interface InstallResult {
    data object InstallerOpened : InstallResult
    data object PermissionRequired : InstallResult
}

object ReleaseVersion {
    fun parse(version: String): List<Int>? {
        val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""").matchEntire(version.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    /** Positive when GitHub's version is newer than the installed one. */
    fun compare(local: String, remote: String): Int? {
        val first = parse(local) ?: return null
        val second = parse(remote) ?: return null
        for (index in 0..2) {
            val diff = second[index].compareTo(first[index])
            if (diff != 0) return diff
        }
        return 0
    }
}

class GithubUpdateService(private val context: Context) {
    private val downloader by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    suspend fun latestRelease(): ReleaseCheck = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SQL-Trainer-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = false
        }
        try {
            val status = connection.responseCode
            if (status == 404) return@withContext ReleaseCheck.NoReleases
            if (status == 403 || status == 429) {
                throw IllegalStateException("GitHub временно ограничил число проверок. Попробуй позже.")
            }
            if (status != 200) {
                throw IllegalStateException("GitHub вернул HTTP $status. Проверь подключение к интернету.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            val newer = ReleaseVersion.compare(current, tag)
                ?: throw IllegalStateException("Не удалось сопоставить версии: $current и $tag")
            val page = json.getString("html_url")
            require(page.startsWith("$RELEASES_PAGE/tag/")) { "Неизвестный адрес страницы релиза." }

            val assets = json.optJSONArray("assets")
            var apk: JSONObject? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val item = assets.getJSONObject(i)
                    if (item.optString("name") == "sql-trainer.apk") {
                        apk = item
                        break
                    }
                }
            }
            val apkUrl = apk?.optString("browser_download_url").orEmpty()
            if (apkUrl.isNotEmpty()) {
                val uri = Uri.parse(apkUrl)
                require(uri.scheme == "https" &&
                    uri.host == "github.com" &&
                    uri.path?.startsWith("/Vitac86/SQL_training/releases/download/") == true
                ) { "APK не принадлежит официальному релизу SQL Trainer." }
            }

            val digest = apk?.optString("digest").orEmpty()
            val checksum = digest.removePrefix("sha256:").takeIf {
                digest.startsWith("sha256:") && it.length == 64 && it.all { c -> c in "0123456789abcdefABCDEF" }
            }
            val release = AppRelease(
                tag = tag,
                version = tag.removePrefix("v"),
                notes = json.optString("body", "").take(1500),
                htmlUrl = page,
                apkUrl = apkUrl,
                sha256 = checksum,
                apkBytes = apk?.optLong("size", 0) ?: 0
            )
            if (newer > 0) ReleaseCheck.Available(release) else ReleaseCheck.Current(release)
        } finally {
            connection.disconnect()
        }
    }

    fun enqueue(release: AppRelease): Long {
        check(release.apkUrl.isNotBlank()) { "В релизе пока нет APK. Попробуй позже." }
        val name = "sql-trainer-${release.version}-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("SQL Trainer ${release.tag}")
            .setDescription("Обновление из GitHub Releases")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        return downloader.enqueue(request)
    }

    suspend fun snapshot(id: Long): DownloadSnapshot? = withContext(Dispatchers.IO) {
        downloader.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DownloadSnapshot(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                bytesReceived = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                failureReason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            )
        }
    }

    suspend fun verify(id: Long, release: AppRelease): Boolean = withContext(Dispatchers.IO) {
        val expected = release.sha256 ?: return@withContext true
        val uri = downloader.getUriForDownloadedFile(id) ?: return@withContext false
        val hash = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                hash.update(buffer, 0, read)
            }
        }
        val actual = hash.digest().joinToString("") { "%02x".format(it) }
        actual.equals(expected, ignoreCase = true)
    }

    fun install(id: Long): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(settings)
            return InstallResult.PermissionRequired
        }
        val uri = downloader.getUriForDownloadedFile(id)
            ?: throw IllegalStateException("Файл APK не найден в загрузках.")
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(install)
        return InstallResult.InstallerOpened
    }

    fun openUrl(url: String) {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" && uri.host == "github.com") { "Недопустимый адрес GitHub." }
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
