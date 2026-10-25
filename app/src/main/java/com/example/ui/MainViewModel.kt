package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.EmailEntity
import com.example.data.local.MailboxEntity
import com.example.data.local.PreferencesManager
import com.example.data.remote.GeminiApiService
import com.example.data.remote.IndiaServiceStock
import com.example.data.remote.SmsOtpMessage
import com.example.data.remote.TempMailApiService
import com.example.data.remote.TempNumberService
import com.example.data.remote.TempPhoneNumber
import com.example.data.repository.TempMailRepository
import com.example.util.NetworkObserver
import com.example.util.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val apiService = TempMailApiService.create()
    private val geminiService = GeminiApiService()
    private val notificationHelper = NotificationHelper(application)
    private val preferencesManager = PreferencesManager(application)
    private val networkObserver = NetworkObserver(application)
    private val tempNumberService = TempNumberService()

    val repository = TempMailRepository(
        dao = db.tempMailDao(),
        apiService = apiService,
        geminiService = geminiService,
        notificationHelper = notificationHelper
    )

    val isOnline: StateFlow<Boolean> = networkObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val activeMailbox: StateFlow<MailboxEntity?> = repository.activeMailbox
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allMailboxes: StateFlow<List<MailboxEntity>> = repository.allMailboxes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Console Mode: "EMAIL" or "SMS"
    private val _consoleMode = MutableStateFlow("EMAIL")
    val consoleMode: StateFlow<String> = _consoleMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow("READY")
    val lastSyncedTime: StateFlow<String> = _lastSyncedTime.asStateFlow()

    private val _connectionStatus = MutableStateFlow("SMTP NODE ONLINE")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _isAiAnalyzing = MutableStateFlow(false)
    val isAiAnalyzing: StateFlow<Boolean> = _isAiAnalyzing.asStateFlow()

    val availableDomains = MutableStateFlow<List<String>>(emptyList())

    // Temp Number (+91 India & Live Public Gateways) State
    private val _availablePhoneNumbers = MutableStateFlow<List<TempPhoneNumber>>(emptyList())
    val availablePhoneNumbers: StateFlow<List<TempPhoneNumber>> = _availablePhoneNumbers.asStateFlow()

    private val _activePhoneNumber = MutableStateFlow<TempPhoneNumber?>(null)
    val activePhoneNumber: StateFlow<TempPhoneNumber?> = _activePhoneNumber.asStateFlow()

    private val _rawSmsMessages = MutableStateFlow<List<SmsOtpMessage>>(emptyList())

    val activeSmsMessages: StateFlow<List<SmsOtpMessage>> = combine(
        _rawSmsMessages,
        _searchQuery
    ) { list, q ->
        if (q.isBlank()) list
        else list.filter { sms ->
            sms.sender.contains(q, ignoreCase = true) ||
                sms.body.contains(q, ignoreCase = true) ||
                (sms.extractedOtp?.contains(q, ignoreCase = true) == true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _indiaSimStock = MutableStateFlow<List<IndiaServiceStock>>(emptyList())
    val indiaSimStock: StateFlow<List<IndiaServiceStock>> = _indiaSimStock.asStateFlow()

    private val _fiveSimProfileInfo = MutableStateFlow<String?>(null)
    val fiveSimProfileInfo: StateFlow<String?> = _fiveSimProfileInfo.asStateFlow()

    private val _isLoadingPhoneNumbers = MutableStateFlow(false)
    val isLoadingPhoneNumbers: StateFlow<Boolean> = _isLoadingPhoneNumbers.asStateFlow()

    private val _isSyncingSms = MutableStateFlow(false)
    val isSyncingSms: StateFlow<Boolean> = _isSyncingSms.asStateFlow()

    private val _smsGatewayStatus = MutableStateFlow("SMS GATEWAY READY")
    val smsGatewayStatus: StateFlow<String> = _smsGatewayStatus.asStateFlow()

    private val _lastSmsSyncTime = MutableStateFlow("READY")
    val lastSmsSyncTime: StateFlow<String> = _lastSmsSyncTime.asStateFlow()

    private val userFiveSimKeyFlow: StateFlow<String> = preferencesManager.userFiveSimApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val buildConfigFiveSimKey: String
        get() = try {
            val raw = BuildConfig.FIVESIM_API_KEY
            if (raw.isNotBlank() && raw != "MY_FIVESIM_API_KEY" && raw != "null") raw.trim() else ""
        } catch (_: Exception) {
            ""
        }

    private val fiveSimApiKey: String
        get() {
            val userKey = userFiveSimKeyFlow.value.trim()
            return if (userKey.isNotBlank()) userKey else buildConfigFiveSimKey
        }

    val hasFiveSimApiKey: Boolean
        get() = fiveSimApiKey.isNotEmpty()

    val hasFiveSimKeyState: StateFlow<Boolean> = combine(
        userFiveSimKeyFlow,
        flowOf(buildConfigFiveSimKey)
    ) { userKey, buildKey ->
        userKey.isNotBlank() || buildKey.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), hasFiveSimApiKey)

    // Baseline snapshot of pre-existing public gateway message IDs per number
    // so strangers' old/existing OTPs on shared public gateways never show up as the user's OTP
    private val baselinePublicMessageIds = mutableMapOf<String, MutableSet<String>>()

    // Settings
    val autoRefreshEnabled = preferencesManager.autoRefreshEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoRefreshInterval = preferencesManager.autoRefreshInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)
    val pushNotificationsEnabled = preferencesManager.pushNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoAiSummarize = preferencesManager.autoAiSummarize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val themeMode = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    // Emails for Active Mailbox with search filter
    val activeEmails: StateFlow<List<EmailEntity>> = combine(
        activeMailbox,
        _searchQuery
    ) { mailbox, query ->
        mailbox to query
    }.flatMapLatest { (mailbox, query) ->
        if (mailbox == null) {
            flowOf(emptyList())
        } else {
            repository.getEmailsForMailbox(mailbox.address).combine(flowOf(query)) { list, q ->
                if (q.isBlank()) {
                    list
                } else {
                    list.filter { email ->
                        email.subject.contains(q, ignoreCase = true) ||
                                email.from.contains(q, ignoreCase = true) ||
                                email.bodyText.contains(q, ignoreCase = true) ||
                                (email.aiSummary?.contains(q, ignoreCase = true) == true)
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.purgeFakeAndWelcomeEmails()

            val domains = repository.getAvailableDomains()
            availableDomains.value = domains

            // Create initial mailbox if none exists or if previous domain was retired
            var currentActive = repository.activeMailbox.first()
            if (currentActive == null || (currentActive.domain == "web-library.net" && currentActive.sidToken.isNullOrBlank())) {
                currentActive = repository.createRandomMailbox()
            }

            // Immediate initial inbox sync
            _isRefreshing.value = true
            repository.refreshInbox(
                mailbox = currentActive,
                autoAiSummarize = autoAiSummarize.value,
                showNotifications = pushNotificationsEnabled.value
            )
            _connectionStatus.value = "SMTP LIVE • @${currentActive.domain}"
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            _lastSyncedTime.value = timeStr
            _isRefreshing.value = false
        }

        // Initialize Temp Number (+91 India & Live Public Gateways + 5SIM Stock)
        initializeTempNumbers()

        startAutoRefreshLoop()
    }

    private fun initializeTempNumbers() {
        viewModelScope.launch {
            _isLoadingPhoneNumbers.value = true
            _smsGatewayStatus.value = "SCANNING +91 & LIVE NODES..."

            val storedUserKey = preferencesManager.userFiveSimApiKey.first().trim()
            val effectiveKey = if (storedUserKey.isNotBlank()) storedUserKey else buildConfigFiveSimKey

            // Load live 5SIM India SIM stock and profile in parallel
            launch {
                val stock = tempNumberService.fetchLiveIndiaSimStock()
                _indiaSimStock.value = stock
                if (effectiveKey.isNotBlank()) {
                    tempNumberService.fetchFiveSimProfile(effectiveKey).onSuccess { prof ->
                        _fiveSimProfileInfo.value = "5SIM ACTIVE • BAL: ${prof.balanceRub} RUB (${prof.email})"
                    }
                }
            }

            // Restore any active 5SIM private +91 orders first if API key is configured
            val privateOrders = if (effectiveKey.isNotBlank()) {
                tempNumberService.fetchActivePrivateOrders(effectiveKey).getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val publicNumbers = tempNumberService.fetchPublicNumbers()
            val combined = (privateOrders + publicNumbers).distinctBy { it.fullNumber }
            _availablePhoneNumbers.value = combined

            val preferredNum = privateOrders.firstOrNull()
                ?: combined.firstOrNull { it.isConfirmedOnline && it.countryCode == "+91" }
                ?: combined.firstOrNull { it.isConfirmedOnline }
                ?: combined.firstOrNull()

            if (preferredNum != null) {
                _activePhoneNumber.value = preferredNum
                _smsGatewayStatus.value = when {
                    preferredNum.isPrivate -> "PRIVATE +91 LIVE • ${preferredNum.serviceName ?: "5SIM"}"
                    preferredNum.isConfirmedOnline -> "SMS LIVE • ${preferredNum.countryCode} (${preferredNum.sourceGateway})"
                    else -> "PUBLIC +91 OFFLINE • USE +91 VIP SIM"
                }
                syncActivePhoneSms(showToast = false)
            } else {
                _smsGatewayStatus.value = "PUBLIC NODES BUSY • USE +91 VIP SIM"
            }
            _isLoadingPhoneNumbers.value = false
        }
    }

    fun saveFiveSimApiKey(rawKey: String) {
        val cleanKey = rawKey.trim().removePrefix("Bearer ").trim()
        if (cleanKey.isBlank()) {
            viewModelScope.launch {
                preferencesManager.setUserFiveSimApiKey("")
                _fiveSimProfileInfo.value = null
                Toast.makeText(getApplication(), "5SIM API Key cleared", Toast.LENGTH_SHORT).show()
            }
            return
        }
        viewModelScope.launch {
            preferencesManager.setUserFiveSimApiKey(cleanKey)
            _smsGatewayStatus.value = "VERIFYING 5SIM API KEY..."
            val profileRes = tempNumberService.fetchFiveSimProfile(cleanKey)
            profileRes.onSuccess { prof ->
                _fiveSimProfileInfo.value = "5SIM ACTIVE • BAL: ${prof.balanceRub} RUB (${prof.email})"
                Toast.makeText(
                    getApplication(),
                    "5SIM Connected! Balance: ${prof.balanceRub} RUB",
                    Toast.LENGTH_LONG
                ).show()
                initializeTempNumbers()
            }.onFailure {
                _fiveSimProfileInfo.value = "5SIM KEY SAVED (CHECK TOKEN / BALANCE)"
                Toast.makeText(
                    getApplication(),
                    "5SIM Key saved! Tap a service in +91 VIP SIM to allocate a number.",
                    Toast.LENGTH_LONG
                ).show()
                initializeTempNumbers()
            }
        }
    }

    fun setConsoleMode(mode: String) {
        _consoleMode.value = mode
        if (mode == "SMS") {
            if (_activePhoneNumber.value == null && !_isLoadingPhoneNumbers.value) {
                initializeTempNumbers()
            } else {
                viewModelScope.launch {
                    val stock = tempNumberService.fetchLiveIndiaSimStock()
                    _indiaSimStock.value = stock
                    if (hasFiveSimApiKey) {
                        tempNumberService.fetchFiveSimProfile(fiveSimApiKey).onSuccess { prof ->
                            _fiveSimProfileInfo.value = "5SIM ACTIVE • BAL: ${prof.balanceRub} RUB (${prof.email})"
                        }
                    }
                }
            }
        }
    }

    fun selectPhoneNumber(phone: TempPhoneNumber) {
        _activePhoneNumber.value = phone
        _rawSmsMessages.value = emptyList()
        _smsGatewayStatus.value = when {
            phone.isPrivate -> "PRIVATE +91 LIVE • ${phone.serviceName ?: "5SIM"}"
            phone.isConfirmedOnline -> "SMS LIVE • ${phone.countryCode} (${phone.sourceGateway})"
            else -> "PUBLIC +91 OFFLINE • USE +91 VIP SIM"
        }
        syncActivePhoneSms(showToast = true)
    }

    fun rotateNextPhoneNumber() {
        val list = _availablePhoneNumbers.value
        if (list.isEmpty()) {
            initializeTempNumbers()
            return
        }
        val current = _activePhoneNumber.value
        val currentIndex = list.indexOfFirst { it.fullNumber == current?.fullNumber }
        val nextIndex = if (currentIndex in 0 until list.lastIndex) currentIndex + 1 else 0
        val nextPhone = list[nextIndex]
        selectPhoneNumber(nextPhone)
    }

    fun clearSmsAndWaitForNewOtp() {
        val phone = _activePhoneNumber.value ?: return
        val existingIds = _rawSmsMessages.value.map { it.id }
        val set = baselinePublicMessageIds.getOrPut(phone.fullNumber) { mutableSetOf() }
        set.addAll(existingIds)
        _rawSmsMessages.value = emptyList()
        Toast.makeText(
            getApplication(),
            "Cleared old SMS! Now listening ONLY for brand-new OTP on ${phone.fullNumber}",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun restoreClearedSms() {
        val phone = _activePhoneNumber.value ?: return
        baselinePublicMessageIds.remove(phone.fullNumber)
        syncActivePhoneSms(showToast = true)
    }

    fun syncActivePhoneSms(showToast: Boolean = true) {
        val phone = _activePhoneNumber.value ?: run {
            initializeTempNumbers()
            return
        }
        viewModelScope.launch {
            _isSyncingSms.value = true
            val result = if (phone.isPrivate && hasFiveSimApiKey) {
                tempNumberService.checkPrivateOrderMessages(fiveSimApiKey, phone)
            } else {
                tempNumberService.fetchPublicMessages(phone)
            }
            _isSyncingSms.value = false

            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            _lastSmsSyncTime.value = timeStr

            result.onSuccess { fetchedMessages ->
                val filteredMessages = if (phone.isPrivate) {
                    fetchedMessages
                } else {
                    val clearedSet = baselinePublicMessageIds[phone.fullNumber]
                    if (clearedSet.isNullOrEmpty()) {
                        fetchedMessages
                    } else {
                        fetchedMessages.filter { it.id !in clearedSet }
                    }
                }

                val previousIds = _rawSmsMessages.value.map { it.id }.toSet()
                val newOtpMessages = filteredMessages.filter { it.id !in previousIds && it.extractedOtp != null }
                if (newOtpMessages.isNotEmpty() && pushNotificationsEnabled.value) {
                    val topOtp = newOtpMessages.first()
                    notificationHelper.sendNewEmailNotification(
                        title = "OTP: ${topOtp.extractedOtp} • ${topOtp.sender}",
                        message = topOtp.body,
                        emailId = topOtp.id.hashCode()
                    )
                }
                _rawSmsMessages.value = filteredMessages
                _smsGatewayStatus.value = when {
                    phone.isPrivate -> "PRIVATE +91 LIVE • ${phone.serviceName ?: "5SIM"}"
                    phone.isConfirmedOnline -> "SMS LIVE • ${phone.countryCode} (${phone.sourceGateway})"
                    else -> "PUBLIC +91 OFFLINE • USE +91 VIP SIM"
                }
                if (showToast) {
                    val msgText = when {
                        !phone.isPrivate && !phone.isConfirmedOnline ->
                            "Free Public +91 SIM is Offline. Tap '+91 VIP SIM' to get a Private +91 number!"
                        filteredMessages.isEmpty() ->
                            "Listening for OTP on ${phone.fullNumber} ($timeStr)"
                        else ->
                            "Loaded ${filteredMessages.size} live SMS message(s) ($timeStr)"
                    }
                    Toast.makeText(
                        getApplication(),
                        msgText,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure {
                _smsGatewayStatus.value = "GATEWAY RETRYING..."
                if (showToast) {
                    Toast.makeText(
                        getApplication(),
                        "Gateway busy. Try switching SIM number.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun rentPrivateIndiaSim(serviceId: String) {
        if (!hasFiveSimApiKey) {
            Toast.makeText(
                getApplication(),
                "Paste your 5SIM API Key in '+91 VIP SIM' or AI Studio Secrets panel first",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        viewModelScope.launch {
            _isSyncingSms.value = true
            _smsGatewayStatus.value = "ALLOCATING +91 PRIVATE SIM..."
            val res = tempNumberService.rentPrivateIndiaNumber(fiveSimApiKey, serviceId)
            _isSyncingSms.value = false

            res.onSuccess { privatePhone ->
                _availablePhoneNumbers.value = listOf(privatePhone) + _availablePhoneNumbers.value
                _activePhoneNumber.value = privatePhone
                _rawSmsMessages.value = emptyList()
                _smsGatewayStatus.value = "PRIVATE +91 LIVE • ${privatePhone.serviceName}"
                Toast.makeText(
                    getApplication(),
                    "Allocated Private +91 SIM: ${privatePhone.fullNumber}",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { err ->
                _smsGatewayStatus.value = "5SIM ERROR"
                Toast.makeText(
                    getApplication(),
                    "Failed to rent +91 SIM: ${err.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun releasePrivateIndiaSim(orderId: Long) {
        if (!hasFiveSimApiKey) return
        viewModelScope.launch {
            tempNumberService.cancelPrivateOrder(fiveSimApiKey, orderId)
            Toast.makeText(getApplication(), "Private +91 Order #$orderId cancelled", Toast.LENGTH_SHORT).show()
            val remaining = _availablePhoneNumbers.value.filterNot { it.privateOrderId == orderId }
            _availablePhoneNumbers.value = remaining
            _activePhoneNumber.value = remaining.firstOrNull()
            _rawSmsMessages.value = emptyList()
        }
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val isEnabled = autoRefreshEnabled.value
                val interval = autoRefreshInterval.value.coerceAtLeast(4)

                if (isEnabled && isOnline.value) {
                    if (_consoleMode.value == "EMAIL") {
                        val mailbox = activeMailbox.value
                        if (mailbox != null) {
                            _isRefreshing.value = true
                            val res = repository.refreshInbox(
                                mailbox = mailbox,
                                autoAiSummarize = autoAiSummarize.value,
                                showNotifications = pushNotificationsEnabled.value
                            )
                            _isRefreshing.value = false
                            res.onSuccess {
                                _connectionStatus.value = "SMTP LIVE • @${mailbox.domain}"
                                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                _lastSyncedTime.value = timeStr
                            }.onFailure {
                                _connectionStatus.value = "RECONNECTING SMTP..."
                            }
                        }
                    } else {
                        // Auto-poll active SMS gateway when in SMS mode
                        syncActivePhoneSms(showToast = false)
                    }
                }

                delay(interval * 1000L)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun manualRefreshInbox() {
        viewModelScope.launch {
            val mailbox = activeMailbox.value ?: return@launch
            _isRefreshing.value = true
            val result = repository.refreshInbox(
                mailbox = mailbox,
                autoAiSummarize = autoAiSummarize.value,
                showNotifications = pushNotificationsEnabled.value
            )
            _isRefreshing.value = false

            result.onSuccess { newCount ->
                _connectionStatus.value = "SMTP LIVE • @${mailbox.domain}"
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                _lastSyncedTime.value = timeStr
                val msg = if (newCount > 0) "Received $newCount new message(s)!" else "Inbox synced ($timeStr) — No new messages yet."
                Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
            }.onFailure {
                _connectionStatus.value = "NETWORK ERROR"
                Toast.makeText(getApplication(), "Failed to sync inbox. Check connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun generateRandomMailbox() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val mailbox = repository.createRandomMailbox()
            repository.refreshInbox(mailbox, autoAiSummarize.value, pushNotificationsEnabled.value)
            _connectionStatus.value = "SMTP LIVE • @${mailbox.domain}"
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            _lastSyncedTime.value = timeStr
            _isRefreshing.value = false
            Toast.makeText(getApplication(), "Active: ${mailbox.address}", Toast.LENGTH_SHORT).show()
        }
    }

    fun createCustomMailbox(login: String, domain: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val mailbox = repository.createCustomMailbox(login, domain)
            repository.refreshInbox(mailbox, autoAiSummarize.value, pushNotificationsEnabled.value)
            _connectionStatus.value = "SMTP LIVE • @${mailbox.domain}"
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            _lastSyncedTime.value = timeStr
            _isRefreshing.value = false
            Toast.makeText(getApplication(), "Active: ${mailbox.address}", Toast.LENGTH_SHORT).show()
        }
    }

    fun switchMailbox(address: String) {
        viewModelScope.launch {
            repository.switchActiveMailbox(address)
            val mailbox = repository.activeMailbox.first()
            if (mailbox != null) {
                _isRefreshing.value = true
                repository.refreshInbox(mailbox, autoAiSummarize.value, pushNotificationsEnabled.value)
                _connectionStatus.value = "SMTP LIVE • @${mailbox.domain}"
                _isRefreshing.value = false
            }
        }
    }

    fun deleteMailbox(address: String) {
        viewModelScope.launch {
            repository.deleteMailbox(address)
            val remaining = repository.allMailboxes.first()
            if (remaining.isNotEmpty()) {
                switchMailbox(remaining.first().address)
            } else {
                generateRandomMailbox()
            }
        }
    }

    fun markEmailAsRead(id: Int, address: String) {
        viewModelScope.launch {
            repository.markEmailAsRead(id, address)
        }
    }

    fun deleteEmail(id: Int, address: String) {
        viewModelScope.launch {
            repository.deleteEmail(id, address)
            Toast.makeText(getApplication(), "Message deleted", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearActiveInbox() {
        viewModelScope.launch {
            val address = activeMailbox.value?.address ?: return@launch
            repository.clearMailboxEmails(address)
            Toast.makeText(getApplication(), "Inbox cleared", Toast.LENGTH_SHORT).show()
        }
    }

    fun reanalyzeEmailWithAi(email: EmailEntity) {
        viewModelScope.launch {
            _isAiAnalyzing.value = true
            repository.reanalyzeWithAi(email)
            _isAiAnalyzing.value = false
            Toast.makeText(getApplication(), "Security inspection updated", Toast.LENGTH_SHORT).show()
        }
    }

    // Preference Setters
    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoRefreshEnabled(enabled) }
    }

    fun setAutoRefreshInterval(seconds: Int) {
        viewModelScope.launch { preferencesManager.setAutoRefreshInterval(seconds) }
    }

    fun setPushNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setPushNotificationsEnabled(enabled) }
    }

    fun setAutoAiSummarize(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoAiSummarize(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            generateRandomMailbox()
            Toast.makeText(getApplication(), "Local cache & mailboxes reset", Toast.LENGTH_SHORT).show()
        }
    }
}
