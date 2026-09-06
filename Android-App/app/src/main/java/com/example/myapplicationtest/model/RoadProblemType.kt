package com.example.myapplicationtest.model

import com.example.myapplicationtest.R

enum class RoadProblemType(val color: Int, val label: String, val iconRes: Int) {
    WORK_ON_ROAD(0xFFFF9800.toInt(), "Work on the road", R.drawable.ic_marker_work),
    PROBLEM_ON_ROAD(0xFFF44336.toInt(), "Problem on the road", R.drawable.ic_marker_problem),
    OTHER(0xFF7E57C2.toInt(), "Other", R.drawable.ic_marker_info),
}
