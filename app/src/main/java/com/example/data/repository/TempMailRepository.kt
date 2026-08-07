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
import java.util.UUID

class TempMailRepository(
    private val dao: TempMailDao,
    private val apiService: TempMailApiService = TempMailApiService.create(),
    private val guerrillaApiService: GuerrillaMailApiService = GuerrillaMailApiService.create(),
    private val mailTmApiService: MailTmApiService = MailTmApiService.create(),
    private val geminiService: GeminiApiService,
    private val notificationHelper: NotificationHelper
) {

    private val moshi = Moshi.Builder().build()
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    val allMailboxes: Flow<List<MailboxEntity>> = dao.getAllMailboxes()
    val activeMailbox: Flow<MailboxEntity?> = dao.getActiveMailbox()

    fun getEmailsForMailbox(address: String): Flow<List<EmailEntity>> {
        return dao.getEmailsForMailbox(address)
    }

    fun getEmailById(id: Int, address: String): Flow<EmailEntity?> {
        return dao.getEmailById(id, address)
    }

    suspend fun getAvailableDomains(): List<String> = withContext(Dispatchers.IO) {
        val defaultList = mutableListOf(
            "guerrillamailblock.com",
            "guerrillamail.com",
            "sharklasers.com",
            "guerrillamail.net",
            "guerrillamail.org",
            "grr.la",
            "pokemail.net",
            "spam4.me",
            "web-library.net"
        )
        try {
            val mailTmDomains = mailTmApiService.getDomains().member?.mapNotNull { it.domain } ?: emptyList()
            mailTmDomains.forEach { d ->
                if (!defaultList.contains(d)) defaultList.add(d)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        defaultList
    }

    private fun isMailTmDomain(domain: String): Boolean {
        return domain.contains("web-library.net") || domain.contains("mail.tm")
    }

    suspend fun createRandomMailbox(): MailboxEntity = withContext(Dispatchers.IO) {
        val prefixes = listOf("user", "mail", "alex", "john", "box", "inbox", "fast", "temp")
        val randomLogin = prefixes.random() + "_" + (100000..999999).random()
        val domain = "guerrillamailblock.com"

        var sidToken: String? = null
        try {
            val response = guerrillaApiService.setEmailUser(randomLogin, domain)
            sidToken = response.sidToken
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fullAddress = "$randomLogin@$domain"
        val mailbox = MailboxEntity(
            address = fullAddress,
            login = randomLogin,
            domain = domain,
            sidToken = sidToken,
            password = null,
            createdAt = System.currentTimeMillis(),
            isActive = true
        )

        dao.setActiveMailbox(mailbox.address)
        dao.insertMailbox(mailbox)
        mailbox
    }

    suspend fun createCustomMailbox(login: String, domain: String): MailboxEntity = withContext(Dispatchers.IO) {
        val cleanLogin = login.lowercase().trim().filter { it.isLetterOrDigit() || it == '.' || it == '_' }
            .ifBlank { "user_" + (100000..999999).random() }

        var sidToken: String? = null
        var passwordStr: String? = null

        if (isMailTmDomain(domain)) {
            val generatedPass = "pass_" + (100000..999999).random()
            val fullAddr = "$cleanLogin@$domain"
            try {
                mailTmApiService.createAccount(MailTmAccountRequest(fullAddr, generatedPass))
                val tokenResp = mailTmApiService.getToken(MailTmAccountRequest(fullAddr, generatedPass))
                sidToken = tokenResp.token
                passwordStr = generatedPass
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                val response = guerrillaApiService.setEmailUser(cleanLogin, domain)
                sidToken = response.sidToken
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val fullAddress = "$cleanLogin@$domain"
        val mailbox = MailboxEntity(
            address = fullAddress,
            login = cleanLogin,
            domain = domain,
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
            val existingEmails = dao.getEmailsForMailboxSync(mailbox.address)
            val existingIds = existingEmails.map { it.id }.toSet()
            var newCount = 0

            // 1. Mail.tm Engine
            if (isMailTmDomain(mailbox.domain)) {
                var jwtToken = mailbox.sidToken
                if (jwtToken.isNullOrBlank() && !mailbox.password.isNullOrBlank()) {
                    try {
                        val tokenResp = mailTmApiService.getToken(
                            MailTmAccountRequest(mailbox.address, mailbox.password)
                        )
                        jwtToken = tokenResp.token
                        if (!jwtToken.isNullOrBlank()) {
                            dao.insertMailbox(mailbox.copy(sidToken = jwtToken))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (!jwtToken.isNullOrBlank()) {
                    val messages = mailTmApiService.getMessages("Bearer $jwtToken").member ?: emptyList()
                    for (msg in messages) {
                        val rawId = msg.id ?: continue
                        val intId = rawId.hashCode()

                        if (!existingIds.contains(intId)) {
                            newCount++
                            val detail = try {
                                mailTmApiService.getMessageDetail(rawId, "Bearer $jwtToken")
                            } catch (e: Exception) {
                                null
                            }

                            val safeFrom = (detail?.from?.address ?: msg.from?.address)?.ifBlank { "Unknown Sender" } ?: "Unknown Sender"
                            val safeSubject = (detail?.subject ?: msg.subject)?.ifBlank { "(No Subject)" } ?: "(No Subject)"
                            val safeDate = detail?.createdAt ?: msg.createdAt ?: "Just Now"

                            val bodyText = detail?.text ?: detail?.intro ?: msg.intro ?: ""
                            val bodyHtml = detail?.html?.firstOrNull() ?: ""

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
                                    title = "New Email from $safeFrom",
                                    message = safeSubject,
                                    emailId = intId
                                )
                            }
                        }
                    }
                    return@withContext Result.success(newCount)
                }
            }

            // 2. Guerrilla Mail Engine
            var currentSidToken = mailbox.sidToken
            if (currentSidToken.isNullOrBlank()) {
                try {
                    val setUserResp = guerrillaApiService.setEmailUser(mailbox.login, mailbox.domain)
                    currentSidToken = setUserResp.sidToken
                    if (!currentSidToken.isNullOrBlank()) {
                        dao.insertMailbox(mailbox.copy(sidToken = currentSidToken))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!currentSidToken.isNullOrBlank()) {
                try {
                    val gmListResp = guerrillaApiService.checkEmail(0, currentSidToken)
                    val items = gmListResp.list ?: emptyList()

                    for (item in items) {
                        val rawId = item.mailId?.toString() ?: continue
                        val intId = rawId.toIntOrNull() ?: rawId.hashCode()

                        if (!existingIds.contains(intId)) {
                            newCount++
                            val detail = try {
                                guerrillaApiService.fetchEmail(rawId, currentSidToken)
                            } catch (e: Exception) {
                                null
                            }

                            val safeFrom = (detail?.mailFrom ?: item.mailFrom)?.ifBlank { "Unknown Sender" } ?: "Unknown Sender"
                            val safeSubject = (detail?.mailSubject ?: item.mailSubject)?.ifBlank { "(No Subject)" } ?: "(No Subject)"
                            val safeDate = detail?.mailDate ?: item.mailDate ?: "Just Now"

                            val rawBody = detail?.mailBody ?: item.mailBody ?: item.mailExcerpt ?: ""
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
                                    title = "New Email from $safeFrom",
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

    suspend fun injectTestVerificationEmail(address: String): EmailEntity = withContext(Dispatchers.IO) {
        val testOtpCode = (100000..999999).random().toString()
        val generatedId = (System.currentTimeMillis() % 1000000).toInt()

        val email = EmailEntity(
            id = generatedId,
            mailboxAddress = address,
            from = "security-noreply@verification-service.com",
            subject = "Your Verification Code is $testOtpCode",
            date = "Just Now",
            timestamp = System.currentTimeMillis(),
            bodyHtml = """
                <div style="font-family: Arial, sans-serif; padding: 20px;">
                    <h2>Account Security & Email Verification</h2>
                    <p>Thank you for using our service. Your one-time login verification code is:</p>
                    <div style="font-size: 28px; font-weight: bold; color: #2563EB; letter-spacing: 4px; margin: 16px 0;">$testOtpCode</div>
                    <p>Or click the link below to confirm your email instantly:</p>
                    <p><a href="https://example.com/verify?token=${UUID.randomUUID()}&code=$testOtpCode" style="color: #2563EB; font-weight: bold;">Verify Email Address Now</a></p>
                    <p style="color: #64748B; font-size: 12px; margin-top: 24px;">If you did not request this, please ignore this email.</p>
                </div>
            """.trimIndent(),
            bodyText = "Your verification code is $testOtpCode. Click here to verify: https://example.com/verify?code=$testOtpCode",
            isRead = false,
            aiSummary = "Official account verification message containing single-use OTP code $testOtpCode and account confirmation link.",
            aiSpamScore = 5,
            aiSpamReason = "Safe legitimate authentication message from verified security service.",
            aiSmartRepliesJson = stringListAdapter.toJson(listOf("Copied code $testOtpCode", "Verified link received", "Thank you!")),
            attachmentsJson = null
        )

        dao.insertEmail(email)

        notificationHelper.sendNewEmailNotification(
            title = "Test Verification Email Received",
            message = "Code: $testOtpCode",
            emailId = generatedId
        )

        email
    }
}
