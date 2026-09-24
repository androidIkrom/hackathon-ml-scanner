package com.nungil.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.nungil.contract.Lang
import com.nungil.contract.app.AppServices
import java.util.Calendar

/** The phone's current date and time, as the plain numbers core/weather/Clock takes. */
internal data class Now(val month: Int, val day: Int, val dayOfWeek: Int, val hour: Int, val minute: Int) {
    companion object {
        fun read(): Now {
            val c = Calendar.getInstance()
            return Now(
                month = c.get(Calendar.MONTH) + 1,
                day = c.get(Calendar.DAY_OF_MONTH),
                // Calendar: 1 = Sunday; Clock: 1 = Monday .. 7 = Sunday.
                dayOfWeek = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1,
                hour = c.get(Calendar.HOUR_OF_DAY),
                minute = c.get(Calendar.MINUTE),
            )
        }
    }
}

/** The activity's AppServices (MainActivity), for a view that is not a Fragment. */
internal fun Context.appServices(): AppServices? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is AppServices) return c
        c = c.baseContext
    }
    return null
}

internal fun Context.appLang(): Lang = appServices()?.lang ?: Lang.EN

/** Calls [onTick] every minute, and when the clock or time zone is changed, while registered. */
internal class MinuteTicker(private val onTick: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = onTick()

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun unregister(context: Context) {
        runCatching { context.unregisterReceiver(this) }
    }
}
