package com.example.robotoperator.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.CreateUserRequest
import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.model.UpdateUserRequest
import com.example.robotoperator.data.model.User
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    apiClient: ApiClient,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("USER") }

    // Selected user for detail view
    var selectedUser by remember { mutableStateOf<User?>(null) }

    fun loadUsers() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val service = apiClient.getService()
                users = service.getUsers()
            } catch (e: Exception) {
                errorMessage = ApiErrorParser.parseErrorMessage(e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadUsers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "User & Robot Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("users_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadUsers() },
                        modifier = Modifier.testTag("users_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh users"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newEmail = ""
                    newPassword = ""
                    newName = ""
                    newRole = "USER"
                    showCreateDialog = true
                },
                containerColor = ActionBlue,
                contentColor = PureWhite,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("add_user_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add User"
                )
            }
        },
        containerColor = SurfaceCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalDivider(color = BorderHairline, thickness = 1.dp)

            if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusRedBg)
                        .border(1.dp, StatusRedBorder, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = StatusRed,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (isLoading && users.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ActionBlue)
                }
            } else if (users.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No users found or error loading accounts",
                            color = Slate500,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { loadUsers() },
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("users_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(users, key = { it.id }) { user ->
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                                .clickable { selectedUser = user }
                                .testTag("user_item_${user.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (user.role == "ADMIN") ActionBlueSubtle else Slate100),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (user.role == "ADMIN") Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (user.role == "ADMIN") ActionBlue else Slate600
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = user.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = Slate900
                                        )
                                        StatusBadge(status = user.role)
                                        if (!user.is_active) {
                                            StatusBadge(status = "DEACTIVATED")
                                        }
                                    }
                                    Text(
                                        text = user.email,
                                        color = Slate600,
                                        fontSize = 13.sp
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Manage user",
                                    tint = Slate400
                                )
                            }
                        }
                    }
                }
            }
        }

        // Create User Dialog (Direct Admin Provisioning)
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create New User") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = { newEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Role:", fontSize = 13.sp)
                            listOf("USER", "ADMIN").forEach { r ->
                                FilterChip(
                                    selected = newRole == r,
                                    onClick = { newRole = r },
                                    label = { Text(r) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCreateDialog = false
                            coroutineScope.launch {
                                try {
                                    apiClient.getService().createUser(
                                        CreateUserRequest(
                                            email = newEmail.trim(),
                                            password = newPassword,
                                            name = newName.trim(),
                                            role = newRole
                                        )
                                    )
                                    loadUsers()
                                } catch (e: Exception) {
                                    errorMessage = ApiErrorParser.parseErrorMessage(e)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Per-User Detail View with Robot Access Management
        selectedUser?.let { user ->
            UserDetailDialog(
                user = user,
                apiClient = apiClient,
                onDismiss = { selectedUser = null },
                onUserUpdated = {
                    loadUsers()
                    selectedUser = null
                }
            )
        }
    }
}

@Composable
fun UserDetailDialog(
    user: User,
    apiClient: ApiClient,
    onDismiss: () -> Unit,
    onUserUpdated: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var grantedRobots by remember { mutableStateOf<List<RobotStatus>>(emptyList()) }
    var isLoadingRobots by remember { mutableStateOf(true) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var robotIdToGrant by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }

    fun loadUserRobots() {
        coroutineScope.launch {
            isLoadingRobots = true
            try {
                grantedRobots = apiClient.getService().getUserRobots(user.id)
            } catch (e: Exception) {
                detailError = ApiErrorParser.parseErrorMessage(e)
            } finally {
                isLoadingRobots = false
            }
        }
    }

    LaunchedEffect(user.id) {
        loadUserRobots()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = user.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            text = user.email,
                            fontSize = 13.sp,
                            color = Slate500
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (!detailError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(StatusRedBg)
                            .border(1.dp, StatusRedBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = detailError!!,
                            color = StatusRed,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                HorizontalDivider(color = BorderHairline, thickness = 1.dp)

                // Role and Status Management
                Text(
                    text = "ACCOUNT STATUS & ROLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Role: ${user.role}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Button(
                        onClick = {
                            val newRole = if (user.role == "ADMIN") "USER" else "ADMIN"
                            coroutineScope.launch {
                                isOperating = true
                                detailError = null
                                try {
                                    apiClient.getService().updateUser(
                                        user.id,
                                        UpdateUserRequest(role = newRole)
                                    )
                                    onUserUpdated()
                                } catch (e: Exception) {
                                    detailError = ApiErrorParser.parseErrorMessage(e)
                                } finally {
                                    isOperating = false
                                }
                            }
                        },
                        enabled = !isOperating,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = Slate900),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (user.role == "ADMIN") "Demote to USER" else "Promote to ADMIN", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (user.is_active) "Status: Active" else "Status: Deactivated",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isOperating = true
                                detailError = null
                                try {
                                    apiClient.getService().updateUser(
                                        user.id,
                                        UpdateUserRequest(is_active = !user.is_active)
                                    )
                                    onUserUpdated()
                                } catch (e: Exception) {
                                    detailError = ApiErrorParser.parseErrorMessage(e)
                                } finally {
                                    isOperating = false
                                }
                            }
                        },
                        enabled = !isOperating,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (user.is_active) "Deactivate" else "Activate", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = BorderHairline, thickness = 1.dp)

                // Robot Access Management Section
                Text(
                    text = "GRANTED ROBOT ACCESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )

                if (isLoadingRobots) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (grantedRobots.isEmpty()) {
                    Text(
                        text = "No robots currently granted to this user. User cannot dispatch deliveries.",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        grantedRobots.forEach { robot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Slate100)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = robot.name.ifBlank { "Robot ${robot.id.take(8)}" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "ID: ${robot.id}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate600
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isOperating = true
                                            detailError = null
                                            try {
                                                apiClient.getService().revokeRobotAccess(user.id, robot.id)
                                                loadUserRobots()
                                            } catch (e: Exception) {
                                                detailError = ApiErrorParser.parseErrorMessage(e)
                                            } finally {
                                                isOperating = false
                                            }
                                        }
                                    },
                                    enabled = !isOperating,
                                    colors = ButtonDefaults.textButtonColors(contentColor = StatusRed)
                                ) {
                                    Text("Revoke", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Grant access to a robot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = robotIdToGrant,
                        onValueChange = { robotIdToGrant = it },
                        placeholder = { Text("Robot ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Button(
                        onClick = {
                            if (robotIdToGrant.isNotBlank()) {
                                coroutineScope.launch {
                                    isOperating = true
                                    detailError = null
                                    try {
                                        apiClient.getService().grantRobotAccess(user.id, robotIdToGrant.trim())
                                        robotIdToGrant = ""
                                        loadUserRobots()
                                    } catch (e: Exception) {
                                        detailError = ApiErrorParser.parseErrorMessage(e)
                                    } finally {
                                        isOperating = false
                                    }
                                }
                            }
                        },
                        enabled = robotIdToGrant.isNotBlank() && !isOperating,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text("Grant")
                    }
                }

                HorizontalDivider(color = BorderHairline, thickness = 1.dp)

                // Delete Account
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isOperating = true
                                detailError = null
                                try {
                                    apiClient.getService().deleteUser(user.id)
                                    onUserUpdated()
                                } catch (e: Exception) {
                                    detailError = ApiErrorParser.parseErrorMessage(e)
                                } finally {
                                    isOperating = false
                                }
                            }
                        },
                        enabled = !isOperating,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusRedBorder)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete / Deactivate User")
                    }
                }
            }
        }
    }
}
