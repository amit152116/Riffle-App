package com.amit152116.riffle.log

import android.util.Log

/**
 * Sole entry point for warning/error logging - same "one owner" shape as
 * PreferencesManager/AppContainer. Construct one per class (`private val log
 * = RiffleLog(this)`) instead of a string TAG const: the tag can't drift
 * from the actual class name after a rename, since it's read off the
 * instance rather than typed by hand.
 *
 * A caught exception should never just be printStackTrace()'d into stdout
 * (invisible in release, no record); it should go through here. Right now
 * this just forwards to android.util.Log, but it's the one seam to add a
 * crash-reporting hook (Crashlytics, Sentry, ...) later without touching
 * every call site again.
 */
class RiffleLog(source: Any) {
    private val tag = source::class.java.simpleName

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
