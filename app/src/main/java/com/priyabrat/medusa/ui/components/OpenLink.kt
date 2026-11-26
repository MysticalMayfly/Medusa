package com.priyabrat.medusa.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.priyabrat.medusa.R

/**
 * Opens a web address in whichever app on the TV handles it: Netflix, Prime Video, JioHotstar or YouTube for
 * their own links (the Mac app opened the same JustWatch links in the browser). Shows a short note when the
 * address is missing or no app takes it.
 */
@Composable
fun rememberOpenLink(): (String?) -> Unit {
    val context = LocalContext.current
    val toast = LocalToast.current
    return remember(context, toast) {
        { url ->
            if (url.isNullOrBlank()) {
                toast(context.getString(R.string.link_missing))
            } else {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    toast(context.getString(R.string.link_no_app))
                }
            }
        }
    }
}
