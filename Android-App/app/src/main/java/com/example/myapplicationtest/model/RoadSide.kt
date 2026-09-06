package com.example.myapplicationtest.model

/**
 * Which side of the road a problem affects, relative to the reporter's
 * direction of travel (stored as [RoadProblem.directionBearing]).
 */
enum class RoadSide(val label: String) {
    BOTH("Both sides"),
    MY_SIDE("My side"),
    OPPOSITE("Opposite side");

    companion object {
        fun from(value: String?): RoadSide =
            entries.firstOrNull { it.name == value } ?: BOTH
    }
}
