package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.components.CopyEmailCard
import com.example.ui.components.EmailListItem
import com.example.ui.components.EmptyInboxView
import com.example.ui.components.QrCodeDialog
import com.example.ui.components.SmsOtpMessageRow
import com.example.ui.components.TempNumberConsoleCard
import com.example.ui.theme.JetBrainsMonoFontFamily
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

    val consoleMode by viewModel.consoleMode.collectAsState()
    val activeMailbox by viewModel.activeMailbox.collectAsState()
    val activeEmails by viewModel.activeEmails.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val domains by viewModel.availableDomains.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsState()

    // Temp Number (+91 India & Public SMS Gateways) states
    val activePhoneNumber by viewModel.activePhoneNumber.collectAsState()
    val availablePhoneNumbers by viewModel.availablePhoneNumbers.collectAsState()
    val activeSmsMessages by viewModel.activeSmsMessages.collectAsState()
    val isLoadingPhoneNumbers by viewModel.isLoadingPhoneNumbers.collectAsState()
    val isSyncingSms by viewModel.isSyncingSms.collectAsState()
    val smsGatewayStatus by viewModel.smsGatewayStatus.collectAsState()
    val lastSmsSyncTime by viewModel.lastSmsSyncTime.collectAsState()
    val indiaSimStock by viewModel.indiaSimStock.collectAsState()

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
                                imageVector = if (consoleMode == "EMAIL")
                                    Icons.Default.AlternateEmail
                                else Icons.Default.PhoneAndroid,
                                contentDescription = "TempMail & OTP Console",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(7.dp)
                                    .size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (consoleMode == "EMAIL") "TempMail Console" else "TempNumber +91 Console",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (!isOnline) "OFFLINE CACHE" else "DEV • AMIT MEENA",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!isOnline) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            text = "Offline — Displaying locally cached messages.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Segmented Developer Console Switcher: SMTP Temp Mail vs +91 Temp Number
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isEmailMode = consoleMode == "EMAIL"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isEmailMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { viewModel.setConsoleMode("EMAIL") }
                        .padding(vertical = 10.dp)
                        .testTag("tab_temp_mail"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AlternateEmail,
                            contentDescription = "Temp Mail",
                            tint = if (isEmailMode) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TEMP MAIL",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isEmailMode) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (!isEmailMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { viewModel.setConsoleMode("SMS") }
                        .padding(vertical = 10.dp)
                        .testTag("tab_temp_number"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "+91 Temp Number",
                            tint = if (!isEmailMode) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+91 TEMP NUMBER",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (!isEmailMode) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (consoleMode == "EMAIL") {
                    // Top SMTP Mailbox Console Card
                    item {
                        CopyEmailCard(
                            emailAddress = activeMailbox?.address ?: "",
                            isRefreshing = isRefreshing,
                            connectionStatus = connectionStatus,
                            lastSyncedTime = lastSyncedTime,
                            onCopyClick = {
                                val addr = activeMailbox?.address ?: ""
                                if (addr.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(addr))
                                    Toast.makeText(context, "Copied: $addr", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onGenerateNewClick = { viewModel.generateRandomMailbox() },
                            onCreateCustomClick = { showCustomDialog = true },
                            onQrCodeClick = { showQrDialog = true },
                            onSwitchMailboxClick = onHistoryClick,
                            onManualRefreshClick = { viewModel.manualRefreshInbox() }
                        )
                    }

                    // Filter / Search Input
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    "Filter by sender, OTP code, or subject...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    modifier = Modifier.size(18.dp)
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_field")
                        )
                    }

                    // Inbox List Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "INCOMING EMAILS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${activeEmails.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (activeEmails.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearActiveInbox() }) {
                                    Text(
                                        "Purge All",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Real Emails List or SMTP Listener Empty Console
                    if (activeEmails.isEmpty()) {
                        item {
                            EmptyInboxView(
                                emailAddress = activeMailbox?.address ?: "",
                                onRefreshInboxClick = { viewModel.manualRefreshInbox() }
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
                } else {
                    // +91 INDIA & PUBLIC SMS OTP CONSOLE MODE
                    item {
                        val fiveSimProfileInfo by viewModel.fiveSimProfileInfo.collectAsState()
                        val hasFiveSimKey by viewModel.hasFiveSimKeyState.collectAsState()
                        TempNumberConsoleCard(
                            activeNumber = activePhoneNumber,
                            availableNumbers = availablePhoneNumbers,
                            isLoadingNumbers = isLoadingPhoneNumbers,
                            isSyncingSms = isSyncingSms,
                            smsGatewayStatus = smsGatewayStatus,
                            lastSmsSyncTime = lastSmsSyncTime,
                            hasPrivateApiKey = hasFiveSimKey,
                            fiveSimProfileInfo = fiveSimProfileInfo,
                            indiaSimStock = indiaSimStock,
                            onSelectNumber = { viewModel.selectPhoneNumber(it) },
                            onRotateNextNumber = { viewModel.rotateNextPhoneNumber() },
                            onForceSyncSms = { viewModel.syncActivePhoneSms(showToast = true) },
                            onRentPrivateIndiaSim = { serviceId ->
                                viewModel.rentPrivateIndiaSim(serviceId)
                            },
                            onReleasePrivateSim = { orderId ->
                                viewModel.releasePrivateIndiaSim(orderId)
                            },
                            onSaveFiveSimApiKey = { key ->
                                viewModel.saveFiveSimApiKey(key)
                            }
                        )
                    }

                    // Filter / Search Input for SMS OTPs
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    "Filter SMS by sender, 6-digit OTP, or app...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search SMS",
                                    modifier = Modifier.size(18.dp)
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sms_search_text_field")
                        )
                    }

                    // SMS List Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LIVE INCOMING SMS / OTP STREAM",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${activeSmsMessages.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (activeSmsMessages.isNotEmpty()) {
                                    TextButton(onClick = { viewModel.clearSmsAndWaitForNewOtp() }) {
                                        Text(
                                            "Clear Old",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    TextButton(onClick = { viewModel.restoreClearedSms() }) {
                                        Text(
                                            "Show All Live",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                TextButton(onClick = { viewModel.syncActivePhoneSms(showToast = true) }) {
                                    Text(
                                        "Refresh SMS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (activeSmsMessages.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "LISTENING FOR REAL-TIME OTP...",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Free public +91 SIMs on web gateways often go offline or block specific websites. For a 100% working Indian +91 OTP on ANY website/app, tap '+91 VIP SIM' -> Paste your 5SIM Key -> Search your website/app name -> Tap 'Get +91 SIM'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.restoreClearedSms() },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Check SMS Now",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Force Sync & Show All SMS")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = activeSmsMessages,
                            key = { it.id }
                        ) { sms ->
                            SmsOtpMessageRow(sms = sms)
                        }
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
    val displayDomains = remember(domains) {
        if (domains.isNotEmpty()) domains
        else listOf("sharklasers.com", "guerrillamailblock.com", "grr.la")
    }

    var loginInput by remember { mutableStateOf("") }
    var selectedDomain by remember(displayDomains) { mutableStateOf(displayDomains.first()) }
    var expandedDomainMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Custom Mailbox",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Configure your custom username and select a live receiving domain:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = loginInput,
                    onValueChange = { loginInput = it },
                    label = { Text("Username / Handle") },
                    placeholder = { Text("e.g. amit.verify") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
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
                        label = { Text("SMTP Domain") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDomainMenu) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDomainMenu,
                        onDismissRequest = { expandedDomainMenu = false }
                    ) {
                        displayDomains.forEachIndexed { index, domain ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "@$domain",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            )
                                        )
                                        if (index < 2) {
                                            Text(
                                                text = "OTP READY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
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
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_custom_email_button")
            ) {
                Text("Deploy Address")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
