package com.example.rokidglassesappcenterhost

import android.content.Context
import android.util.Log
import com.example.cxrglobal.CXRLink
import com.example.cxrglobal.callbacks.IGlassAppCbk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Ensures the Client APK is installed on glasses, then launches it.
 *
 * Flow:
 *   1. `appIsInstalled` query — wait for `onQueryAppResult` callback.
 *   2. If absent: extract `assets/client.apk` → `filesDir/client.apk` →
 *      `appUploadAndInstall` → wait for `onInstallAppResult`.
 *   3. `appStart(MainActivity)` — wait for `onOpenAppResult`.
 *
 * The CXR link must already be configured for the Client's CUSTOMAPP session
 * and connected (CXR + BT) before calling [ensureRunning].
 */
class ClientBootstrap(
    private val context: Context,
    private val link: CXRLink,
) {
    sealed class Result {
        object Started : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun ensureRunning(): Result {
        val installed = queryInstalled()
        Log.d(TAG, "installed=$installed")
        if (!installed) {
            val installOk = installFromAssets()
            if (!installOk) return Result.Failed("client install failed")
        }
        val startOk = startClient()
        return if (startOk) Result.Started else Result.Failed("client start failed")
    }

    private suspend fun queryInstalled(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        link.appIsInstalled(object : IGlassAppCbk {
            override fun onQueryAppResult(installed: Boolean) {
                deferred.complete(installed)
            }
        })
        return awaitOrFalse(deferred, "queryInstalled")
    }

    private suspend fun installFromAssets(): Boolean {
        val apkFile = extractAssetApk() ?: return false
        val deferred = CompletableDeferred<Boolean>()
        try {
            link.appUploadAndInstall(apkFile.absolutePath, object : IGlassAppCbk {
                override fun onInstallAppResult(success: Boolean) {
                    deferred.complete(success)
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "appUploadAndInstall threw", t)
            return false
        }
        // Install can take a while (bytes go over BT) — wider timeout.
        return awaitOrFalse(deferred, "install", timeoutMs = INSTALL_TIMEOUT_MS)
    }

    private fun extractAssetApk(): File? = try {
        val dest = File(context.filesDir, Constants.CLIENT_ASSET_NAME)
        // Always overwrite to pick up upgrades when manager APK is reinstalled.
        context.assets.open(Constants.CLIENT_ASSET_NAME).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest
    } catch (t: Throwable) {
        Log.e(TAG, "extractAssetApk failed", t)
        null
    }

    private suspend fun startClient(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        link.appStart(Constants.CLIENT_MAIN_ACTIVITY, object : IGlassAppCbk {
            override fun onOpenAppResult(success: Boolean) {
                deferred.complete(success)
            }
        })
        return awaitOrFalse(deferred, "start")
    }

    private suspend fun awaitOrFalse(
        deferred: CompletableDeferred<Boolean>,
        what: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Boolean = try {
        withTimeout(timeoutMs) { deferred.await() }
    } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "$what timed out after ${timeoutMs}ms")
        false
    }

    companion object {
        private const val TAG = "ClientBootstrap"
        private const val DEFAULT_TIMEOUT_MS = 5_000L
        private const val INSTALL_TIMEOUT_MS = 60_000L
    }
}
