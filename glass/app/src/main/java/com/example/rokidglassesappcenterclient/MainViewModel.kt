package com.example.rokidglassesappcenterclient

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Client ViewModel.
 *
 * Owns:
 * - The CXR service bridge (subscribes to incoming requests, sends responses).
 * - An [AppManagerHandler] that converts JSON requests → PM operations → JSON responses.
 *
 * UI is intentionally minimal — glasses display real estate is small. We just expose
 * connection state and the most-recent traffic for visual confirmation.
 */
class MainViewModel : ViewModel() {

    private val cxrBridge = CXRServiceBridge()
    private var handler: AppManagerHandler? = null

    private val _connection = MutableStateFlow("disconnected")
    val connection = _connection.asStateFlow()

    private val _lastRequest = MutableStateFlow<String?>(null)
    val lastRequest = _lastRequest.asStateFlow()

    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse = _lastResponse.asStateFlow()

    private val statusListener = object : CXRServiceBridge.StatusListener {
        override fun onConnected(p0: String?, p1: String?, p2: Int) {
            Log.d(TAG, "onConnected p0=$p0 p1=$p1 p2=$p2")
            _connection.value = "connected"
        }
        override fun onDisconnected() {
            Log.d(TAG, "onDisconnected")
            _connection.value = "disconnected"
        }
        override fun onConnecting(p0: String?, p1: String?, p2: Int) {
            Log.d(TAG, "onConnecting")
            _connection.value = "connecting"
        }
        override fun onARTCStatus(p0: Float, p1: Boolean) {}
        override fun onRokidAccountChanged(p0: String?) {}
    }

    private val msgCallback = object : CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
            val payload = args?.firstStringOrNull().orEmpty()
            Log.d(TAG, "onReceive name=$name payload=$payload")
            _lastRequest.value = payload
            val responseJson = handler?.process(payload)
                ?: run {
                    Log.w(TAG, "handler not initialized; dropping request")
                    return
                }
            _lastResponse.value = responseJson
            cxrBridge.sendMessage(Protocol.KEY_RESPONSE, Caps().apply { write(responseJson) })
        }
    }

    fun init(context: Context) {
        if (handler != null) return
        handler = AppManagerHandler(context.applicationContext)
        cxrBridge.setStatusListener(statusListener)
        cxrBridge.subscribe(Protocol.KEY_REQUEST, msgCallback)
    }

    companion object {
        private const val TAG = "ClientVM"
    }
}

/** Reads the first Caps slot as a string, returning null if missing/typed wrong. */
private fun Caps.firstStringOrNull(): String? = runCatching {
    if (size() == 0) return@runCatching null
    val v = at(0)
    if (v.type() == Caps.Value.TYPE_STRING) v.string else null
}.getOrNull()
