package com.example.myapplicationtest.model

enum class Severity(val label: String, val color: Long) {
    LOW("Low", 0xFF4CAF50),
    MEDIUM("Medium", 0xFFFFEB3B),
    HIGH("High", 0xFFFF9800),
    CANT_PASS("Can't pass", 0xFFF44336),
}
