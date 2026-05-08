package com.example.rokidglassesappcenter.client

/**
 * Wire protocol shared between AppCenter (phone) and Client (glasses).
 *
 * Transport: CXR-L CustomCMD. Phone-side uses [com.example.cxrglobal.CXRLink.sendCustomCmd];
 * glasses-side uses [com.rokid.cxr.CXRServiceBridge].
 *
 * Payload: Caps containing one string field — JSON-encoded request/response.
 * We use JSON instead of structured Caps because:
 *   1) easier to evolve (add fields without binary-format compat concerns)
 *   2) symmetric with how the rest of the codebase logs/debugs
 *   3) Caps gives us framing already; no need to also use it for structure
 */
object Protocol {
    /** CustomCMD key for phone → glasses (request). */
    const val KEY_REQUEST = "appmgr.req"

    /** CustomCMD key for glasses → phone (response). */
    const val KEY_RESPONSE = "appmgr.res"

    /** Request operations. */
    object Op {
        const val LIST = "list"
        const val START = "start"
        const val STOP = "stop"
        const val UNINSTALL = "uninstall"
    }
}
