package com.example.rokidglassesappcenterhost.ui.list

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cxrglobal.CXRLink
import com.example.cxrglobal.CxrDefs
import com.example.cxrglobal.callbacks.ICXRLinkCbk
import com.example.cxrglobal.callbacks.IGlassAppCbk
import com.example.rokidglassesappcenterhost.AppMgrClient
import com.example.rokidglassesappcenterhost.ClientBootstrap
import com.example.rokidglassesappcenterhost.Constants
import com.example.rokidglassesappcenterhost.HostApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Drives the App List screen.
 *
 * Owns the entire connect → bootstrap → list lifecycle:
 *   1. Build CXRLink + configCXRSession to Client package, then connect(token).
 *   2. Wait for both CXR transport and BT to come up.
 *   3. Run [ClientBootstrap] — install Client if missing, then start it.
 *   4. Construct [AppMgrClient] and refresh app list.
 */
class AppListViewModel : ViewModel() {

    enum class ClientState { Idle, Installing, Starting, Ready, Failed }

    private val _connection = MutableStateFlow(false)
    val connection = _connection.asStateFlow()

    private val _clientState = MutableStateFlow(ClientState.Idle)
    val clientState = _clientState.asStateFlow()

    private val _apps = MutableStateFlow<List<GlassApp>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps = _showSystemApps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    private var cxrLink: CXRLink? = null
    private var client: AppMgrClient? = null
    private var bootstrap: ClientBootstrap? = null
    private var appContext: Context? = null

    private var isLConnected = false
        set(value) {
            field = value
            _connection.value = value && isBTConnected
        }
    private var isBTConnected = false
        set(value) {
            field = value
            _connection.value = value && isLConnected
        }

    private val connectCallback = object : ICXRLinkCbk {
        override fun onCXRLConnected(connected: Boolean) {
            Log.d(TAG, "onCXRLConnected: $connected")
            isLConnected = connected
            maybeBootstrap()
        }
        override fun onGlassBtConnected(connected: Boolean) {
            Log.d(TAG, "onGlassBtConnected: $connected")
            isBTConnected = connected
            maybeBootstrap()
        }
        override fun onGlassAiAssistStart() {}
        override fun onGlassAiAssistStop() {}
    }

    fun init(context: Context, token: String?) {
        if (cxrLink != null) return
        appContext = context.applicationContext
        if (token.isNullOrBlank()) {
            _toast.value = "Token missing"
            return
        }
        val link = CXRLink(context).apply {
            configCXRSession(
                CxrDefs.CXRSession(
                    CxrDefs.CXRSessionType.CUSTOMAPP,
                    Constants.CLIENT_PACKAGE,
                ),
            )
            setCXRLinkCbk(connectCallback)
        }
        cxrLink = link
        bootstrap = ClientBootstrap(context, link)
        (context.applicationContext as? HostApplication)?.sharedCxrLink = link
        link.connect(token)
    }

    private var bootstrapped = false

    /** Trigger bootstrap exactly once after both transports are up. */
    private fun maybeBootstrap() {
        if (!_connection.value || bootstrapped) return
        bootstrapped = true
        viewModelScope.launch {
            _clientState.value = ClientState.Starting
            val result = bootstrap?.ensureRunning()
            when (result) {
                ClientBootstrap.Result.Started -> {
                    _clientState.value = ClientState.Ready
                    val link = cxrLink ?: return@launch
                    val c = AppMgrClient(link)
                    client = c
                    (appContext as? HostApplication)?.sharedAppMgrClient = c
                    refresh()
                }
                is ClientBootstrap.Result.Failed -> {
                    Log.e(TAG, "bootstrap failed: ${result.reason}")
                    _clientState.value = ClientState.Failed
                    _toast.value = result.reason
                }
                null -> {
                    _clientState.value = ClientState.Failed
                }
            }
        }
    }

    fun refresh() {
        val c = client ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                val resp = c.list()
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONObject("data")?.optJSONArray("apps") ?: JSONArray()
                    val list = (0 until arr.length()).map { arr.getJSONObject(it).toGlassApp() }
                        .sortedBy { it.label.lowercase() }
                    _apps.value = list
                } else {
                    _toast.value = resp.optString("error", "list failed")
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleShowSystem() {
        _showSystemApps.value = !_showSystemApps.value
    }

    fun startApp(app: GlassApp) {
        val c = client ?: return
        viewModelScope.launch {
            val resp = c.start(app.pkg)
            if (!resp.optBoolean("ok", false)) {
                _toast.value = resp.optString("error", "start failed")
            }
        }
    }

    fun stopApp(app: GlassApp) {
        val c = client ?: return
        viewModelScope.launch {
            val resp = c.stop(app.pkg)
            if (!resp.optBoolean("ok", false)) {
                _toast.value = resp.optString("error", "stop failed")
            }
        }
    }

    fun uninstallApp(app: GlassApp) {
        val c = client ?: return
        viewModelScope.launch {
            val resp = c.uninstall(app.pkg)
            if (!resp.optBoolean("ok", false)) {
                _toast.value = resp.optString("error", "uninstall failed")
            } else {
                _toast.value = "Confirm on glasses"
            }
        }
    }

    /**
     * Pick-and-install: copies the APK content from a SAF Uri into app private storage
     * (Hi Rokid AIDL needs a real file path) and pushes it via [CXRLink.appUploadAndInstall].
     */
    fun installApk(uri: Uri) {
        val ctx = appContext ?: return
        val link = cxrLink ?: return
        viewModelScope.launch {
            _uploading.value = true
            try {
                val cached = withContext(Dispatchers.IO) { copyToCache(ctx, uri) }
                if (cached == null) {
                    _toast.value = "APK copy failed"
                    return@launch
                }
                val deferred = CompletableDeferred<Boolean>()
                runCatching {
                    link.appUploadAndInstall(cached.absolutePath, object : IGlassAppCbk {
                        override fun onInstallAppResult(success: Boolean) {
                            deferred.complete(success)
                        }
                    })
                }.onFailure {
                    Log.e(TAG, "appUploadAndInstall threw", it)
                    deferred.complete(false)
                }
                val ok = deferred.await()
                _toast.value = if (ok) "Install succeeded" else "Install failed"
                if (ok) refresh()
            } finally {
                _uploading.value = false
            }
        }
    }

    fun consumeToast() { _toast.value = null }

    /** Copies content URI to a fresh file in cacheDir. Caller owns file lifetime. */
    private fun copyToCache(ctx: Context, uri: Uri): File? = try {
        val dest = File(ctx.cacheDir, "upload_${System.nanoTime()}.apk")
        ctx.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            dest.outputStream().use { input.copyTo(it) }
        }
        dest
    } catch (t: Throwable) {
        Log.e(TAG, "copyToCache failed", t)
        null
    }

    fun release() {
        runCatching { cxrLink?.disconnect() }
        cxrLink = null
        client = null
        bootstrap = null
        bootstrapped = false
        (appContext as? HostApplication)?.let {
            it.sharedCxrLink = null
            it.sharedAppMgrClient = null
        }
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AppListViewModel"
    }
}
