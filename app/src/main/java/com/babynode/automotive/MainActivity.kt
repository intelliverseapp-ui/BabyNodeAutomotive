package com.babynode.automotive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.babynode.automotive.ui.AutomotiveScreen
import com.babynode.automotive.ui.theme.BabyNodeAutomotiveTheme

class MainActivity : ComponentActivity() {

    private val TAG = "BNA_MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // ⭐ Initialize CAN bus hardware (BNA only)
        CarCanBus.initialize(this)
        Log.i(TAG, "CarCanBus initialized")

        setContent {
            BabyNodeAutomotiveTheme {
                Scaffold(
                    modifier = Modifier
                ) { innerPadding ->
                    // ⭐ Mount the unified AutomotiveScreen
                    AutomotiveScreen()
                }
            }
        }
    }
}
