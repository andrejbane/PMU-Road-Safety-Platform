package com.example.myapplicationtest.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

const val BASE_URL = "http://10.0.2.2:8080"
val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
val httpClient = OkHttpClient()
