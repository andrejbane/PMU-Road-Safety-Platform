package com.example.myapplicationtest.util

import android.util.Log
import com.example.myapplicationtest.model.DirectionsResponse
import com.example.myapplicationtest.model.DirectionsResult
import com.example.myapplicationtest.model.NavigationStep
import com.example.myapplicationtest.model.RoadProblem
import com.example.myapplicationtest.model.RoadSide
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

fun stripHtml(html: String): String {
    return html.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun maneuverToEmoji(maneuver: String?): String {
    return when (maneuver) {
        "turn-left" -> "↰"
        "turn-right" -> "↱"
        "turn-slight-left" -> "↖"
        "turn-slight-right" -> "↗"
        "turn-sharp-left" -> "⤹"
        "turn-sharp-right" -> "⤵"
        "uturn-left", "uturn-right" -> "↩"
        "roundabout-left", "roundabout-right" -> "🔄"
        "merge" -> "⤴"
        "fork-left" -> "↖"
        "fork-right" -> "↗"
        "ramp-left" -> "↰"
        "ramp-right" -> "↱"
        "straight" -> "⬆"
        else -> "⬆"
    }
}

/**
 * A location search match. Places autocomplete suggestions carry a [placeId]
 * with no [position] until resolved via [resolveResultPosition]; other sources
 * fill [position] directly. [distanceMeters] is the straight-line distance
 * from the search origin when the source provides it.
 */
data class GeocodeResult(
    val position: LatLng?,
    val address: String,
    val placeId: String? = null,
    val distanceMeters: Double? = null,
)

/**
 * Google Places API (New) autocomplete — best-quality as-you-type suggestions
 * for addresses and POIs, biased around [near]. Billed per request.
 */
private fun placesSuggestions(
    query: String,
    near: LatLng?,
    apiKey: String?,
    maxResults: Int,
): List<GeocodeResult> {
    if (apiKey.isNullOrEmpty()) return emptyList()
    return try {
        val payload = JSONObject().apply {
            put("input", query)
            near?.let {
                val center = JSONObject().put("latitude", it.latitude).put("longitude", it.longitude)
                put("origin", center)
                put(
                    "locationBias",
                    JSONObject().put(
                        "circle",
                        JSONObject().put("center", center).put("radius", 50000.0)
                    )
                )
            }
        }
        val request = Request.Builder()
            .url("https://places.googleapis.com/v1/places:autocomplete")
            .header("X-Goog-Api-Key", apiKey)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = OkHttpClient().newCall(request).execute().body?.string() ?: return emptyList()
        val json = JSONObject(body)
        json.optJSONObject("error")?.let {
            Log.e("GeocodingAPI", "Places autocomplete error: ${it.optString("message")}")
            return emptyList()
        }
        val suggestions = json.optJSONArray("suggestions") ?: return emptyList()
        (0 until suggestions.length()).mapNotNull { i ->
            val prediction = suggestions.getJSONObject(i).optJSONObject("placePrediction")
                ?: return@mapNotNull null
            val text = prediction.optJSONObject("text")?.optString("text")
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GeocodeResult(
                position = null,
                address = text,
                placeId = prediction.optString("placeId").takeIf { it.isNotEmpty() },
                distanceMeters = if (prediction.has("distanceMeters"))
                    prediction.getDouble("distanceMeters") else null,
            )
        }.distinctBy { it.address }.take(maxResults)
    } catch (e: Exception) {
        Log.w("GeocodingAPI", "Places autocomplete failed", e)
        emptyList()
    }
}

/** Looks up a place's coordinates via Places API (New) Place Details. */
private fun placeLocation(placeId: String, apiKey: String?): LatLng? {
    if (apiKey.isNullOrEmpty()) return null
    return try {
        val request = Request.Builder()
            .url("https://places.googleapis.com/v1/places/$placeId")
            .header("X-Goog-Api-Key", apiKey)
            .header("X-Goog-FieldMask", "location")
            .build()
        val body = OkHttpClient().newCall(request).execute().body?.string() ?: return null
        val loc = JSONObject(body).optJSONObject("location") ?: return null
        LatLng(loc.getDouble("latitude"), loc.getDouble("longitude"))
    } catch (e: Exception) {
        Log.w("GeocodingAPI", "Place details lookup failed", e)
        null
    }
}

/** Coordinates for a search result, fetching Place Details if needed. */
suspend fun resolveResultPosition(result: GeocodeResult, apiKey: String?): LatLng? =
    result.position ?: withContext(Dispatchers.IO) {
        result.placeId?.let { placeLocation(it, apiKey) }
    }

/** Builds a readable one-line label from a Photon feature's properties. */
private fun photonLabel(props: JSONObject): String {
    val streetLine = listOfNotNull(
        props.optString("street").takeIf { it.isNotEmpty() },
        props.optString("housenumber").takeIf { it.isNotEmpty() },
    ).joinToString(" ")
    return listOfNotNull(
        props.optString("name").takeIf { it.isNotEmpty() },
        streetLine.takeIf { it.isNotEmpty() },
        props.optString("city").takeIf { it.isNotEmpty() },
        props.optString("state").takeIf { it.isNotEmpty() },
        props.optString("country").takeIf { it.isNotEmpty() },
    ).distinct().joinToString(", ")
}

/**
 * Free OpenStreetMap autocomplete (photon.komoot.io) — built for as-you-type
 * search, typo-tolerant, and prioritises results near [near]. No API key.
 */
private fun photonSuggestions(query: String, near: LatLng?, maxResults: Int): List<GeocodeResult> {
    return try {
        val url = "https://photon.komoot.io/api/?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&limit=${maxResults * 2}" +
                (near?.let { "&lat=${it.latitude}&lon=${it.longitude}" } ?: "")
        val request = Request.Builder().url(url)
            .header("User-Agent", "PMU-RoadProblems-App")
            .build()
        val body = OkHttpClient().newCall(request).execute().body?.string() ?: return emptyList()
        val features = JSONObject(body).getJSONArray("features")
        (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val label = photonLabel(feature.getJSONObject("properties"))
            if (label.isBlank()) null
            else GeocodeResult(LatLng(coords.getDouble(1), coords.getDouble(0)), label)
        }.distinctBy { it.address }.take(maxResults)
    } catch (e: Exception) {
        Log.w("GeocodingAPI", "Photon lookup failed", e)
        emptyList()
    }
}

private fun platformGeocoderSuggestions(
    context: android.content.Context,
    query: String,
    near: LatLng?,
    maxResults: Int,
): List<GeocodeResult> {
    if (!android.location.Geocoder.isPresent()) return emptyList()
    val geocoder = android.location.Geocoder(context)
    val raw = mutableListOf<android.location.Address>()

    if (near != null) {
        val latDelta = 2.0
        val lngDelta = 2.0 / kotlin.math.cos(Math.toRadians(near.latitude)).coerceAtLeast(0.2)
        try {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(
                query, maxResults * 2,
                near.latitude - latDelta, near.longitude - lngDelta,
                near.latitude + latDelta, near.longitude + lngDelta,
            )?.let { raw.addAll(it) }
        } catch (_: Exception) { }
    }
    try {
        @Suppress("DEPRECATION")
        geocoder.getFromLocationName(query, maxResults * 2)?.let { raw.addAll(it) }
    } catch (_: Exception) { }

    return raw.mapNotNull { addr ->
            val line = addr.getAddressLine(0) ?: return@mapNotNull null
            GeocodeResult(LatLng(addr.latitude, addr.longitude), line)
        }
        .distinctBy { it.address }
        .sortedBy { result ->
            val position = result.position
            if (near != null && position != null)
                SphericalUtil.computeDistanceBetween(near, position)
            else 0.0
        }
        .take(maxResults)
}

/**
 * Returns up to [maxResults] location matches for a partial query, closest
 * options prioritised. Google Places autocomplete results always rank first;
 * if Google returns fewer than [maxResults], the list is topped up from
 * Photon (or the platform geocoder as a last resort).
 */
suspend fun geocodeSuggestions(
    context: android.content.Context,
    query: String,
    near: LatLng? = null,
    maxResults: Int = 5,
    apiKey: String? = null,
): List<GeocodeResult> = withContext(Dispatchers.IO) {
    val google = placesSuggestions(query, near, apiKey, maxResults)
    if (google.size >= maxResults) return@withContext google

    val fallback = photonSuggestions(query, near, maxResults).takeIf { it.isNotEmpty() }
        ?: platformGeocoderSuggestions(context, query, near, maxResults)
    (google + fallback)
        .distinctBy { it.address.lowercase() }
        .take(maxResults)
}

/** Google Geocoding API lookup for a free-text address. */
private fun googleGeocode(query: String, apiKey: String): GeocodeResult? {
    return try {
        val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                "?address=${java.net.URLEncoder.encode(query, "UTF-8")}&key=$apiKey"
        val response = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        if (json.optString("status") != "OK") {
            Log.e("GeocodingAPI", "Non-OK status: ${json.optString("status")} / ${json.optString("error_message")}")
            return null
        }
        val first = json.getJSONArray("results").getJSONObject(0)
        val loc = first.getJSONObject("geometry").getJSONObject("location")
        GeocodeResult(
            position = LatLng(loc.getDouble("lat"), loc.getDouble("lng")),
            address = first.optString("formatted_address", query),
        )
    } catch (e: Exception) {
        Log.e("GeocodingAPI", "Exception geocoding \"$query\"", e)
        null
    }
}

/**
 * Resolves a free-text place/address query to coordinates, preferring matches
 * near [near]. Google first (Places autocomplete, then the Geocoding API);
 * Photon and the platform geocoder only as fallbacks.
 */
suspend fun geocodeAddress(
    context: android.content.Context,
    query: String,
    apiKey: String,
    near: LatLng? = null,
): GeocodeResult? =
    withContext(Dispatchers.IO) {
        placesSuggestions(query, near, apiKey, 1).firstOrNull()?.let { match ->
            val position = resolveResultPosition(match, apiKey)
            if (position != null) return@withContext match.copy(position = position)
        }

        googleGeocode(query, apiKey)?.let { return@withContext it }

        photonSuggestions(query, near, 1).firstOrNull()
            ?: platformGeocoderSuggestions(context, query, near, 1).firstOrNull()
    }

/**
 * Street/place description for coordinates, e.g. "Knez Mihailova 5, Beograd".
 * Google reverse geocoding first, platform geocoder as fallback.
 */
suspend fun reverseGeocode(
    context: android.content.Context,
    position: LatLng,
    apiKey: String? = null,
): String? = withContext(Dispatchers.IO) {
    if (!apiKey.isNullOrEmpty()) {
        try {
            val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                    "?latlng=${position.latitude},${position.longitude}&key=$apiKey"
            val body = OkHttpClient().newCall(Request.Builder().url(url).build())
                .execute().body?.string()
            if (body != null) {
                val json = JSONObject(body)
                if (json.optString("status") == "OK") {
                    json.getJSONArray("results").optJSONObject(0)
                        ?.optString("formatted_address")?.takeIf { it.isNotEmpty() }
                        ?.let { return@withContext it }
                }
            }
        } catch (e: Exception) {
            Log.w("GeocodingAPI", "Reverse geocode failed", e)
        }
    }

    try {
        if (android.location.Geocoder.isPresent()) {
            @Suppress("DEPRECATION")
            android.location.Geocoder(context)
                .getFromLocation(position.latitude, position.longitude, 1)
                ?.firstOrNull()?.getAddressLine(0)
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun normalizeBearing(bearing: Double): Double = (bearing % 360.0 + 360.0) % 360.0

/** Smallest angle (0–180) between two compass bearings. */
fun bearingDifference(a: Double, b: Double): Double {
    val diff = kotlin.math.abs(normalizeBearing(a) - normalizeBearing(b))
    return if (diff > 180.0) 360.0 - diff else diff
}

/** Heading (0–360) of the route segment closest to [position], or null if the route is too short. */
fun routeHeadingNear(routePoints: List<LatLng>, position: LatLng): Double? {
    if (routePoints.size < 2) return null
    var closestIdx = 0
    var minDist = Double.MAX_VALUE
    for (i in routePoints.indices) {
        val d = SphericalUtil.computeDistanceBetween(position, routePoints[i])
        if (d < minDist) {
            minDist = d
            closestIdx = i
        }
    }
    val heading = if (closestIdx < routePoints.size - 1) {
        SphericalUtil.computeHeading(routePoints[closestIdx], routePoints[closestIdx + 1])
    } else {
        SphericalUtil.computeHeading(routePoints[closestIdx - 1], routePoints[closestIdx])
    }
    return normalizeBearing(heading)
}

/**
 * True if this problem affects traffic travelling along [routeHeading].
 * Problems marked for both sides (or with unknown direction) always affect the route.
 */
fun RoadProblem.affectsTravelDirection(routeHeading: Double?): Boolean {
    val affected = affectedBearing ?: return true
    if (routeHeading == null) return true
    return bearingDifference(affected, routeHeading) <= 90.0
}

fun bearingToCardinal(bearing: Double): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(normalizeBearing(bearing) / 45.0).let { kotlin.math.round(it).toInt() } % 8]
}

/** Short human-readable description of which side of the road a problem affects. */
fun roadSideDescription(problem: RoadProblem): String {
    val affected = problem.affectedBearing
    return when {
        problem.roadSide == RoadSide.BOTH -> "Both directions"
        affected != null -> "One side — ${bearingToCardinal(affected)}-bound traffic"
        else -> "One side (direction unknown)"
    }
}

fun findProblemsNearRoute(
    routePoints: List<LatLng>,
    problems: List<RoadProblem>,
    radiusMeters: Double = 50.0,
): List<RoadProblem> {
    if (routePoints.size < 2) return emptyList()
    return problems.filter { problem ->
        PolyUtil.isLocationOnPath(problem.position, routePoints, true, radiusMeters) &&
                problem.affectsTravelDirection(routeHeadingNear(routePoints, problem.position))
    }
}

private fun parseRoute(routeJson: JSONObject): DirectionsResult {
    val legsArray = routeJson.getJSONArray("legs")
    val encodedPolyline = routeJson.getJSONObject("overview_polyline").getString("points")
    val points = PolyUtil.decode(encodedPolyline)

    var totalDistanceMeters = 0
    var totalDurationSeconds = 0
    val steps = mutableListOf<NavigationStep>()

    for (legIdx in 0 until legsArray.length()) {
        val leg = legsArray.getJSONObject(legIdx)
        totalDistanceMeters += leg.getJSONObject("distance").getInt("value")
        totalDurationSeconds += leg.getJSONObject("duration").getInt("value")

        val stepsArray = leg.getJSONArray("steps")
        for (i in 0 until stepsArray.length()) {
            val stepJson = stepsArray.getJSONObject(i)
            val startLoc = stepJson.getJSONObject("start_location")
            val endLoc = stepJson.getJSONObject("end_location")
            steps.add(
                NavigationStep(
                    instruction = stripHtml(stepJson.getString("html_instructions")),
                    distance = stepJson.getJSONObject("distance").getString("text"),
                    distanceMeters = stepJson.getJSONObject("distance").getInt("value"),
                    duration = stepJson.getJSONObject("duration").getString("text"),
                    startLocation = LatLng(startLoc.getDouble("lat"), startLoc.getDouble("lng")),
                    endLocation = LatLng(endLoc.getDouble("lat"), endLoc.getDouble("lng")),
                    maneuver = if (stepJson.has("maneuver")) stepJson.getString("maneuver") else null,
                )
            )
        }
    }

    val distance = if (totalDistanceMeters >= 1000) {
        String.format(java.util.Locale.US, "%.1f km", totalDistanceMeters / 1000.0)
    } else {
        "$totalDistanceMeters m"
    }
    val duration = when {
        totalDurationSeconds >= 3600 -> {
            val h = totalDurationSeconds / 3600
            val m = (totalDurationSeconds % 3600) / 60
            if (m > 0) "$h hour${if (h > 1) "s" else ""} $m min${if (m > 1) "s" else ""}"
            else "$h hour${if (h > 1) "s" else ""}"
        }
        else -> "${totalDurationSeconds / 60} min${if (totalDurationSeconds / 60 != 1) "s" else ""}"
    }

    return DirectionsResult(points, distance, duration, steps, totalDistanceMeters, totalDurationSeconds)
}

suspend fun fetchDirections(
    origin: LatLng,
    destination: LatLng,
    apiKey: String,
    problemsToAvoid: List<RoadProblem> = emptyList(),
    avoidRadiusMeters: Double = 50.0,
): DirectionsResponse = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        var lastStatus: String? = null
        var lastErrorMessage: String? = null

        fun fetchRoutes(url: String): List<DirectionsResult> {
            Log.d("DirectionsAPI", "URL: $url")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val status = json.optString("status")
            if (status != "OK") {
                lastStatus = status
                lastErrorMessage = json.optString("error_message", null)
                Log.e("DirectionsAPI", "Non-OK status: $status / error_message: $lastErrorMessage")
                return emptyList()
            }
            val routesArray = json.getJSONArray("routes")
            val results = mutableListOf<DirectionsResult>()
            for (i in 0 until routesArray.length()) {
                results.add(parseRoute(routesArray.getJSONObject(i)))
            }
            return results
        }

        fun scoreRoute(route: DirectionsResult): Int {
            return problemsToAvoid.count { problem ->
                PolyUtil.isLocationOnPath(problem.position, route.points, true, avoidRadiusMeters) &&
                        problem.affectsTravelDirection(routeHeadingNear(route.points, problem.position))
            }
        }

        fun pickBest(candidates: List<DirectionsResult>): DirectionsResult {
            val minHits = candidates.minOf { scoreRoute(it) }
            return candidates.filter { scoreRoute(it) == minHits }
                .minByOrNull { it.durationSeconds }
                ?: candidates.first()
        }

        val baseUrl = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=driving&alternatives=true&key=$apiKey"

        val baseCandidates = fetchRoutes(baseUrl)
        if (baseCandidates.isEmpty()) {
            val detail = when {
                lastErrorMessage != null -> "$lastStatus: $lastErrorMessage"
                lastStatus != null -> lastStatus
                else -> "No routes found"
            }
            return@withContext DirectionsResponse.Error(detail!!)
        }
        if (problemsToAvoid.isEmpty()) return@withContext DirectionsResponse.Success(
            baseCandidates.minByOrNull { it.durationSeconds } ?: baseCandidates.first()
        )

        val allCandidates = baseCandidates.toMutableList()
        val bestBase = pickBest(allCandidates)
        if (scoreRoute(bestBase) == 0) return@withContext DirectionsResponse.Success(bestBase)

        val referenceRoute = baseCandidates.minByOrNull { it.durationSeconds }!!.points
        val problemsStillOnRoute = problemsToAvoid.filter { problem ->
            PolyUtil.isLocationOnPath(problem.position, referenceRoute, true, avoidRadiusMeters) &&
                    problem.affectsTravelDirection(routeHeadingNear(referenceRoute, problem.position))
        }

        fun closestRouteHeading(routePoints: List<LatLng>, problemPos: LatLng): Double =
            routeHeadingNear(routePoints, problemPos) ?: 0.0

        val offsets = listOf(80.0, 150.0, 300.0, 500.0, 800.0)
        val angles = listOf(90.0, -90.0, 45.0, -45.0, 135.0, -135.0)

        for (problem in problemsStillOnRoute) {
            val heading = closestRouteHeading(referenceRoute, problem.position)
            for (offsetDist in offsets) {
                for (angle in angles) {
                    val nudgePoint = SphericalUtil.computeOffset(problem.position, offsetDist, heading + angle)
                    val viaUrl = "https://maps.googleapis.com/maps/api/directions/json" +
                            "?origin=${origin.latitude},${origin.longitude}" +
                            "&destination=${destination.latitude},${destination.longitude}" +
                            "&mode=driving&alternatives=true" +
                            "&waypoints=via:${nudgePoint.latitude},${nudgePoint.longitude}&key=$apiKey"
                    allCandidates.addAll(fetchRoutes(viaUrl))
                }
            }
        }

        if (problemsStillOnRoute.size > 1) {
            val combinedWaypoints = problemsStillOnRoute.joinToString("|") { problem ->
                val heading = closestRouteHeading(referenceRoute, problem.position)
                val nudge = SphericalUtil.computeOffset(problem.position, 150.0, heading + 90.0)
                "via:${nudge.latitude},${nudge.longitude}"
            }
            val combinedUrl = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&mode=driving&alternatives=true" +
                    "&waypoints=$combinedWaypoints&key=$apiKey"
            allCandidates.addAll(fetchRoutes(combinedUrl))
        }

        DirectionsResponse.Success(pickBest(allCandidates))
    } catch (e: Exception) {
        Log.e("DirectionsAPI", "Exception fetching directions", e)
        DirectionsResponse.Error("Network error: ${e.message}")
    }
}
