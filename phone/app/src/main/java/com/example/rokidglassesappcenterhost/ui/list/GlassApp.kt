package com.example.rokidglassesappcenterhost.ui.list

import org.json.JSONObject

/** App entry as reported by Client's `list` op. */
data class GlassApp(
    val pkg: String,
    val label: String,
    val versionName: String,
    val system: Boolean,
    val enabled: Boolean,
    val launchable: Boolean,
)

fun JSONObject.toGlassApp(): GlassApp = GlassApp(
    pkg = optString("pkg"),
    label = optString("label"),
    versionName = optString("versionName"),
    system = optBoolean("system", false),
    enabled = optBoolean("enabled", true),
    launchable = optBoolean("launchable", true),
)
