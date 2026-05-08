package com.example.rokidglassesappcenter.host

object Constants {
    const val AUTH_REQUEST_CODE = 1001

    const val EXTRA_TOKEN = "token"

    /**
     * Client package name. Must match Client app's [applicationId].
     * Used both as the CUSTOMAPP session target and as the bootstrap install check key.
     */
    const val CLIENT_PACKAGE = "com.example.rokidglassesappcenter.client"
    const val CLIENT_MAIN_ACTIVITY = "com.example.rokidglassesappcenter.client.MainActivity"

    /** Filename of the bundled client APK in app assets. */
    const val CLIENT_ASSET_NAME = "client.apk"
}
