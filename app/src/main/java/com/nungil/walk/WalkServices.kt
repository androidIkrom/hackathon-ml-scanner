package com.nungil.walk

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.nungil.core.walk.LatLon
import com.nungil.core.walk.OrsJson
import com.nungil.core.walk.Place
import com.nungil.core.walk.PlacesCodec
import com.nungil.core.walk.Route
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Saved places ("save this place as home") in SharedPreferences. */
class PlaceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("walk_places", Context.MODE_PRIVATE)

    fun all(): List<Place> = PlacesCodec.decode(prefs.getString(KEY, null))

    fun save(place: Place) = prefs.edit().putString(KEY, PlacesCodec.encode(PlacesCodec.put(all(), place))).apply()

    fun find(name: String): Place? = PlacesCodec.find(all(), name)

    private companion object {
        const val KEY = "places"
    }
}

/** Go-mode settings the walker sets by voice: "quiet updates" stays off until "updates on", walk after walk. */
class GoSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("walk_go", Context.MODE_PRIVATE)

    var quietUpdates: Boolean
        get() = prefs.getBoolean(KEY_QUIET, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET, value).apply()

    private companion object {
        const val KEY_QUIET = "quiet_updates"
    }
}

/** GPS and network location on the main thread. The caller checks the permission first. */
class LocationTracker(context: Context, private val onFix: (LatLon) -> Unit) : LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    var last: LatLon? = null
        private set

    /** Accuracy of [last] in metres (0 when unknown). */
    @Volatile
    var accuracyM: Float = 0f
        private set

    /** When [last] was measured (elapsed realtime, ms): the last known place can be minutes old (FixAge). */
    @Volatile
    var fixAtMs: Long? = null
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) continue
            runCatching {
                manager.getLastKnownLocation(provider)?.let {
                    if (last == null) {
                        last = LatLon(it.latitude, it.longitude)
                        accuracyM = if (it.hasAccuracy()) it.accuracy else 0f
                        fixAtMs = it.elapsedRealtimeNanos / 1_000_000
                    }
                }
                manager.requestLocationUpdates(provider, INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper())
            }.onFailure { Log.w(TAG, "location $provider", it) }
        }
    }

    fun stop() {
        runCatching { manager.removeUpdates(this) }
    }

    override fun onLocationChanged(location: Location) {
        val p = LatLon(location.latitude, location.longitude)
        last = p
        accuracyM = if (location.hasAccuracy()) location.accuracy else 0f
        fixAtMs = location.elapsedRealtimeNanos / 1_000_000
        onFix(p)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private companion object {
        const val TAG = "Nungil"
        const val INTERVAL_MS = 1_000L
        const val MIN_DISTANCE_M = 1f
    }
}

sealed interface RouteResult {
    data class Ok(val route: Route) : RouteResult
    /** No walking route (empty features, or the destination is outside the routable data). */
    data object NoRoute : RouteResult
    /** HTTP 429: do not ask again for 60 s. */
    data object TooManyRequests : RouteResult
    /** No network, timeout, IO error, bad key, deprecated host. */
    data object Failed : RouteResult
}

/** A walking-route provider. Blocking calls: run them off the main and GL threads. */
interface RouteSource {
    fun route(from: LatLon, to: Place): RouteResult

    /** Empty on any failure. */
    fun geocode(query: String, near: LatLon?): List<Place>

    /** The same search without the walking-reach limit, to tell "too far" from "no such place". */
    fun geocodeFar(query: String, near: LatLon?): List<Place> = emptyList()

    /** True when the last place search got no answer at all (no network, quota used up). */
    val searchDown: Boolean get() = false

    /** The street at [at] (or the name of what is there), for "where am I"; null on any failure. */
    fun reverse(at: LatLon): String? = null
}

/**
 * openrouteservice on the current host api.heigit.org (spec §1). The old host
 * api.openrouteservice.org answers 403 "Quota exceeded" whatever the real quota, first for directions
 * and later for place search too (every search failed for an evening, because the path used on the
 * current host did not exist and all of them went to the old one). It stays only as the fallback when
 * the current path returns 404.
 */
class OrsRouteSource(private val key: String) : RouteSource {

    override fun route(from: LatLon, to: Place): RouteResult {
        val (code, body) = request(DIRECTIONS_URL, "POST", OrsJson.routeRequest(from, to.point)) ?: return RouteResult.Failed
        return when {
            code == 200 -> OrsJson.parseRoute(body, to)?.let { RouteResult.Ok(it) } ?: RouteResult.NoRoute
            code == 404 -> RouteResult.NoRoute
            code == 429 -> RouteResult.TooManyRequests
            OrsJson.isQuotaError(code, body) -> {
                Log.e(TAG, "openrouteservice 403 Quota exceeded: this is the deprecated host, use $DIRECTIONS_URL")
                RouteResult.Failed
            }
            else -> {
                Log.w(TAG, "openrouteservice route HTTP $code")
                RouteResult.Failed
            }
        }
    }

    /** Answers of this session by query: the day's quota is small (it ran out during a test with repeats). */
    private val answers = java.util.concurrent.ConcurrentHashMap<String, List<Place>>()

    /** The current host's path is gone (404): after the first try only the old host is asked. */
    @Volatile private var skipCurrentHost = false

    @Volatile override var searchDown = false
        private set

    override fun geocode(query: String, near: LatLon?): List<Place> = geocode(query, near, reach = true)

    override fun geocodeFar(query: String, near: LatLon?): List<Place> = geocode(query, near, reach = false)

    private fun geocode(query: String, near: LatLon?, reach: Boolean): List<Place> {
        val cacheKey = "${query.trim().lowercase()}|$reach"
        answers[cacheKey]?.let { return it }
        val params = buildString {
            append("?api_key=").append(enc(key))
            append("&text=").append(enc(query))
            append("&size=3")
            if (near != null) {
                append("&focus.point.lon=").append(near.lon).append("&focus.point.lat=").append(near.lat)
                if (reach) {
                    // Only places within walking reach; the parser also drops anything farther.
                    append("&boundary.circle.lon=").append(near.lon).append("&boundary.circle.lat=").append(near.lat)
                    append("&boundary.circle.radius=").append(OrsJson.NEAR_KM)
                }
            }
        }
        var answer = if (skipCurrentHost) null else request(GEOCODE_URL + params, "GET", null)
        if (skipCurrentHost || answer?.first == 404) {
            skipCurrentHost = true
            answer = request(GEOCODE_FALLBACK_URL + params, "GET", null)
        }
        searchDown = answer?.first != 200
        val (code, body) = answer ?: return emptyList()
        if (code != 200) {
            Log.w(TAG, "openrouteservice geocode HTTP $code" + if (OrsJson.isQuotaError(code, body)) " (quota exceeded)" else "")
            return emptyList()
        }
        return OrsJson.parseGeocode(body, near.takeIf { reach }).also { answers[cacheKey] = it }
    }

    /** The current host's reverse path is gone (404). Its own flag: a 404 here must not send place search to the old host. */
    @Volatile private var skipCurrentReverseHost = false

    /** Pelias reverse on the current host, the old host when that path is gone, as [geocode] does. */
    override fun reverse(at: LatLon): String? {
        val params = "?api_key=${enc(key)}&point.lat=${at.lat}&point.lon=${at.lon}&size=1"
        var answer = if (skipCurrentReverseHost) null else request(REVERSE_URL + params, "GET", null)
        if (skipCurrentReverseHost || answer?.first == 404) {
            skipCurrentReverseHost = true
            answer = request(REVERSE_FALLBACK_URL + params, "GET", null)
        }
        val (code, body) = answer ?: return null
        if (code != 200) {
            Log.w(TAG, "openrouteservice reverse HTTP $code")
            return null
        }
        return OrsJson.parseReverse(body)
    }

    /**
     * (HTTP code, body), or null on a network failure. One retry after [RETRY_MS]: walking outdoors the
     * phone hops between Wi-Fi networks and a request in the gap fails with UnknownHostException.
     */
    private fun request(url: String, method: String, body: String?): Pair<Int, String>? =
        requestOnce(url, method, body) ?: run {
            Thread.sleep(RETRY_MS)
            requestOnce(url, method, body)
        }

    private fun requestOnce(url: String, method: String, body: String?): Pair<Int, String>? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = CONNECT_TIMEOUT_MS
            c.readTimeout = READ_TIMEOUT_MS
            if (method == "POST") {
                c.setRequestProperty("Authorization", key)
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Accept", "application/geo+json")
                c.doOutput = true
                c.outputStream.use { it.write(body.orEmpty().toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "openrouteservice request failed: ${e.javaClass.simpleName}")
        null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val RETRY_MS = 2_000L
        const val TAG = "Nungil"
        const val DIRECTIONS_URL = "https://api.heigit.org/openrouteservice/v2/directions/foot-walking/geojson"
        const val GEOCODE_URL = "https://api.heigit.org/pelias/v1/search"
        const val GEOCODE_FALLBACK_URL = "https://api.openrouteservice.org/geocode/search"
        const val REVERSE_URL = "https://api.heigit.org/pelias/v1/reverse"
        const val REVERSE_FALLBACK_URL = "https://api.openrouteservice.org/geocode/reverse"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
