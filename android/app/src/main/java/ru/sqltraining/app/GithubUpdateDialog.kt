package ru.sqltraining.app

import android.app.DownloadManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val linkColor = Color(0xFF65D6AD)

private sealed interface TransferUi {
    data object None : TransferUi
    data class Downloading(val percent: Int?) : TransferUi
    data object Ready : TransferUi
    data class Failed(val reason: String) : TransferUi
}

/** Explicit, opt-in update check. SQL lessons and the local DB never require network access. */
@Composable
fun GithubUpdateDialog(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val service = remember(context) { GithubUpdateService(context) }
    val prefs = remember(context) { context.getSharedPreferences("github_updates", Context.MODE_PRIVATE) }
    var installedId by remember { mutableLongStateOf(prefs.getLong("download_id", -1)) }
    var installedTag by remember { mutableStateOf(prefs.getString("download_tag", "") ?: "") }
    var lookup by remember { mutableStateOf<ReleaseCheck?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var transfer by remember { mutableStateOf<TransferUi>(TransferUi.None) }
    var installMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshToken) {
        isChecking = true
        lookupError = null
        lookup = null
        try {
            lookup = service.latestRelease()
        } catch (error: Exception) {
            lookupError = error.message ?: "Не удалось связаться с GitHub."
        } finally {
            isChecking = false
        }
    }

    val release = (lookup as? ReleaseCheck.Available)?.release

    LaunchedEffect(release?.tag, installedId) {
        if (release == null || installedId <= 0 || installedTag != release.tag) {
            transfer = TransferUi.None
            return@LaunchedEffect
        }
        while (true) {
            try {
                val current = service.snapshot(installedId)
                if (current == null) {
                    transfer = TransferUi.Failed("Загрузка не найдена. Нажми «Скачать APK» ещё раз.")
                    break
                }
                when (current.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        transfer = if (service.verify(installedId, release)) TransferUi.Ready
                        else TransferUi.Failed("Контрольная сумма APK не совпала. Установка отменена.")
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        transfer = TransferUi.Failed("Не удалось скачать APK (код ошибки ${current.failureReason}).")
                        break
                    }
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_RUNNING ->
                        transfer = TransferUi.Downloading(current.percentage)
                    else -> {
                        transfer = TransferUi.Failed("Неизвестный статус загрузки.")
                        break
                    }
                }
            } catch (error: Exception) {
                transfer = TransferUi.Failed(error.message ?: "Ошибка проверки загруженного APK.")
                break
            }
            delay(700)
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Обновления SQL Trainer") },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val installed = remember(context) {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                }
                Text("Установлена версия $installed", style = MaterialTheme.typography.bodyMedium)
                when {
                    isChecking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Проверяем GitHub Releases…")
                        }
                    }
                    lookupError != null -> {
                        Text(lookupError!!, color = MaterialTheme.colorScheme.error)
                        Text("Проверка выполняется только по твоему запросу. Для неё нужен интернет.", fontSize = 12.sp)
                    }
                    lookup is ReleaseCheck.NoReleases -> {
                        Text("Пока нет опубликованных версий в GitHub Releases.")
                        Text(
                            "До публикации первого релиза APK можно скачать вручную из GitHub Actions. " +
                                "Внутреннее обновление заработает после настройки подписанных релизов.",
                            fontSize = 12.sp
                        )
                    }
                    lookup is ReleaseCheck.Current -> {
                        Text("Установлена актуальная опубликованная версия.", color = linkColor)
                        Text("Последний релиз: ${(lookup as ReleaseCheck.Current).release.tag}")
                    }
                    release != null -> {
                        Text("Доступна новая версия ${release.tag}", color = linkColor)
                        if (release.notes.isNotBlank()) {
                            Text("Что изменилось", style = MaterialTheme.typography.titleSmall)
                            Text(release.notes, fontSize = 12.sp)
                        }
                        when (val state = transfer) {
                            TransferUi.None -> {
                                if (release.apkUrl.isEmpty()) {
                                    Text("Релиз опубликован, но APK ещё не загружен. Проверь позже.")
                                } else {
                                    Text("APK загружается только после твоего подтверждения.")
                                }
                            }
                            is TransferUi.Downloading -> {
                                Text("Скачивание: ${state.percent?.let { "$it%" } ?: "ожидание…"}")
                                if (state.percent != null) {
                                    LinearProgressIndicator(
                                        progress = { state.percent / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Text("Если закрыть окно, загрузка продолжится в Android.", fontSize = 11.sp)
                            }
                            TransferUi.Ready -> {
                                Text("APK скачан и проверен. Android попросит подтвердить установку.", color = linkColor)
                            }
                            is TransferUi.Failed -> {
                                Text(state.reason, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (installMessage != null) Text(installMessage!!, fontSize = 12.sp)
                        Text(
                            "Обновление поверх старых APK из GitHub Actions может не установиться " +
                                "из-за разных подписей. После перехода на постоянный ключ подписи " +
                                "следующие релизы будут обновляться без удаления данных.",
                            fontSize = 11.sp
                        )
                    }
                }
                TextButton(
                    onClick = {
                        try {
                            service.openUrl(
                                when (val status = lookup) {
                                    is ReleaseCheck.Available -> status.release.htmlUrl
                                    is ReleaseCheck.Current -> status.release.htmlUrl
                                    else -> ACTIONS_PAGE
                                }
                            )
                        } catch (e: Exception) {
                            installMessage = e.message ?: "Не удалось открыть браузер."
                        }
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Открыть на GitHub")
                }
            }
        },
        confirmButton = {
            when {
                release != null && release.apkUrl.isNotBlank() &&
                    (transfer is TransferUi.None || transfer is TransferUi.Failed) -> {
                    Button(onClick = {
                        try {
                            // A previous failed download can own the same destination file.
                            // DownloadManager handles replacement when the new filename is unique.
                            val id = service.enqueue(release)
                            installedTag = release.tag
                            installedId = id
                            prefs.edit().putLong("download_id", id)
                                .putString("download_tag", release.tag).apply()
                            transfer = TransferUi.Downloading(null)
                            installMessage = null
                        } catch (error: Exception) {
                            transfer = TransferUi.Failed(error.message ?: "Не удалось начать загрузку.")
                        }
                    }) { Text("Скачать APK") }
                }
                release != null && transfer is TransferUi.Ready -> {
                    Button(onClick = {
                        try {
                            installMessage = when (service.install(installedId)) {
                                InstallResult.InstallerOpened -> "Подтверди установку в системном окне Android."
                                InstallResult.PermissionRequired ->
                                    "Разреши установку из SQL Trainer в настройках, затем снова нажми «Установить»."
                            }
                        } catch (error: Exception) {
                            installMessage = error.message ?: "Не удалось открыть установщик."
                        }
                    }) { Text("Установить") }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { refreshToken++ }) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Повторить")
                }
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
        }
    )
}
