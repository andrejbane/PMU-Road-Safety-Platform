package com.example.myapplicationtest.navigation

import com.example.myapplicationtest.R

enum class AppDestinations(val label: String, val icon: Int) {
    MAP("Map", R.drawable.ic_map),
    PROFILE("Profile", R.drawable.ic_account_box),
    MODERATOR("Moderator", R.drawable.ic_shield),
}
