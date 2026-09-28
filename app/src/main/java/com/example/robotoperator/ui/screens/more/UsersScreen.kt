package com.example.robotoperator.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.CreateUserRequest
import com.example.robotoperator.data.model.UpdateUserRequest
import com.example.robotoperator.data.model.User
import com.example.robotoperator.ui.components.BadgeColorType
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }

    val scope = rememberCoroutineScope()

    val fetchUsers: suspend () -> Unit = {
        try {
            val service = apiClient.getService()
            val list = withContext(Dispatchers.IO) { service.getUsers() }
            users = list
            errorMessage = null
        } catch (e: Exception) {
            val err = ApiErrorParser.parse(e)
            errorMessage = err.message
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        fetchUsers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Users & Operators", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Slate900) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("users_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Slate700)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.testTag("add_user_button")) {
                        Icon(Icons.Default.Add, contentDescription = "Add user", tint = PrimaryBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("users_screen")
        ) {
            if (!errorMessage.isNullOrEmpty()) {
                Text(
                    text = errorMessage!!,
                    color = StatusRedText,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusRedBg)
                        .padding(12.dp)
                )
                HorizontalDivider(color = StatusRedBorder, thickness = 1.dp)
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch { fetchUsers() }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading && users.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                    }
                } else if (users.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No users found", color = Slate500, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("users_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users, key = { it.id }) { user ->
                            UserItemCard(
                                user = user,
                                onToggleActive = { newActive ->
                                    scope.launch {
                                        try {
                                            val s = apiClient.getService()
                                            withContext(Dispatchers.IO) {
                                                s.updateUser(user.id, UpdateUserRequest(is_active = newActive))
                                            }
                                            fetchUsers()
                                        } catch (e: Exception) {
                                            errorMessage = ApiErrorParser.parse(e).message
                                        }
                                    }
                                },
                                onDelete = { userToDelete = user }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateUserDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { email, pass, name, role ->
                scope.launch {
                    try {
                        val s = apiClient.getService()
                        withContext(Dispatchers.IO) {
                            s.createUser(CreateUserRequest(email, pass, name, role))
                        }
                        showCreateDialog = false
                        fetchUsers()
                    } catch (e: Exception) {
                        errorMessage = ApiErrorParser.parse(e).message
                    }
                }
            }
        )
    }

    if (userToDelete != null) {
        val u = userToDelete!!
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete user?", color = Slate900) },
            text = { Text("Delete '${u.email}' permanently? They will immediately lose system access.", color = Slate600) },
            confirmButton = {
                Button(
                    onClick = {
                        val id = u.id
                        userToDelete = null
                        scope.launch {
                            try {
                                val s = apiClient.getService()
                                withContext(Dispatchers.IO) { s.deleteUser(id) }
                                fetchUsers()
                            } catch (e: Exception) {
                                errorMessage = ApiErrorParser.parse(e).message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EStopRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("confirm_delete_user")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Cancel", color = Slate600) }
            },
            containerColor = PureWhite,
            shape = RoundedCornerShape(6.dp)
        )
    }
}

@Composable
private fun UserItemCard(
    user: User,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("user_row_${user.id}"),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name ?: user.email,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(
                        text = user.role,
                        type = if (user.role.equals("ADMIN", ignoreCase = true)) BadgeColorType.GREEN else BadgeColorType.GREY
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusBadge(
                        text = if (user.is_active) "ACTIVE" else "DISABLED",
                        type = if (user.is_active) BadgeColorType.GREEN else BadgeColorType.RED
                    )
                }

                Text(
                    text = user.email,
                    fontSize = 12.sp,
                    color = Slate500
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = user.is_active,
                    onCheckedChange = onToggleActive,
                    modifier = Modifier.testTag("toggle_user_active_${user.id}")
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_user_${user.id}")) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete user", tint = EStopRed)
                }
            }
        }
    }
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (email: String, pass: String, name: String, role: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("ADMIN") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("create_user_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "New Administrator User",
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate900,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().testTag("user_name_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().testTag("user_email_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().testTag("user_password_input")
                )

                if (!validationError.isNullOrEmpty()) {
                    Text(text = validationError!!, color = StatusRedText, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(4.dp)) {
                        Text("Cancel", color = Slate600)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank() || name.isBlank()) {
                                validationError = "All fields are required"
                                return@Button
                            }
                            onCreate(email.trim(), password, name.trim(), role)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("confirm_create_user")
                    ) {
                        Text("Create user")
                    }
                }
            }
        }
    }
}
