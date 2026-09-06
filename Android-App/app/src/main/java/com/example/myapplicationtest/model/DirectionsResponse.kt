package com.example.myapplicationtest.model

sealed class DirectionsResponse {
    data class Success(val result: DirectionsResult) : DirectionsResponse()
    data class Error(val message: String) : DirectionsResponse()
}
