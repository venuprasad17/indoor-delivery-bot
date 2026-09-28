package com.example.robotoperator.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.state.AuthState
import com.example.robotoperator.ui.components.BadgeColorType
import com.example.robotoperator.ui.components.KeyValRow
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    authManager: AuthManager,
    settingsStore: ServerSettingsStore,
    onNavigateEvents: () -> Unit,
    onNavigateUsers: () -> Unit,
    onNavigateServerSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val authState by authManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    val user = (authState as? AuthState.Authenticated)?.user

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "System Operations & Settings",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Slate500,
            letterSpacing = 0.5.sp
        )

        // Account Profile Card
        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Operator Account",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate900,
                    modifier = Modifier.padding(16.dp, 12.dp)
                )
                HorizontalDivider(color = Slate200, thickness = 1.dp)

                KeyValRow(label = "Name", value = user?.name ?: "Operator")
                KeyValRow(label = "Email", value = user?.email ?: "admin@robotics.local")
                KeyValRow(
                    label = "Role",
                    value = user?.role ?: "ADMIN",
                    trailingBadge = {
                        StatusBadge(
                            text = user?.role ?: "ADMIN",
                            type = BadgeColorType.GREEN
                        )
                    }
                )
                KeyValRow(
                    label = "Server address",
                    value = settingsStore.serverUrl,
                    isMonospace = true,
                    onClick = onNavigateServerSettings
                )
            }
        }

        // Navigation Menu Items
        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MoreMenuItem(
                    icon = Icons.Default.ListAlt,
                    title = "System Events",
                    subtitle = "Filter and inspect telemetry event stream logs",
                    onClick = onNavigateEvents,
                    testTag = "menu_events"
                )
                HorizontalDivider(color = Slate200, thickness = 1.dp)

                MoreMenuItem(
                    icon = Icons.Default.People,
                    title = "Users & Access",
                    subtitle = "Manage operator credentials and roles",
                    onClick = onNavigateUsers,
                    testTag = "menu_users"
                )
                HorizontalDivider(color = Slate200, thickness = 1.dp)

                MoreMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Server Settings",
                    subtitle = "Change host address and test health",
                    onClick = onNavigateServerSettings,
                    testTag = "menu_server_settings"
                )
            }
        }

        // Logout Button
        Button(
            onClick = { showLogoutDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = PureWhite,
                contentColor = EStopRed
            ),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StatusRedBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("logout_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign out", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign out?", color = Slate900) },
            text = { Text("You will return to the administrator login screen.", color = Slate600) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            authManager.logout()
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EStopRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = Slate600)
                }
            },
            containerColor = PureWhite,
            shape = RoundedCornerShape(6.dp)
        )
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Slate700, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Slate900)
            Text(text = subtitle, fontSize = 12.sp, color = Slate500)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Slate400,
            modifier = Modifier.size(14.dp)
        )
    }
}
