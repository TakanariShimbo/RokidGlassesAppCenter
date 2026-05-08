package com.example.rokidglassesappcenterclient

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes CustomCMD JSON requests and turns them into PackageManager / Intent operations.
 *
 * Each public method returns the JSON response body (without the `id` and `ok`/`error`
 * envelope, which [process] adds). The split keeps op handlers focused on what they
 * actually return; envelope handling lives once.
 */
class AppManagerHandler(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    /** Top-level entry. Always returns a JSON string suitable for the response payload. */
    fun process(rawJson: String): String {
        val req = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return error(id = "", message = "invalid JSON: $rawJson")
        val id = req.optString("id")
        val op = req.optString("op")
        Log.d(TAG, "process op=$op id=$id")
        return try {
            when (op) {
                Protocol.Op.LIST -> ok(id, list())
                Protocol.Op.START -> ok(id, start(req))
                Protocol.Op.STOP -> ok(id, stop(req))
                Protocol.Op.UNINSTALL -> ok(id, uninstall(req))
                else -> error(id, "unknown op: $op")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "op=$op failed", t)
            error(id, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun list(): JSONObject {
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val arr = JSONArray()
        for (info in apps) {
            val item = JSONObject().apply {
                put("pkg", info.packageName)
                put("label", info.loadLabel(pm).toString())
                put("system", (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                put("enabled", info.enabled)
                runCatching {
                    val pkgInfo = pm.getPackageInfo(info.packageName, 0)
                    put("versionName", pkgInfo.versionName.orEmpty())
                    put("versionCode", pkgInfo.longVersionCode)
                    put("launchable", pm.getLaunchIntentForPackage(info.packageName) != null)
                }.onFailure { put("versionName", "") }
            }
            arr.put(item)
        }
        return JSONObject().put("apps", arr)
    }

    private fun start(req: JSONObject): JSONObject {
        val pkg = req.optString("pkg").ifBlank { error("pkg required for start") }
        val activityName = req.optString("activity").takeIf { it.isNotBlank() }

        val reuseFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        val intent = if (activityName != null) {
            Intent().apply {
                setClassName(pkg, activityName)
                addFlags(reuseFlags)
            }
        } else {
            pm.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(reuseFlags)
            } ?: error("no launch intent for $pkg")
        }
        context.startActivity(intent)
        return JSONObject().put("started", pkg)
    }

    /**
     * Force-stop is unavailable to non-system apps. As a soft equivalent we simply
     * return the user to the launcher; if the target app was foreground, this drops
     * it to background. We do NOT claim success at killing the process.
     */
    private fun stop(req: JSONObject): JSONObject {
        val pkg = req.optString("pkg").ifBlank { error("pkg required for stop") }
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(home)
        return JSONObject()
            .put("stopped", pkg)
            .put("note", "sent home; foreground app dropped to background")
    }

    /**
     * Triggers ACTION_UNINSTALL_PACKAGE. The user must confirm on the glasses display
     * — non-system apps cannot uninstall silently.
     */
    private fun uninstall(req: JSONObject): JSONObject {
        val pkg = req.optString("pkg").ifBlank { error("pkg required for uninstall") }
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$pkg")
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return JSONObject()
            .put("requested", pkg)
            .put("note", "user must confirm on glasses")
    }

    private fun ok(id: String, data: JSONObject): String =
        JSONObject().put("id", id).put("ok", true).put("data", data).toString()

    private fun error(id: String, message: String): String =
        JSONObject().put("id", id).put("ok", false).put("error", message).toString()

    private fun error(message: String): Nothing = throw IllegalArgumentException(message)

    companion object {
        private const val TAG = "AppManagerHandler"
    }
}
