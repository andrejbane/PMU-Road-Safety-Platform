package com.example.myapplicationtest.model

data class UserProfile(
    val id: String,
    val username: String,
    val email: String,
    val name: String,
    val displayUsername: String?,
    val role: String,
    val disabled: Boolean,
)
