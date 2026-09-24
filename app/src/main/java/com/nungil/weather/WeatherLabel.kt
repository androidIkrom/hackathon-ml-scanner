package com.nungil.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.nungil.R
import com.nungil.contract.Lang
import com.nungil.contract.app.AppServices
import com.nungil.core.weather.Clock
import com.nungil.core.weather.Today
import com.nungil.core.weather.Weather
import com.nungil.core.weather.WeatherPhrases
import com.nungil.design.resolveColorAttr
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Small "18° Cloudy" label for the top right of the app bar, with today's date under it ("Thu, Sep 25" /
 * "9월 25일 (목)", updated at midnight). It shows the last stored report at once, fetches a
 * new one from Open-Meteo when that is over 30 minutes old (the only network use besides walk routes), and speaks
 * the full report when tapped. Without the location permission it reads "Weather" and asks for it on tap.
 * Put it in the toolbar: `<com.nungil.weather.WeatherLabel android:layout_gravity="end" … />`.
 */
class WeatherLabel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var today: Today? = null
    private var fetchedAtMs: Long? = null
    private var fetching = false
    private val ticker = MinuteTicker { show() }

    init {
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Nungil_Label)
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        minHeight = resources.getDimensionPixelSize(R.dimen.ng_touch)
        val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
        setPadding(pad, 0, pad, 0)
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap() }
        loadStored()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.register(context)
        show()
        refreshIfStale()
    }

    override fun onDetachedFromWindow() {
        ticker.unregister(context)
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Coming back from the permission dialog or from the background.
        if (hasWindowFocus) refreshIfStale()
    }

    private fun services(): AppServices? = context.appServices()

    private fun lang(): Lang = context.appLang()

    private fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun onTap() {
        val t = today
        val speaker = services()?.speaker
        when {
            t != null -> speaker?.say(dateSpoken() + " " + WeatherPhrases.spoken(t, lang(), ageMs()))
            !hasLocation() -> (services() as? Activity)?.let {
                speaker?.say(context.getString(R.string.weather_needs_location))
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)
            }
            else -> speaker?.say(context.getString(R.string.weather_not_yet))
        }
        refreshIfStale()
    }

    private fun ageMs(): Long = fetchedAtMs?.let { System.currentTimeMillis() - it } ?: 0L

    private fun show() {
        val t = today
        val now = Now.read()
        val weather = t?.let { WeatherPhrases.label(it, lang()) } ?: context.getString(R.string.weather_title)
        val date = Clock.dateLabel(now.month, now.day, now.dayOfWeek, lang())
        // Two lines, right-aligned: the weather, and the date under it in the smaller, softer caption style.
        text = SpannableStringBuilder(weather).append('\n').apply {
            val start = length
            append(date)
            setSpan(RelativeSizeSpan(DATE_SCALE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val soft = ForegroundColorSpan(context.resolveColorAttr(R.attr.ngTextSub))
            setSpan(soft, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        contentDescription = dateSpoken() + " " +
            (t?.let { WeatherPhrases.spoken(it, lang(), ageMs()) } ?: context.getString(R.string.weather_title))
    }

    private fun dateSpoken(): String {
        val now = Now.read()
        return Clock.dateSpoken(now.month, now.day, now.dayOfWeek, lang())
    }

    private fun loadStored() {
        if (prefs.contains(KEY_AT)) {
            today = Today(
                nowC = prefs.getInt(KEY_NOW, 0),
                code = prefs.getInt(KEY_CODE, 3),
                highC = prefs.getInt(KEY_HIGH, 0),
                lowC = prefs.getInt(KEY_LOW, 0),
                rainChance = prefs.getInt(KEY_RAIN, -1).takeIf { it >= 0 },
            )
            fetchedAtMs = prefs.getLong(KEY_AT, 0L)
        }
        show()
    }

    private fun refreshIfStale() {
        if (fetching || !hasLocation() || !Weather.needsRefresh(System.currentTimeMillis(), fetchedAtMs)) return
        val place = lastLocation() ?: return
        fetching = true
        worker.execute {
            val fresh = try {
                Weather.parse(download(Weather.url(place.latitude, place.longitude)))
            } catch (e: Exception) {
                Log.i(TAG, "Weather fetch failed: ${e.message}")
                null
            }
            post {
                fetching = false
                if (fresh != null) store(fresh)
            }
        }
    }

    private fun store(fresh: Today) {
        val now = System.currentTimeMillis()
        today = fresh
        fetchedAtMs = now
        prefs.edit()
            .putInt(KEY_NOW, fresh.nowC)
            .putInt(KEY_CODE, fresh.code)
            .putInt(KEY_HIGH, fresh.highC)
            .putInt(KEY_LOW, fresh.lowC)
            .putInt(KEY_RAIN, fresh.rainChance ?: -1)
            .putLong(KEY_AT, now)
            .apply()
        show()
    }

    /** The freshest last-known fix of any provider; weather needs only the town, so no GPS wait. */
    @SuppressLint("MissingPermission")
    private fun lastLocation(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos.coerceAtMost(SystemClock.elapsedRealtimeNanos()) }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "Nungil"
        const val PREFS = "weather"
        const val KEY_NOW = "now"
        const val KEY_CODE = "code"
        const val KEY_HIGH = "high"
        const val KEY_LOW = "low"
        const val KEY_RAIN = "rain"
        const val KEY_AT = "fetched_at"
        const val TIMEOUT_MS = 8_000

        /** The date line is caption-sized (15sp) under the 18sp label. */
        const val DATE_SCALE = 15f / 18f

        /** One background thread for the whole app; a fetch is a single small request. */
        val worker = Executors.newSingleThreadExecutor()
    }
}
