package com.example.myapplicationtest.model

data class User(
    val name: String,
    val email: String,
    val token: String = "",
    val displayUsername: String? = null,
    val role: String = "USER",
    val photoBase64: String? = null,
)
