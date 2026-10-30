package com.danitube.app.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.danitube.app.data.suggest.UnmeteredNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NeedsWifi : UpdateState
    data class Downloading(val versionName: String, val percent: Int) : UpdateState
    data class Ready(val versionName: String) : UpdateState
    data class Failed(val message: String?) : UpdateState
}

/** The newest GitHub release that has an APK. */
data class ReleaseInfo(val versionName: String, val versionCode: Long, val apkUrl: String, val sizeBytes: Long)

/**
 * Updates the app from the project's GitHub releases. Everything here is Wi-Fi only.
 *
 * The daily [UpdateWorker] and the parent's button call [checkAndDownload]: it asks GitHub for the
 * latest release, and when it is newer than the installed version, downloads the APK into the cache
 * and checks it (same app, newer, same signing key). Installing is a separate step, [install], that
 * only the parent starts, so the install screen is never in front of the child.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val network: UnmeteredNetwork,
) {
    private val packages = context.packageManager
    private val dir = File(context.cacheDir, "updates").apply { mkdirs() }
    private val lock = Mutex()

    val installedName: String = installedInfo().versionName.orEmpty()
    private val installedCode: Long = PackageInfoCompat.getLongVersionCode(installedInfo())

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private fun installedInfo(): PackageInfo = packages.getPackageInfo(context.packageName, 0)

    /** Whatever APK a previous run left behind is still good if it is newer than what is installed. */
    private fun initialState(): UpdateState {
        val ready = downloadedApks().maxByOrNull { it.second }
        downloadedApks().filter { it != ready }.forEach { it.first.delete() }
        return ready?.let { UpdateState.Ready(it.third) } ?: UpdateState.Idle
    }

    /** Looks for a newer release and downloads it. Does nothing off Wi-Fi. */
    suspend fun checkAndDownload() = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!network.isAvailable()) {
                _state.value = UpdateState.NeedsWifi
                return@withContext
            }
            _state.value = UpdateState.Checking
            try {
                val release = fetchLatest()
                if (release == null || release.versionCode <= installedCode) {
                    downloadedApks().forEach { it.first.delete() }
                    _state.value = UpdateState.UpToDate
                    return@withContext
                }
                val target = apkFile(release.versionName)
                if (!(target.exists() && verify(target))) download(release, target)
                _state.value = UpdateState.Ready(release.versionName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                _state.value = UpdateState.Failed(e.message)
            }
        }
    }

    /** True when Android lets this app install packages (the "install unknown apps" switch). */
    fun canInstall(): Boolean = packages.canRequestPackageInstalls()

    /** Opens the system screen where the parent turns that switch on for this app. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Hands the downloaded APK to Android's installer. Android then asks for a confirmation (on
     * Android 12+ it may skip it when this app installed the current version) and restarts the app.
     */
    suspend fun install() = withContext(Dispatchers.IO) {
        val apk = downloadedApks().maxByOrNull { it.second }?.first ?: return@withContext
        try {
            val installer = packages.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val result = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                // Mutable: the system adds the status and the confirmation screen to this intent.
                val pending = PendingIntent.getBroadcast(
                    context, id, result, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Install failed", e)
            _state.value = UpdateState.Failed(e.message)
        }
    }

    private fun fetchLatest(): ReleaseInfo? {
        val json = JSONObject(open(LATEST_RELEASE_URL, "application/vnd.github+json").use { it.readBytes().decodeToString() })
        return parseRelease(json)
    }

    private fun download(release: ReleaseInfo, target: File) {
        val partial = File(dir, "${target.name}.part")
        _state.value = UpdateState.Downloading(release.versionName, 0)
        open(release.apkUrl, "application/octet-stream").use { input ->
            partial.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                var lastPercent = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    val percent = if (release.sizeBytes > 0) (copied * 100 / release.sizeBytes).toInt().coerceIn(0, 100) else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _state.value = UpdateState.Downloading(release.versionName, percent)
                    }
                }
            }
        }
        check(release.sizeBytes <= 0 || partial.length() == release.sizeBytes) { "Incomplete download" }
        check(verify(partial)) { "The downloaded file is not a valid update" }
        target.delete()
        check(partial.renameTo(target)) { "Could not save the update" }
        downloadedApks().filter { it.first != target }.forEach { it.first.delete() }
    }

    private fun open(url: String, accept: String) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "DaniTube")
            check(responseCode == HttpURLConnection.HTTP_OK) { "GitHub answered $responseCode" }
        }.inputStream

    /** The file must be this app, newer than what is installed, and signed with the same key. */
    private fun verify(apk: File): Boolean {
        val info = packages.getPackageArchiveInfo(apk.path, signingFlags()) ?: return false
        return info.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(info) > installedCode &&
            signers(info).isNotEmpty() &&
            signers(info) == signers(packages.getPackageInfo(context.packageName, signingFlags()))
    }

    @Suppress("DEPRECATION")
    private fun signingFlags() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }.orEmpty()
        return signatures.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    private fun apkFile(versionName: String) = File(dir, "DaniTube-$versionName.apk")

    /** (file, version code, version name) of each valid, newer APK in the cache; others are deleted. */
    private fun downloadedApks(): List<Triple<File, Long, String>> =
        dir.listFiles { f -> f.name.endsWith(".apk") }.orEmpty().mapNotNull { file ->
            val info = packages.getPackageArchiveInfo(file.path, 0)
            val code = info?.let { PackageInfoCompat.getLongVersionCode(it) }
            if (info != null && code != null && code > installedCode) Triple(file, code, info.versionName.orEmpty())
            else { file.delete(); null }
        }

    companion object {
        private const val TAG = "UpdateManager"
        private const val TIMEOUT_MS = 20_000
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/JavToscano/danitube/releases/latest"

        /** `v1.2.3` (or `1.2.3`) to the version code the release workflow gives it: 1*10000 + 2*100 + 3. */
        fun versionCodeOf(tag: String): Long? {
            val parts = tag.removePrefix("v").substringBefore('-').split('.').map { it.toLongOrNull() ?: return null }
            if (parts.size != 3) return null
            return parts[0] * 10_000 + parts[1] * 100 + parts[2]
        }

        /** The release's version and its APK; null when the tag is not a version or there is no APK. */
        fun parseRelease(json: JSONObject): ReleaseInfo? {
            val tag = json.optString("tag_name")
            val code = versionCodeOf(tag) ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    return ReleaseInfo(tag.removePrefix("v"), code, asset.getString("browser_download_url"), asset.optLong("size"))
                }
            }
            return null
        }
    }
}
