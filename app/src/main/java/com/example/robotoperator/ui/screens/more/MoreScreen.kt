package com.example.robotoperator.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    authManager: AuthManager,
    settingsStore: ServerSettingsStore,
    onNavigateToEvents: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToServerSettings: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentUser by authManager.currentUser.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val isAdmin = currentUser?.role == "ADMIN"
        val grantedRobots by authManager.grantedRobots.collectAsState()

        // Operator Profile Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite, RoundedCornerShape(6.dp))
                .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ActionBlueSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ActionBlue
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = currentUser?.name ?: "Operator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900
                        )
                        Text(
                            text = currentUser?.email ?: "operator@local",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }

                    StatusBadge(status = currentUser?.role ?: "USER")
                }

                if (!isAdmin) {
                    HorizontalDivider(color = BorderHairline, thickness = 1.dp)
                    Text(
                        text = if (grantedRobots.isEmpty()) "Granted Robots: None (Ask an admin to grant access)" else "Granted Robots (${grantedRobots.size}): ${grantedRobots.joinToString { it.name.ifBlank { it.id.take(6) } }}",
                        fontSize = 11.sp,
                        color = if (grantedRobots.isEmpty()) StatusAmber else Slate600,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Server connection indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite, RoundedCornerShape(6.dp))
                .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                .clickable { onNavigateToServerSettings() }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = Slate600
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connected Server",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                    Text(
                        text = settingsStore.serverUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = Slate900
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Slate400
                )
            }
        }

        // System administration section
        Text(
            text = if (isAdmin) "SYSTEM & ADMINISTRATION" else "PREFERENCES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Slate500,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite, RoundedCornerShape(6.dp))
                .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
        ) {
            Column {
                if (isAdmin) {
                    MoreMenuItem(
                        icon = Icons.Default.ListAlt,
                        title = "System Events",
                        subtitle = "Audit log & system severity filters",
                        tag = "menu_events",
                        onClick = onNavigateToEvents
                    )
                    HorizontalDivider(color = BorderHairline, thickness = 1.dp)
                    MoreMenuItem(
                        icon = Icons.Default.Group,
                        title = "User & Robot Access",
                        subtitle = "Account administration & robot access permissions",
                        tag = "menu_users",
                        onClick = onNavigateToUsers
                    )
                    HorizontalDivider(color = BorderHairline, thickness = 1.dp)
                }
                MoreMenuItem(
                    icon = Icons.Default.Dns,
                    title = "Server Settings",
                    subtitle = "Endpoint configuration and health check",
                    tag = "menu_server_settings",
                    onClick = onNavigateToServerSettings
                )
            }
        }

        // Account actions
        Text(
            text = "SESSION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Slate500,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("logout_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = PureWhite,
                contentColor = StatusRed
            ),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StatusRedBorder)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Robot Operator v1.0.0 • Internal Operations",
            style = MaterialTheme.typography.labelSmall,
            color = Slate400,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out", fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to end your operator session?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        coroutineScope.launch {
                            authManager.logout()
                            onLoggedOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutDialog = false },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = PureWhite
        )
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Slate600
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Slate900
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Slate400
        )
    }
}
