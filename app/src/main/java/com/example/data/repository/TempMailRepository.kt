package com.example.data.repository

import com.example.data.local.EmailEntity
import com.example.data.local.MailboxEntity
import com.example.data.local.TempMailDao
import com.example.data.remote.GeminiApiService
import com.example.data.remote.GuerrillaMailApiService
import com.example.data.remote.MailTmAccountRequest
import com.example.data.remote.MailTmApiService
import com.example.data.remote.TempMailApiService
import com.example.util.NotificationHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Collections

class TempMailRepository(
    private val dao: TempMailDao,
    private val apiService: TempMailApiService = TempMailApiService.create(),
    private val guerrillaApiService: GuerrillaMailApiService = GuerrillaMailApiService.create(),
    private val mailTmApiService: MailTmApiService = MailTmApiService.create(MailTmApiService.MAIL_TM_BASE_URL),
    private val mailGwApiService: MailTmApiService = MailTmApiService.create(MailTmApiService.MAIL_GW_BASE_URL),
    private val geminiService: GeminiApiService,
    private val notificationHelper: NotificationHelper
) {

    private val moshi = Moshi.Builder().build()
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    private val mailTmDomainsCache = Collections.synchronizedSet(mutableSetOf<String>())
    private val mailGwDomainsCache = Collections.synchronizedSet(mutableSetOf<String>())

    private val guerrillaDomains = listOf(
        "sharklasers.com",
        "guerrillamailblock.com",
        "grr.la",
        "pokemail.net",
        "spam4.me",
        "guerrillamail.com",
        "guerrillamail.net",
        "guerrillamail.org"
    )

    val allMailboxes: Flow<List<MailboxEntity>> = dao.getAllMailboxes()
    val activeMailbox: Flow<MailboxEntity?> = dao.getActiveMailbox()

    fun getEmailsForMailbox(address: String): Flow<List<EmailEntity>> {
        return dao.getEmailsForMailbox(address)
    }

    fun getEmailById(id: Int, address: String): Flow<EmailEntity?> {
        return dao.getEmailById(id, address)
    }

    suspend fun purgeFakeAndWelcomeEmails() = withContext(Dispatchers.IO) {
        try {
            dao.purgeSystemWelcomeAndFakeEmails()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getAvailableDomains(): List<String> = withContext(Dispatchers.IO) {
        val liveDomains = mutableListOf<String>()

        // 1. Fetch live OTP-ready domains from Mail.tm
        try {
            val tmList = mailTmApiService.getDomains().member
                ?.filter { it.isActive != false && !it.domain.isNullOrBlank() }
                ?.mapNotNull { it.domain?.lowercase()?.trim() }
                ?: emptyList()
            mailTmDomainsCache.addAll(tmList)
            tmList.forEach { if (!liveDomains.contains(it)) liveDomains.add(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fetch live OTP-ready domains from Mail.gw
        try {
            val gwList = mailGwApiService.getDomains().member
                ?.filter { it.isActive != false && !it.domain.isNullOrBlank() }
                ?.mapNotNull { it.domain?.lowercase()?.trim() }
                ?: emptyList()
            mailGwDomainsCache.addAll(gwList)
            gwList.forEach { if (!liveDomains.contains(it)) liveDomains.add(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Append Guerrilla Mail domains
        guerrillaDomains.forEach { d ->
            if (!liveDomains.contains(d)) liveDomains.add(d)
        }

        liveDomains
    }

    private fun isGuerrillaDomain(domain: String): Boolean {
        val clean = domain.lowercase().trim()
        return guerrillaDomains.any { it.equals(clean, ignoreCase = true) }
    }

    private fun getSiteParam(domain: String): String? {
        return when (domain.lowercase().trim()) {
            "sharklasers.com" -> "sharklasers.com"
            "guerrillamailblock.com" -> "guerrillamailblock.com"
            "grr.la" -> "grr.la"
            "pokemail.net" -> "pokemail.net"
            "spam4.me" -> "spam4.me"
            "guerrillamail.net" -> "guerrillamail.net"
            "guerrillamail.org" -> "guerrillamail.org"
            else -> "guerrillamail.com"
        }
    }

    private fun isGuerrillaSystemWelcomeEmail(
        rawId: String,
        from: String,
        subject: String,
        body: String
    ): Boolean {
        if (rawId == "1") return true
        val lowerFrom = from.lowercase()
        val lowerSub = subject.lowercase()
        val lowerBody = body.lowercase()
        if (lowerFrom.contains("guerrillamail.com") && lowerSub.contains("welcome")) return true
        if (lowerFrom.contains("no-reply@guerrillamail")) return true
        if (lowerSub.contains("welcome to guerrilla mail")) return true
        if (lowerBody.contains("thank you for using guerrilla mail")) return true
        return false
    }

    suspend fun createRandomMailbox(): MailboxEntity = withContext(Dispatchers.IO) {
        dao.purgeSystemWelcomeAndFakeEmails()

        val prefixes = listOf("amit", "dev", "verify", "auth", "user", "inbox", "core", "mail")
        val randomLogin = "${prefixes.random()}${(10000..99999).random()}"
        val generatedPass = "Pwd_${(100000..999999).random()}!"

        // Ensure domain caches are populated
        if (mailTmDomainsCache.isEmpty() && mailGwDomainsCache.isEmpty()) {
            getAvailableDomains()
        }

        // 1. Try Mail.tm live domain first (best for real OTP verification codes)
        val tmDomain = mailTmDomainsCache.firstOrNull()
        if (!tmDomain.isNullOrBlank()) {
            val fullAddress = "$randomLogin@$tmDomain"
            try {
                mailTmApiService.createAccount(MailTmAccountRequest(fullAddress, generatedPass))
                val tokenResp = mailTmApiService.getToken(MailTmAccountRequest(fullAddress, generatedPass))
                val jwt = tokenResp.token
                if (!jwt.isNullOrBlank()) {
                    val mailbox = MailboxEntity(
                        address = fullAddress,
                        login = randomLogin,
                        domain = tmDomain,
                        sidToken = jwt,
                        password = "tm:$generatedPass",
                        createdAt = System.currentTimeMillis(),
                        isActive = true
                    )
                    dao.setActiveMailbox(mailbox.address)
                    dao.insertMailbox(mailbox)
                    return@withContext mailbox
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Try Mail.gw live domain second
        val gwDomain = mailGwDomainsCache.firstOrNull()
        if (!gwDomain.isNullOrBlank()) {
            val fullAddress = "$randomLogin@$gwDomain"
            try {
                mailGwApiService.createAccount(MailTmAccountRequest(fullAddress, generatedPass))
                val tokenResp = mailGwApiService.getToken(MailTmAccountRequest(fullAddress, generatedPass))
                val jwt = tokenResp.token
                if (!jwt.isNullOrBlank()) {
                    val mailbox = MailboxEntity(
                        address = fullAddress,
                        login = randomLogin,
                        domain = gwDomain,
                        sidToken = jwt,
                        password = "gw:$generatedPass",
                        createdAt = System.currentTimeMillis(),
                        isActive = true
                    )
                    dao.setActiveMailbox(mailbox.address)
                    dao.insertMailbox(mailbox)
                    return@withContext mailbox
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to Guerrilla Mail (with welcome message filtering)
        val gDomain = "sharklasers.com"
        val gAddr = "$randomLogin@$gDomain"
        var sidToken: String? = null
        try {
            val response = guerrillaApiService.setEmailUser(randomLogin, site = getSiteParam(gDomain))
            sidToken = response.sidToken
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        MailboxEntity(
            address = gAddr,
            login = randomLogin,
            domain = gDomain,
            sidToken = sidToken,
            password = null,
            createdAt = System.currentTimeMillis(),
            isActive = true
        ).also {
            dao.setActiveMailbox(it.address)
            dao.insertMailbox(it)
        }
    }

    suspend fun createCustomMailbox(login: String, domain: String): MailboxEntity = withContext(Dispatchers.IO) {
        dao.purgeSystemWelcomeAndFakeEmails()

        val cleanDomain = domain.lowercase().trim().removePrefix("@")
        var cleanLogin = login.lowercase().trim()
            .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
            .ifBlank { "user${(1000..9999).random()}" }

        if (mailTmDomainsCache.isEmpty() && mailGwDomainsCache.isEmpty()) {
            getAvailableDomains()
        }

        var sidToken: String? = null
        var passwordStr: String? = null

        if (!isGuerrillaDomain(cleanDomain)) {
            val generatedPass = "Pwd_${(100000..999999).random()}!"
            val useGw = mailGwDomainsCache.contains(cleanDomain) && !mailTmDomainsCache.contains(cleanDomain)
            val targetService = if (useGw) mailGwApiService else mailTmApiService
            val prefix = if (useGw) "gw:" else "tm:"

            var fullAddr = "$cleanLogin@$cleanDomain"
            try {
                targetService.createAccount(MailTmAccountRequest(fullAddr, generatedPass))
                val tokenResp = targetService.getToken(MailTmAccountRequest(fullAddr, generatedPass))
                sidToken = tokenResp.token
                passwordStr = "$prefix$generatedPass"
            } catch (e: Exception) {
                // If username is already taken on public domain, append short numeric tag so it works reliably
                try {
                    cleanLogin = "${cleanLogin}${(10..99).random()}"
                    fullAddr = "$cleanLogin@$cleanDomain"
                    targetService.createAccount(MailTmAccountRequest(fullAddr, generatedPass))
                    val tokenResp = targetService.getToken(MailTmAccountRequest(fullAddr, generatedPass))
                    sidToken = tokenResp.token
                    passwordStr = "$prefix$generatedPass"
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } else {
            try {
                val response = guerrillaApiService.setEmailUser(
                    emailUser = cleanLogin,
                    site = getSiteParam(cleanDomain)
                )
                sidToken = response.sidToken
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val finalAddress = "$cleanLogin@$cleanDomain"
        val mailbox = MailboxEntity(
            address = finalAddress,
            login = cleanLogin,
            domain = cleanDomain,
            sidToken = sidToken,
            password = passwordStr,
            createdAt = System.currentTimeMillis(),
            isActive = true
        )

        dao.setActiveMailbox(mailbox.address)
        dao.insertMailbox(mailbox)
        mailbox
    }

    suspend fun switchActiveMailbox(address: String) = withContext(Dispatchers.IO) {
        dao.setActiveMailbox(address)
    }

    suspend fun deleteMailbox(address: String) = withContext(Dispatchers.IO) {
        dao.deleteMailbox(address)
        dao.clearMailboxEmails(address)
    }

    suspend fun refreshInbox(
        mailbox: MailboxEntity,
        autoAiSummarize: Boolean = true,
        showNotifications: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            dao.purgeSystemWelcomeAndFakeEmails()

            val existingEmails = dao.getEmailsForMailboxSync(mailbox.address)
            val existingIds = existingEmails.map { it.id }.toSet()
            var newCount = 0

            // 1. Mail.tm / Mail.gw Live SMTP Engine
            if (!isGuerrillaDomain(mailbox.domain) || !mailbox.password.isNullOrBlank()) {
                val rawPass = mailbox.password?.removePrefix("tm:")?.removePrefix("gw:")
                val isGw = mailbox.password?.startsWith("gw:") == true || mailGwDomainsCache.contains(mailbox.domain)
                val primaryService = if (isGw) mailGwApiService else mailTmApiService

                var jwtToken = mailbox.sidToken
                if (jwtToken.isNullOrBlank() && !rawPass.isNullOrBlank()) {
                    try {
                        val tokenResp = primaryService.getToken(
                            MailTmAccountRequest(mailbox.address, rawPass)
                        )
                        jwtToken = tokenResp.token
                        if (!jwtToken.isNullOrBlank()) {
                            dao.insertMailbox(mailbox.copy(sidToken = jwtToken))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // If mailbox had no token (e.g. old domain), try registering it now
                if (jwtToken.isNullOrBlank()) {
                    val newPass = rawPass ?: "Pwd_${(100000..999999).random()}!"
                    try {
                        primaryService.createAccount(MailTmAccountRequest(mailbox.address, newPass))
                    } catch (_: Exception) {}
                    try {
                        val tokenResp = primaryService.getToken(MailTmAccountRequest(mailbox.address, newPass))
                        jwtToken = tokenResp.token
                        if (!jwtToken.isNullOrBlank()) {
                            val prefix = if (isGw) "gw:" else "tm:"
                            dao.insertMailbox(mailbox.copy(sidToken = jwtToken, password = "$prefix$newPass"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (!jwtToken.isNullOrBlank()) {
                    val messages = primaryService.getMessages("Bearer $jwtToken").member ?: emptyList()
                    for (msg in messages) {
                        val rawId = msg.id ?: continue
                        val intId = rawId.hashCode()

                        if (!existingIds.contains(intId)) {
                            val detail = try {
                                primaryService.getMessageDetail(rawId, "Bearer $jwtToken")
                            } catch (e: Exception) {
                                null
                            }

                            val safeFrom = (detail?.from?.address ?: msg.from?.address)?.ifBlank { "Unknown Sender" } ?: "Unknown Sender"
                            val safeSubject = (detail?.subject ?: msg.subject)?.ifBlank { "(No Subject)" } ?: "(No Subject)"
                            val safeDate = detail?.createdAt ?: msg.createdAt ?: "Just Now"

                            val bodyText = detail?.text ?: detail?.intro ?: msg.intro ?: ""
                            val bodyHtml = detail?.html?.firstOrNull() ?: ""

                            newCount++

                            val aiResult = if (autoAiSummarize) {
                                try {
                                    geminiService.analyzeEmail(safeSubject, safeFrom, bodyText.ifBlank { bodyHtml })
                                } catch (e: Exception) {
                                    null
                                }
                            } else null

                            val emailEntity = EmailEntity(
                                id = intId,
                                mailboxAddress = mailbox.address,
                                from = safeFrom,
                                subject = safeSubject,
                                date = safeDate,
                                timestamp = System.currentTimeMillis(),
                                bodyHtml = bodyHtml,
                                bodyText = bodyText,
                                isRead = false,
                                aiSummary = aiResult?.summary,
                                aiSpamScore = aiResult?.spamScore,
                                aiSpamReason = aiResult?.spamReason,
                                aiSmartRepliesJson = aiResult?.smartReplies?.let { stringListAdapter.toJson(it) },
                                attachmentsJson = null
                            )

                            dao.insertEmail(emailEntity)

                            if (showNotifications) {
                                notificationHelper.sendNewEmailNotification(
                                    title = "New Email • $safeFrom",
                                    message = safeSubject,
                                    emailId = intId
                                )
                            }
                        }
                    }
                    return@withContext Result.success(newCount)
                }
            }

            // 2. Guerrilla Mail Engine (Strictly filtering out Guerrilla's fake welcome email)
            var currentSidToken = mailbox.sidToken
            try {
                val setUserResp = guerrillaApiService.setEmailUser(
                    emailUser = mailbox.login,
                    site = getSiteParam(mailbox.domain),
                    sidToken = currentSidToken
                )
                if (!setUserResp.sidToken.isNullOrBlank()) {
                    currentSidToken = setUserResp.sidToken
                    dao.insertMailbox(mailbox.copy(sidToken = currentSidToken))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!currentSidToken.isNullOrBlank()) {
                try {
                    val fetchedItems = mutableListOf<com.example.data.remote.GmMailSummaryDto>()
                    try {
                        val listResp = guerrillaApiService.getEmailList(0, currentSidToken)
                        listResp.list?.let { fetchedItems.addAll(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        val checkResp = guerrillaApiService.checkEmail(0, currentSidToken)
                        checkResp.list?.let { fetchedItems.addAll(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val distinctItems = fetchedItems.distinctBy { it.mailId }

                    for (item in distinctItems) {
                        val rawId = item.mailId?.toString() ?: continue
                        val previewFrom = item.mailFrom ?: ""
                        val previewSubject = item.mailSubject ?: ""
                        val previewExcerpt = item.mailExcerpt ?: item.mailBody ?: ""

                        // Skip & delete Guerrilla Mail's built-in fake welcome message
                        if (isGuerrillaSystemWelcomeEmail(rawId, previewFrom, previewSubject, previewExcerpt)) {
                            try {
                                guerrillaApiService.delEmail(rawId, currentSidToken)
                            } catch (_: Exception) {}
                            continue
                        }

                        val intId = rawId.toIntOrNull() ?: rawId.hashCode()

                        if (!existingIds.contains(intId)) {
                            val detail = try {
                                guerrillaApiService.fetchEmail(rawId, currentSidToken)
                            } catch (e: Exception) {
                                null
                            }

                            val safeFrom = (detail?.mailFrom ?: item.mailFrom)?.ifBlank { "Unknown Sender" } ?: "Unknown Sender"
                            val safeSubject = (detail?.mailSubject ?: item.mailSubject)?.ifBlank { "(No Subject)" } ?: "(No Subject)"
                            val safeDate = detail?.mailDate ?: item.mailDate ?: "Just Now"

                            val rawBody = detail?.mailBody ?: item.mailBody ?: item.mailExcerpt ?: ""
                            if (isGuerrillaSystemWelcomeEmail(rawId, safeFrom, safeSubject, rawBody)) {
                                continue
                            }

                            newCount++

                            val bodyHtml = if (rawBody.contains("<") && rawBody.contains(">")) rawBody else ""
                            val bodyText = if (bodyHtml.isNotBlank()) rawBody.replace(Regex("<[^>]*>"), "") else rawBody

                            val aiResult = if (autoAiSummarize) {
                                try {
                                    geminiService.analyzeEmail(safeSubject, safeFrom, bodyText.ifBlank { bodyHtml })
                                } catch (e: Exception) {
                                    null
                                }
                            } else null

                            val emailEntity = EmailEntity(
                                id = intId,
                                mailboxAddress = mailbox.address,
                                from = safeFrom,
                                subject = safeSubject,
                                date = safeDate,
                                timestamp = System.currentTimeMillis(),
                                bodyHtml = bodyHtml,
                                bodyText = bodyText,
                                isRead = false,
                                aiSummary = aiResult?.summary,
                                aiSpamScore = aiResult?.spamScore,
                                aiSpamReason = aiResult?.spamReason,
                                aiSmartRepliesJson = aiResult?.smartReplies?.let { stringListAdapter.toJson(it) },
                                attachmentsJson = null
                            )

                            dao.insertEmail(emailEntity)

                            if (showNotifications) {
                                notificationHelper.sendNewEmailNotification(
                                    title = "New Email • $safeFrom",
                                    message = safeSubject,
                                    emailId = intId
                                )
                            }
                        }
                    }

                    return@withContext Result.success(newCount)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Result.success(newCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchEmailContentIfNeeded(email: EmailEntity): EmailEntity = withContext(Dispatchers.IO) {
        email
    }

    suspend fun markEmailAsRead(id: Int, address: String) = withContext(Dispatchers.IO) {
        dao.updateEmailReadStatus(id, address, true)
    }

    suspend fun deleteEmail(id: Int, address: String) = withContext(Dispatchers.IO) {
        dao.deleteEmail(id, address)
    }

    suspend fun clearMailboxEmails(address: String) = withContext(Dispatchers.IO) {
        dao.clearMailboxEmails(address)
    }

    suspend fun reanalyzeWithAi(email: EmailEntity): EmailEntity = withContext(Dispatchers.IO) {
        val aiResult = geminiService.analyzeEmail(email.subject, email.from, email.bodyText.ifBlank { email.bodyHtml })
        val smartRepliesJson = stringListAdapter.toJson(aiResult.smartReplies)

        dao.updateEmailAiDetails(
            id = email.id,
            mailboxAddress = email.mailboxAddress,
            summary = aiResult.summary,
            spamScore = aiResult.spamScore,
            spamReason = aiResult.spamReason,
            smartRepliesJson = smartRepliesJson
        )

        email.copy(
            aiSummary = aiResult.summary,
            aiSpamScore = aiResult.spamScore,
            aiSpamReason = aiResult.spamReason,
            aiSmartRepliesJson = smartRepliesJson
        )
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        dao.clearAllEmails()
        dao.clearAllMailboxes()
    }
}
