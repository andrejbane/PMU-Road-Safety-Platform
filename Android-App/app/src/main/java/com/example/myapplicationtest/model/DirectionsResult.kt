package com.example.myapplicationtest.model

import com.google.android.gms.maps.model.LatLng

data class DirectionsResult(
    val points: List<LatLng>,
    val distance: String,
    val duration: String,
    val steps: List<NavigationStep>,
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
)
