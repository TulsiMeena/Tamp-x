package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.IndiaServiceStock
import com.example.data.remote.SmsOtpMessage
import com.example.data.remote.TempPhoneNumber
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun TempNumberConsoleCard(
    activeNumber: TempPhoneNumber?,
    availableNumbers: List<TempPhoneNumber>,
    isLoadingNumbers: Boolean,
    isSyncingSms: Boolean,
    smsGatewayStatus: String,
    lastSmsSyncTime: String,
    hasPrivateApiKey: Boolean,
    fiveSimProfileInfo: String? = null,
    indiaSimStock: List<IndiaServiceStock>,
    onSelectNumber: (TempPhoneNumber) -> Unit,
    onRotateNextNumber: () -> Unit,
    onForceSyncSms: () -> Unit,
    onRentPrivateIndiaSim: (String) -> Unit,
    onReleasePrivateSim: (Long) -> Unit,
    onSaveFiveSimApiKey: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showNumberPickerDialog by remember { mutableStateOf(false) }
    var showPrivateSimGridDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("temp_number_console_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Telemetry Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSyncingSms || isLoadingNumbers) Color(0xFFF59E0B)
                                else Color(0xFF10B981)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = smsGatewayStatus,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { onForceSyncSms() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSyncingSms || isLoadingNumbers) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync SMS",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSyncingSms) "POLLING SMS..." else "SYNCED $lastSmsSyncTime",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Country & Gateway Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (activeNumber?.countryCode == "+91")
                            Color(0xFF10B981).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = activeNumber?.countryName ?: "SEARCHING GATEWAY...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (activeNumber?.countryCode == "+91")
                                Color(0xFF10B981)
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeNumber?.isPrivate == true)
                            "PRIVATE SIM • ${activeNumber.serviceName ?: "OTP"}"
                        else "PUBLIC GATEWAY • ${activeNumber?.sourceGateway ?: "LIVE"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Monospace Terminal Number Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE RECEIVING NUMBER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeNumber?.fullNumber ?: "Connecting to SMS Node...",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Active SIM",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live SIM & OTP Readiness Diagnostic Strip
            val isSimHealthy = activeNumber != null && (activeNumber.isPrivate || activeNumber.isConfirmedOnline)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sim_otp_readiness_strip"),
                color = when {
                    activeNumber == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    isSimHealthy -> Color(0xFF10B981).copy(alpha = 0.12f)
                    else -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            activeNumber == null -> "○ SCANNING GATEWAY..."
                            activeNumber.isPrivate -> "● PRIVATE +91 SIM ACTIVE • OTP READY"
                            activeNumber.isConfirmedOnline -> "● SIM ONLINE • OTP RECEIVER READY"
                            else -> "⚠ FREE +91 OFFLINE • USE +91 VIP SIM"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = when {
                            activeNumber == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            isSimHealthy -> Color(0xFF10B981)
                            else -> Color(0xFFF59E0B)
                        }
                    )

                    Text(
                        text = if (hasPrivateApiKey)
                            "5SIM KEY: CONNECTED ▸"
                        else
                            "CONNECT 5SIM KEY ▸",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (hasPrivateApiKey)
                            Color(0xFF10B981)
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showPrivateSimGridDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dual Copy Buttons: 10-Digit Only (for Indian Apps) vs Full +91 Number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val tenDigit = activeNumber?.localNumber ?: ""
                        if (tenDigit.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(tenDigit))
                            Toast.makeText(
                                context,
                                "Copied 10-Digit Number: $tenDigit",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = activeNumber != null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_10_digit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy 10-Digit",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy 10-Digit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        val full = activeNumber?.fullNumber ?: ""
                        if (full.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(full))
                            Toast.makeText(
                                context,
                                "Copied Full Number: $full",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = activeNumber != null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_full_number_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Full Number",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy ${activeNumber?.countryCode ?: "+91"} Full",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Controls: Switch Number | Next Number | Private +91 SIM Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showNumberPickerDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("switch_temp_number_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Select Number",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "All Numbers (${availableNumbers.size})",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onRotateNextNumber,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("next_temp_number_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Next SIM",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Next SIM",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = { showPrivateSimGridDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF10B981)
                    ),
                    modifier = Modifier
                        .weight(1.05f)
                        .testTag("india_private_sim_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = "+91 Private SIM",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+91 VIP SIM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Release button if currently using a rented private 5SIM number
            if (activeNumber?.isPrivate == true && activeNumber.privateOrderId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { onReleasePrivateSim(activeNumber.privateOrderId) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Cancel / Release Private +91 Order #${activeNumber.privateOrderId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // Number Picker Modal
    if (showNumberPickerDialog) {
        AlertDialog(
            onDismissRequest = { showNumberPickerDialog = false },
            title = {
                Text(
                    text = "Select Active SMS Gateway Number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Numbers marked ONLINE are actively receiving SMS right now. Free public +91 SIMs on web gateways are often offline—for guaranteed Indian +91 OTPs, use '+91 VIP SIM' (5SIM).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (availableNumbers.isEmpty()) {
                        Text(
                            text = "Fetching live numbers from gateways...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(availableNumbers, key = { it.fullNumber }) { num ->
                                val isSelected = num.fullNumber == activeNumber?.fullNumber
                                val isOnline = num.isPrivate || num.isConfirmedOnline
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSelectNumber(num)
                                            showNumberPickerDialog = false
                                        }
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = num.fullNumber,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Text(
                                                text = "${num.countryName} • ${num.sourceGateway}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            color = if (isOnline)
                                                Color(0xFF10B981).copy(alpha = 0.15f)
                                            else Color(0xFFEF4444).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = when {
                                                    num.isPrivate -> "PRIVATE +91"
                                                    isOnline -> "ONLINE"
                                                    else -> "OFFLINE"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNumberPickerDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Dedicated +91 India SIM Stock & Allocation Modal (5SIM Live Grid with 277+ Services Search)
    if (showPrivateSimGridDialog) {
        var serviceSearchQuery by remember { mutableStateOf("") }
        val filteredStock = remember(indiaSimStock, serviceSearchQuery) {
            if (serviceSearchQuery.isBlank()) {
                indiaSimStock
            } else {
                val q = serviceSearchQuery.trim()
                indiaSimStock.filter {
                    it.displayName.contains(q, ignoreCase = true) ||
                        it.serviceId.contains(q, ignoreCase = true)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showPrivateSimGridDialog = false },
            title = {
                Column {
                    Text(
                        text = "+91 India Private OTP SIM (5SIM)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = fiveSimProfileInfo ?: "LIVE TELECOM INVENTORY • ${indiaSimStock.size} +91 SERVICES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = Color(0xFF10B981)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (hasPrivateApiKey)
                            "IMPORTANT: 5SIM par har website/app ka alag OTP filter hota hai! Jis website par number dalna hai, niche uska naam search karein (ya 'Any Website' chunein) aur 'Get +91 SIM' dabayein:"
                        else
                            "Public free +91 numbers aksar offline rehte hain. Apna 5SIM API Key niche paste karein aur kisi bhi website/app ke liye 100% Real Private +91 Indian SIM lein:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // In-app 5SIM API Key Input & 1-Tap Clipboard Paste
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                placeholder = {
                                    Text(
                                        text = if (hasPrivateApiKey) "Update 5SIM JWT API Key..." else "Paste 5SIM API Key (eyJhbG...)",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fivesim_api_key_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                        if (clip.isNotBlank()) {
                                            apiKeyInput = clip
                                            onSaveFiveSimApiKey(clip)
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("paste_fivesim_key_button")
                                ) {
                                    Text(
                                        text = "Paste & Connect",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (apiKeyInput.isNotBlank()) {
                                            onSaveFiveSimApiKey(apiKeyInput)
                                        }
                                    },
                                    enabled = apiKeyInput.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_fivesim_key_button")
                                ) {
                                    Text(
                                        text = "Save 5SIM Key",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    // Search bar for 277+ Indian websites/apps on 5SIM
                    OutlinedTextField(
                        value = serviceSearchQuery,
                        onValueChange = { serviceSearchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search website/app (e.g. Google, Flipkart, Zomato, Other)...",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Service",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fivesim_service_search_input")
                    )

                    if (indiaSimStock.isEmpty()) {
                        Text(
                            text = "Querying live +91 operator stock from 5sim.net...",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredStock, key = { it.serviceId }) { stock ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stock.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${stock.availableCount} +91 SIMs • ${stock.priceRub} RUB",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = 10.sp
                                                ),
                                                color = if (stock.availableCount > 0)
                                                    Color(0xFF10B981)
                                                else MaterialTheme.colorScheme.error
                                            )
                                        }

                                        if (hasPrivateApiKey) {
                                            Button(
                                                onClick = {
                                                    onRentPrivateIndiaSim(stock.serviceId)
                                                    showPrivateSimGridDialog = false
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Get +91 SIM",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        } else {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "PASTE KEY ABOVE",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        fontSize = 9.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivateSimGridDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SmsOtpMessageRow(
    sms: SmsOtpMessage
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (sms.extractedOtp != null)
                    Color(0xFF10B981).copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("sms_otp_item_${sms.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = "SMS Sender",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sms.sender,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = sms.receivedAt,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (sms.extractedOtp != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(sms.extractedOtp))
                            Toast.makeText(
                                context,
                                "OTP Copied: ${sms.extractedOtp}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .border(
                            width = 1.dp,
                            color = Color(0xFF10B981).copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DETECTED OTP:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sms.extractedOtp,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy OTP",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TAP TO COPY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
