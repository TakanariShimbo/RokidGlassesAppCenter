package com.example.rokidglassesappcenter.host

import android.util.Log
import com.example.cxrglobal.CXRLink
import com.example.cxrglobal.callbacks.ICustomCmdCbk
import com.rokid.cxr.Caps
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Coroutine-based CustomCMD client. Sends JSON requests to Client and resolves
 * responses by `id` correlation.
 *
 * Wire format on each side:
 * - Send: `Caps().apply { write(jsonString) }.serialize()` → ByteArray → `sendCustomCmd(key, bytes)`
 * - Recv: `Caps.fromBytes(payload).at(0).string` → JSON
 *
 * Lifecycle: this class registers itself as the [com.example.cxrglobal.CXRLink]'s
 * CustomCmd callback. Don't share the link's CustomCmd callback with anything else
 * concurrently — there's only one slot.
 */
class AppMgrClient(private val link: CXRLink) {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    init {
        link.setCXRCustomCmdCbk(object : ICustomCmdCbk {
            override fun onCustomCmdResult(key: String, payload: ByteArray) {
                if (key != Protocol.KEY_RESPONSE) return
                val caps = runCatching { Caps.fromBytes(payload) }.getOrNull() ?: return
                val json = caps.firstStringOrNull() ?: return
                Log.d(TAG, "recv $json")
                val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
                val id = obj.optString("id")
                pending.remove(id)?.complete(obj)
            }
        })
    }

    suspend fun list(): JSONObject = call(Protocol.Op.LIST)

    suspend fun start(pkg: String, activity: String? = null): JSONObject =
        call(Protocol.Op.START) {
            put("pkg", pkg)
            if (!activity.isNullOrBlank()) put("activity", activity)
        }

    suspend fun stop(pkg: String): JSONObject =
        call(Protocol.Op.STOP) { put("pkg", pkg) }

    suspend fun uninstall(pkg: String): JSONObject =
        call(Protocol.Op.UNINSTALL) { put("pkg", pkg) }

    /** Generic correlated request. */
    private suspend fun call(
        op: String,
        builder: JSONObject.() -> Unit = {},
    ): JSONObject {
        val id = UUID.randomUUID().toString()
        val req = JSONObject().apply {
            put("id", id)
            put("op", op)
            builder()
        }
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val bytes = Caps().apply { write(req.toString()) }.serialize()
        Log.d(TAG, "send op=$op id=$id")
        link.sendCustomCmd(Protocol.KEY_REQUEST, bytes)
        return try {
            withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            JSONObject().put("id", id).put("ok", false).put("error", "timeout")
        }
    }

    companion object {
        private const val TAG = "AppMgrClient"
        private const val REQUEST_TIMEOUT_MS = 8_000L
    }
}

/** First Caps value as String, or null if missing/wrong type. */
private fun Caps.firstStringOrNull(): String? = runCatching {
    if (size() == 0) return@runCatching null
    val v = at(0)
    if (v.type() == Caps.Value.TYPE_STRING) v.string else null
}.getOrNull()

/** Shared protocol mirror — keep in sync with the Client's [Protocol]. */
object Protocol {
    const val KEY_REQUEST = "appmgr.req"
    const val KEY_RESPONSE = "appmgr.res"

    object Op {
        const val LIST = "list"
        const val START = "start"
        const val STOP = "stop"
        const val UNINSTALL = "uninstall"
    }
}
