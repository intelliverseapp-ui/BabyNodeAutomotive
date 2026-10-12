package com.babynode.automotive.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.BuildConfig
import com.babynode.automotive.CarConnectionState
import com.babynode.automotive.CarCommandDispatcher
import com.babynode.automotive.CarStatusEvent
import com.babynode.automotive.ModuleType

private const val TAG = "AutomotiveScreen"

@Composable
fun AutomotiveScreen(
    dispatcher: CarCommandDispatcher,
    moduleType: ModuleType,
    connectionState: CarConnectionState,
    status: CarStatusEvent?,
    eventHistory: List<CarStatusEvent>,
    modifier: Modifier = Modifier,
    onModuleSelected: (ModuleType) -> Unit = {},
    onApproveDevice: (String) -> Unit = {},
    onForgetDevice: () -> Unit = {},
    onRetryConnection: () -> Unit = {}
) {
    Log.i(TAG, "Render AutomotiveScreen(): moduleType=${moduleType.uiLabel}")

    var moduleExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // -------------------------------
        // Module Selector Header
        // -------------------------------
        Text(
            text = "Module Type",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        // -------------------------------
        // Module Selector Dropdown
        // -------------------------------
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        Log.i(TAG, "Module dropdown expanded")
                        moduleExpanded = true
                    }
                    .padding(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Select Module",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = moduleType.uiLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            DropdownMenu(
                expanded = moduleExpanded,
                onDismissRequest = {
                    Log.i(TAG, "Module dropdown dismissed")
                    moduleExpanded = false
                }
            ) {
                DropdownMenuItem(
                    text = { Text(ModuleType.SINGLE_CAN.uiLabel) },
                    onClick = {
                        Log.i(TAG, "Module selected → ${ModuleType.SINGLE_CAN.uiLabel}")
                        moduleExpanded = false
                        onModuleSelected(ModuleType.SINGLE_CAN)
                    }
                )
                DropdownMenuItem(
                    text = { Text(ModuleType.DUAL_CAN.uiLabel) },
                    onClick = {
                        Log.i(TAG, "Module selected → ${ModuleType.DUAL_CAN.uiLabel}")
                        moduleExpanded = false
                        onModuleSelected(ModuleType.DUAL_CAN)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------
        // Connection State
        // -------------------------------
        val connectionText = when (connectionState) {
            CarConnectionState.Disconnected -> "Disconnected"
            is CarConnectionState.Connecting -> "Connecting (${connectionState.transportName})"
            is CarConnectionState.Connected -> "Connected (${connectionState.transportName})"
            is CarConnectionState.Failed ->
                "Connection failed (${connectionState.transportName}): ${connectionState.message}"
        }

        Log.i(TAG, "ConnectionState UI → $connectionText")

        Text(
            text = connectionText,
            style = MaterialTheme.typography.bodyMedium
        )

        when (connectionState) {
            CarConnectionState.Disconnected,
            is CarConnectionState.Failed -> {
                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                Button(
                    onClick =
                        onRetryConnection
                ) {
                    Text(
                        text =
                            "Retry Connection"
                    )
                }
            }

            is CarConnectionState.Connecting,
            is CarConnectionState.Connected -> {
                // No retry control while connecting or connected.
            }
        }

        val approvalRequest =
            status as?
                CarStatusEvent.DeviceApprovalRequired

        if (approvalRequest != null) {
            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )

            Surface(
                tonalElevation =
                    2.dp,
                shape =
                    MaterialTheme.shapes.medium,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                ) {
                    Text(
                        text =
                            "Approve BabyNodeCAN",
                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )

                    Text(
                        text =
                            approvalRequest.deviceName +
                                "\n" +
                                approvalRequest.deviceAddress,
                        style =
                            MaterialTheme.typography.bodyMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                12.dp
                            )
                    )

                    Button(
                        onClick = {
                            onApproveDevice(
                                approvalRequest.deviceAddress
                            )
                        }
                    ) {
                        Text(
                            text =
                                "Approve and Connect"
                        )
                    }
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        OutlinedButton(
            onClick =
                onForgetDevice
        ) {
            Text(
                text =
                    "Forget approved BabyNodeCAN"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------
        // Voice Input Section
        // -------------------------------
        Log.i(TAG, "Render VoiceInputSection(): latestEvent=$status")
        VoiceInputSection(
            dispatcher = dispatcher,
            status = status,
            eventHistory = eventHistory
        )

        if (BuildConfig.DEBUG) {
            Spacer(
                modifier =
                    Modifier.height(
                        24.dp
                    )
            )

            // Debug-only free-text command interface.
            Log.d(
                TAG,
                "Render CommandTestSection(): latestEvent=$status"
            )

            CommandTestSection(
                dispatcher =
                    dispatcher,
                status =
                    status,
                eventHistory =
                    eventHistory
            )

            Spacer(
                modifier =
                    Modifier.height(
                        24.dp
                    )
            )

            // Debug-only transport event history.
            Log.d(
                TAG,
                "Render CanDebugSection(): " +
                    "eventHistorySize=${eventHistory.size}"
            )

            CanDebugSection(
                eventHistory =
                    eventHistory
            )
        }
    }
}
