package com.example.rokidglassesappcenterhost.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Sends the user to a brand-appropriate app market for a given target package,
 * falling back to the Rokid web download page when no market handles the deep link.
 */
object SmartMarketLauncher {
    private const val TAG = "SmartMarketLauncher"
    private const val DOWNLOAD_URL = "https://static.rokidcdn.com/web_assets/site/downloadAI.html"

    fun launchMarket(context: Context, destPackageName: String) {
        val marketPackage = when (Build.BRAND) {
            "HUAWEI" -> "com.huawei.appmarket"
            "Xiaomi" -> "com.xiaomi.market"
            "OPPO" -> "com.heytap.market"
            "VIVO" -> "com.bbk.appstore"
            else -> ""
        }

        if (marketPackage.isNotEmpty()) {
            val destUrl = if (marketPackage == "com.heytap.market") {
                "market://details?id=$destPackageName&channel=heytap"
            } else {
                "market://details?id=$destPackageName"
            }
            val launched = runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destUrl)).apply {
                    `package` = marketPackage
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                true
            }.getOrElse {
                Log.w(TAG, "market launch failed: $marketPackage", it)
                false
            }
            if (launched) return
        }

        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }.onFailure { Log.w(TAG, "web fallback failed", it) }
    }
}
