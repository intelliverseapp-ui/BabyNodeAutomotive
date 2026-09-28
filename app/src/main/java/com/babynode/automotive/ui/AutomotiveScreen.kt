package com.babynode.automotive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AutomotiveScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // -------------------------------
        // Voice Input Section (real)
        // -------------------------------
        VoiceInputSection()
        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------
        // Command Test Section (real)
        // -------------------------------
        CommandTestSection()
        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------
        // CAN Debug Section (real)
        // -------------------------------
        CanDebugSection()
    }
}
