package com.example.myapplicationtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplicationtest.ui.app.MyApplicationTestApp
import com.example.myapplicationtest.ui.theme.MyApplicationTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTestTheme {
                MyApplicationTestApp()
            }
        }
    }
}
