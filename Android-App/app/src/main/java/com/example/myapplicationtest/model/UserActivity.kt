package com.example.myapplicationtest.model

data class UserActivity(
    val user: UserProfile,
    val problems: List<RoadProblem>,
    val votes: List<Vote>,
)
