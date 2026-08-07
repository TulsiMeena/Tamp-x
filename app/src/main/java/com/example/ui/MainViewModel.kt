package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.EmailEntity
import com.example.data.local.MailboxEntity
import com.example.data.local.PreferencesManager
import com.example.data.remote.GeminiApiService
import com.example.data.remote.TempMailApiService
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isAiAnalyzing = MutableStateFlow(false)
    val isAiAnalyzing: StateFlow<Boolean> = _isAiAnalyzing.asStateFlow()

    val availableDomains = MutableStateFlow<List<String>>(emptyList())

    // Settings
    val autoRefreshEnabled = preferencesManager.autoRefreshEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoRefreshInterval = preferencesManager.autoRefreshInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
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
            val domains = repository.getAvailableDomains()
            availableDomains.value = domains

            // Create initial mailbox if none exists
            var currentActive = repository.activeMailbox.first()
            if (currentActive == null) {
                currentActive = repository.createRandomMailbox()
            }
            
            // Immediate initial inbox sync
            _isRefreshing.value = true
            repository.refreshInbox(
                mailbox = currentActive,
                autoAiSummarize = autoAiSummarize.value,
                showNotifications = pushNotificationsEnabled.value
            )
            _isRefreshing.value = false
        }

        startAutoRefreshLoop()
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val isEnabled = autoRefreshEnabled.value
                val interval = autoRefreshInterval.value.coerceAtLeast(3)

                if (isEnabled && isOnline.value) {
                    val mailbox = activeMailbox.value
                    if (mailbox != null) {
                        _isRefreshing.value = true
                        repository.refreshInbox(
                            mailbox = mailbox,
                            autoAiSummarize = autoAiSummarize.value,
                            showNotifications = pushNotificationsEnabled.value
                        )
                        _isRefreshing.value = false
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
                val msg = if (newCount > 0) "Received $newCount new message(s)!" else "Inbox is up to date."
                Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(getApplication(), "Failed to check inbox. Check network.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun generateRandomMailbox() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val mailbox = repository.createRandomMailbox()
            repository.refreshInbox(mailbox, autoAiSummarize.value, pushNotificationsEnabled.value)
            _isRefreshing.value = false
            Toast.makeText(getApplication(), "Generated: ${mailbox.address}", Toast.LENGTH_SHORT).show()
        }
    }

    fun createCustomMailbox(login: String, domain: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val mailbox = repository.createCustomMailbox(login, domain)
            repository.refreshInbox(mailbox, autoAiSummarize.value, pushNotificationsEnabled.value)
            _isRefreshing.value = false
            Toast.makeText(getApplication(), "Created: ${mailbox.address}", Toast.LENGTH_SHORT).show()
        }
    }

    fun switchMailbox(address: String) {
        viewModelScope.launch {
            repository.switchActiveMailbox(address)
            val mailbox = repository.activeMailbox.first()
            if (mailbox != null) {
                repository.refreshInbox(mailbox, autoAiSummarize.value, pushNotificationsEnabled.value)
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
            Toast.makeText(getApplication(), "Email deleted.", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearActiveInbox() {
        viewModelScope.launch {
            val address = activeMailbox.value?.address ?: return@launch
            repository.clearMailboxEmails(address)
            Toast.makeText(getApplication(), "Inbox cleared.", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendTestVerificationEmail() {
        viewModelScope.launch {
            val address = activeMailbox.value?.address ?: return@launch
            repository.injectTestVerificationEmail(address)
            Toast.makeText(getApplication(), "Test verification email sent to inbox!", Toast.LENGTH_SHORT).show()
        }
    }

    fun reanalyzeEmailWithAi(email: EmailEntity) {
        viewModelScope.launch {
            _isAiAnalyzing.value = true
            repository.reanalyzeWithAi(email)
            _isAiAnalyzing.value = false
            Toast.makeText(getApplication(), "Gemini AI analysis updated!", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(getApplication(), "All data cleared.", Toast.LENGTH_SHORT).show()
        }
    }
}
