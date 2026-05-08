package com.example.rokidglassesappcenterhost

import android.app.Application
import com.example.cxrglobal.CXRLink

/**
 * Application-scope holder for the active CXRLink and AppMgrClient.
 *
 * The list screen owns a single connection that's reused across operations,
 * so we keep it here rather than recreating it per Activity.
 */
class HostApplication : Application() {
    var sharedCxrLink: CXRLink? = null
    var sharedAppMgrClient: AppMgrClient? = null
}
