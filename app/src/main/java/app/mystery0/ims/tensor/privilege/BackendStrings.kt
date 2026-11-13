package app.mystery0.ims.tensor.privilege

import android.content.Context
import androidx.annotation.StringRes
import app.mystery0.ims.tensor.R

/** Backend-layer messages resolved from string resources; initialized once by the Application. */
object BackendStrings {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes resId: Int): String =
        requireNotNull(appContext) { "BackendStrings not initialized" }.getString(resId)
}
