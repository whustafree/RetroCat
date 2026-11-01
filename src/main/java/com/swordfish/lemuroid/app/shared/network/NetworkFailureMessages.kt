package com.swordfish.lemuroid.app.shared.network

import android.content.Context
import androidx.annotation.StringRes
import com.swordfish.lemuroid.R

/**
 * What to tell the user about something that failed, in their language.
 *
 * The exception may carry a text of its own for the cases where the generic one would not say
 * enough (a Drive folder that is not shared, a link that is not a folder); otherwise the failure
 * itself decides, so nothing ever shows a raw exception message in English.
 */
fun Context.networkErrorMessage(throwable: Throwable): String {
    val written = (throwable as? NetworkFailureException)?.messageRes
    return getString(written ?: throwable.asNetworkFailure().messageRes())
}

/** The sentence that goes with a failure when no more specific one was written. */
@StringRes
fun NetworkFailure.messageRes(): Int =
    when (this) {
        NetworkFailure.OFFLINE -> R.string.network_error_offline
        NetworkFailure.TIMEOUT -> R.string.network_error_timeout
        NetworkFailure.SERVER -> R.string.network_error_server
        NetworkFailure.NOT_FOUND -> R.string.network_error_not_found
        NetworkFailure.INVALID_CONTENT -> R.string.network_error_content
        NetworkFailure.CORRUPT -> R.string.network_error_corrupt
        NetworkFailure.NOT_SHARED -> R.string.network_error_not_shared
        NetworkFailure.UNKNOWN -> R.string.network_error_unknown
    }
