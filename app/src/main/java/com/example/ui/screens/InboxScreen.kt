package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.components.CopyEmailCard
import com.example.ui.components.EmailListItem
import com.example.ui.components.EmptyInboxView
import com.example.ui.components.QrCodeDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun InboxScreen(
    viewModel: MainViewModel,
    onEmailClick: (Int) -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val activeMailbox by viewModel.activeMailbox.collectAsState()
    val activeEmails by viewModel.activeEmails.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val domains by viewModel.availableDomains.collectAsState()

    var showCustomDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    // Android 13+ Notification Permission Request
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionState = rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS
        )
        LaunchedEffect(Unit) {
            if (!notificationPermissionState.status.isGranted) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Temp Mail AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isOnline) {
                                Text(
                                    text = "Offline Mode (Cached Data)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onHistoryClick,
                        modifier = Modifier.testTag("history_nav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Saved Mailboxes"
                        )
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("settings_nav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Offline Banner
            if (!isOnline) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No internet connection. Viewing offline cached emails.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Copy Email Banner Card
                item {
                    CopyEmailCard(
                        emailAddress = activeMailbox?.address ?: "",
                        isRefreshing = isRefreshing,
                        onCopyClick = {
                            val addr = activeMailbox?.address ?: ""
                            if (addr.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(addr))
                                Toast.makeText(context, "Copied email: $addr", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onGenerateNewClick = { viewModel.generateRandomMailbox() },
                        onCreateCustomClick = { showCustomDialog = true },
                        onQrCodeClick = { showQrDialog = true },
                        onSwitchMailboxClick = onHistoryClick,
                        onManualRefreshClick = { viewModel.manualRefreshInbox() }
                    )
                }

                // Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search subject, sender, content...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_field")
                    )
                }

                // Header title for list
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inbox (${activeEmails.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (activeEmails.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearActiveInbox() }) {
                                Text("Clear Inbox", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Email List or Empty State
                if (activeEmails.isEmpty()) {
                    item {
                        EmptyInboxView(
                            emailAddress = activeMailbox?.address ?: "",
                            onSendTestEmailClick = { viewModel.sendTestVerificationEmail() }
                        )
                    }
                } else {
                    items(
                        items = activeEmails,
                        key = { it.id }
                    ) { email ->
                        EmailListItem(
                            email = email,
                            onClick = { onEmailClick(email.id) },
                            onDeleteClick = {
                                viewModel.deleteEmail(email.id, email.mailboxAddress)
                            }
                        )
                    }
                }
            }
        }
    }

    // Custom Email Creation Dialog
    if (showCustomDialog) {
        CustomEmailDialog(
            domains = domains,
            onDismiss = { showCustomDialog = false },
            onCreate = { login, domain ->
                viewModel.createCustomMailbox(login, domain)
                showCustomDialog = false
            }
        )
    }

    // QR Code Dialog
    if (showQrDialog) {
        QrCodeDialog(
            emailAddress = activeMailbox?.address ?: "",
            onDismiss = { showQrDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomEmailDialog(
    domains: List<String>,
    onDismiss: () -> Unit,
    onCreate: (login: String, domain: String) -> Unit
) {
    val orderedDomains = remember(domains) {
        val primary = listOf("1secmail.com", "1secmail.org", "1secmail.net")
        val sorted = domains.sortedByDescending { primary.contains(it) }
        if (sorted.isEmpty()) primary else sorted
    }

    var loginInput by remember { mutableStateOf("") }
    var selectedDomain by remember { mutableStateOf(orderedDomains.first()) }
    var expandedDomainMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Email") },
        text = {
            Column {
                Text(
                    text = "Choose your custom username and domain extension:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = loginInput,
                    onValueChange = { loginInput = it },
                    label = { Text("Username / Alias") },
                    placeholder = { Text("e.g. john.test") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedDomainMenu,
                    onExpandedChange = { expandedDomainMenu = !expandedDomainMenu },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "@$selectedDomain",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Domain Extension") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDomainMenu) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDomainMenu,
                        onDismissRequest = { expandedDomainMenu = false }
                    ) {
                        orderedDomains.forEach { domain ->
                            DropdownMenuItem(
                                text = { Text("@$domain") },
                                onClick = {
                                    selectedDomain = domain
                                    expandedDomainMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (loginInput.isNotBlank()) {
                        onCreate(loginInput, selectedDomain)
                    }
                },
                enabled = loginInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_custom_email_button")
            ) {
                Text("Create Email")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
