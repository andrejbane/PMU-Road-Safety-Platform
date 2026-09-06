package com.example.myapplicationtest.model

data class Vote(
    val id: String? = null,
    val voteType: String,
    val votedAt: String? = null,
    val problem: RoadProblem? = null,
    val votedBy: String? = null,
)
