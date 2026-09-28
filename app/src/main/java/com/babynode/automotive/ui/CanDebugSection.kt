package com.babynode.automotive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarCanBus

@Composable
fun CanDebugSection() {

    // ⭐ CAN log buffer (updated continuously)
    val canLog = remember { mutableStateListOf<String>() }

    // ⭐ Subscribe to CAN bus updates
    LaunchedEffect(Unit) {
        CarCanBus.setListener { frame ->
            val formatted = "ID=${frame.id}  DATA=${frame.data.joinToString(" ")}"
            canLog.add(formatted)

            // ⭐ Keep log from growing forever
            if (canLog.size > 200) {
                canLog.removeFirst()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        Text(
            text = "CAN Debug",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ⭐ Scrolling CAN log window
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            for (line in canLog) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
