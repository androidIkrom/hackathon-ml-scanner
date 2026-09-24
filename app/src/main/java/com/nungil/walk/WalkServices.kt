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

    @SuppressLint("MissingPermission")
    fun start() {
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) continue
            runCatching {
                manager.getLastKnownLocation(provider)?.let {
                    if (last == null) {
                        last = LatLon(it.latitude, it.longitude)
                        accuracyM = if (it.hasAccuracy()) it.accuracy else 0f
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
}

/**
 * openrouteservice on the current host api.heigit.org (spec §1). The old host
 * api.openrouteservice.org answers 403 "Quota exceeded" for directions whatever the real quota;
 * it is used only as the geocoding fallback when the new path returns 404.
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

    override fun geocode(query: String, near: LatLon?): List<Place> {
        val params = buildString {
            append("?api_key=").append(enc(key))
            append("&text=").append(enc(query))
            append("&size=3")
            if (near != null) {
                append("&focus.point.lon=").append(near.lon).append("&focus.point.lat=").append(near.lat)
                // Only places within walking reach; the parser also drops anything farther.
                append("&boundary.circle.lon=").append(near.lon).append("&boundary.circle.lat=").append(near.lat)
                append("&boundary.circle.radius=").append(OrsJson.NEAR_KM)
            }
        }
        var answer = request(GEOCODE_URL + params, "GET", null)
        if (answer?.first == 404) answer = request(GEOCODE_FALLBACK_URL + params, "GET", null)
        val (code, body) = answer ?: return emptyList()
        if (code != 200) {
            Log.w(TAG, "openrouteservice geocode HTTP $code")
            return emptyList()
        }
        return OrsJson.parseGeocode(body, near)
    }

    /** (HTTP code, body), or null on a network failure. */
    private fun request(url: String, method: String, body: String?): Pair<Int, String>? = try {
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
        const val TAG = "Nungil"
        const val DIRECTIONS_URL = "https://api.heigit.org/openrouteservice/v2/directions/foot-walking/geojson"
        const val GEOCODE_URL = "https://api.heigit.org/openrouteservice/geocode/search"
        const val GEOCODE_FALLBACK_URL = "https://api.openrouteservice.org/geocode/search"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
