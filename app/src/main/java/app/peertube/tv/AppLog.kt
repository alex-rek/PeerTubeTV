package app.peertube.tv

import android.util.Log

private const val LOG_TAG = "PeerIcheTV"

/**
 * Records a handled failure with its stack trace. Errors shown to the user only as a short message
 * are still visible in `adb logcat -s PeerIcheTV` when diagnosing a report.
 */
internal fun logFailure(what: String, error: Throwable) {
    Log.w(LOG_TAG, what, error)
}
