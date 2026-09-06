package com.example.myapplicationtest.model

import com.google.android.gms.maps.model.LatLng

data class NavigationStep(
    val instruction: String,
    val distance: String,
    val distanceMeters: Int,
    val duration: String,
    val startLocation: LatLng,
    val endLocation: LatLng,
    val maneuver: String?,
)
