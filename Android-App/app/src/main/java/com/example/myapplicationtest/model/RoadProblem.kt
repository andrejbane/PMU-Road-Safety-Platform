package com.example.myapplicationtest.model

import android.net.Uri
import com.google.android.gms.maps.model.LatLng

data class RoadProblem(
    val id: String? = null,
    val position: LatLng,
    val title: String,
    val description: String,
    val type: RoadProblemType,
    val severity: Severity = Severity.MEDIUM,
    val roadSide: RoadSide = RoadSide.BOTH,
    val directionBearing: Double? = null,
    val photoUri: Uri? = null,
    val photoBase64: String? = null,
    val isUserReport: Boolean = false,
    val official: Boolean = false,
    val reportedBy: String? = null,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val createdAt: String? = null,
    val hidden: Boolean = false,
) {
    /**
     * Bearing (degrees, 0–360) of the travel direction this problem affects,
     * or null if it affects both directions (or the direction is unknown).
     */
    val affectedBearing: Double?
        get() = when (roadSide) {
            RoadSide.BOTH -> null
            RoadSide.MY_SIDE -> directionBearing?.let { (it % 360.0 + 360.0) % 360.0 }
            RoadSide.OPPOSITE -> directionBearing?.let { ((it + 180.0) % 360.0 + 360.0) % 360.0 }
        }
}
