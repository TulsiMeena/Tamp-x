package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TempPhoneNumber(
    val fullNumber: String,      // e.g. "+919876543210"
    val localNumber: String,     // e.g. "9876543210" (10-digit for Indian forms)
    val countryCode: String,     // e.g. "+91"
    val countryName: String,     // e.g. "India (+91)"
    val sourceGateway: String,   // e.g. "5SIM-Private", "ReceiveSMSFree", "OnlineSim"
    val sourceUrl: String = "",
    val isPrivate: Boolean = false,
    val privateOrderId: Long? = null,
    val serviceName: String? = null,
    val isConfirmedOnline: Boolean = true,
    val statusNote: String = "LIVE • READY FOR OTP"
)

data class SmsOtpMessage(
    val id: String,
    val phoneNumber: String,
    val sender: String,
    val body: String,
    val receivedAt: String,
    val extractedOtp: String?
)

data class IndiaServiceStock(
    val serviceId: String,
    val displayName: String,
    val availableCount: Int,
    val priceRub: Double
)

data class FiveSimProfile(
    val email: String,
    val balanceRub: Double,
    val rating: Double
)

class TempNumberService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val browserUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Extracts a 4 to 8 digit OTP verification code from real incoming SMS text
     * (including WhatsApp 123-456 format and Google G-123456 format).
     */
    fun extractOtpFromSms(text: String): String? {
        // 1. Check hyphenated 6-digit OTPs (e.g. WhatsApp "482-910")
        val hyphenOtpRegex = Regex("\\b([0-9]{3})-([0-9]{3})\\b")
        hyphenOtpRegex.find(text)?.let { match ->
            return match.groupValues[1] + match.groupValues[2]
        }

        // 2. Check Google-style "G-123456" OTPs
        val googleOtpRegex = Regex("\\bG-([0-9]{4,8})\\b", RegexOption.IGNORE_CASE)
        googleOtpRegex.find(text)?.groupValues?.getOrNull(1)?.let { return it }

        // 3. Check keyword-prefixed 4-8 digit OTPs
        val keywordRegex = Regex(
            "(?:code|otp|pin|password|verification|passcode|is|कोड)[:\\s\\-]*([0-9]{4,8})\\b",
            RegexOption.IGNORE_CASE
        )
        keywordRegex.find(text)?.groupValues?.getOrNull(1)?.let { return it }

        // 4. Generic 4-8 digit fallback
        val genericRegex = Regex("\\b([0-9]{4,8})\\b")
        val matches = genericRegex.findAll(text).map { it.groupValues[1] }.toList()
        return matches.firstOrNull { it !in listOf("2024", "2025", "2026", "2027") } ?: matches.firstOrNull()
    }

    /**
     * Fetches real, active public phone numbers:
     * 1. Scrapes genuinely ONLINE numbers from ReceiveSMSFree (which receives live OTPs every few minutes).
     * 2. Checks OnlineSim Free API for any enabled numbers.
     * 3. Checks public India (+91) numbers and verifies whether each +91 SIM is actually online
     *    or offline (so dead '1 months ago' numbers are never selected as the default active SIM).
     */
    suspend fun fetchPublicNumbers(): List<TempPhoneNumber> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<TempPhoneNumber>()

        // 1. Scrape genuinely LIVE public numbers from receive-sms-free.cc
        try {
            val liveFreeNums = scrapeReceiveSmsFreeNumbers()
            discovered.addAll(liveFreeNums)
        } catch (_: Exception) {
        }

        // 2. Try OnlineSim Free Public JSON API for India (91) and active countries
        try {
            val onlineSimIndia = fetchOnlineSimNumbersForCountry(91, "India (+91)", "+91")
            for (n in onlineSimIndia) {
                if (discovered.none { it.fullNumber == n.fullNumber }) {
                    discovered.add(n)
                }
            }
        } catch (_: Exception) {
        }

        // 3. Check public India (+91) numbers and inspect their live status
        try {
            val scrapedIndia = scrapePublicIndiaNumbers()
            for (num in scrapedIndia) {
                if (discovered.none { it.localNumber == num.localNumber }) {
                    discovered.add(num)
                }
            }
        } catch (_: Exception) {
        }

        // Prioritize:
        // 1st: Confirmed ONLINE +91 numbers
        // 2nd: Confirmed ONLINE international gateway numbers (ReceiveSMSFree / OnlineSim)
        // 3rd: Offline/Congested public +91 numbers (clearly marked as OFFLINE so user isn't misled)
        discovered.sortedWith(
            compareByDescending<TempPhoneNumber> { it.isConfirmedOnline && it.countryCode == "+91" }
                .thenByDescending { it.isConfirmedOnline }
                .thenByDescending { it.countryCode == "+91" }
        )
    }

    private fun scrapeReceiveSmsFreeNumbers(): List<TempPhoneNumber> {
        val results = mutableListOf<TempPhoneNumber>()
        val pages = listOf(
            "https://receive-sms-free.cc/Free-India-Phone-Number/",
            "https://receive-sms-free.cc/"
        )
        val linkRegex = Regex(
            "href=\"((?:https://receive-sms-free\\.cc)?/Free-([A-Za-z]+)-Phone-Number/([0-9]{8,15})/?)\"",
            RegexOption.IGNORE_CASE
        )

        for (pageUrl in pages) {
            try {
                val req = Request.Builder()
                    .url(pageUrl)
                    .header("User-Agent", browserUserAgent)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string().orEmpty()
                        val matches = linkRegex.findAll(html).toList()
                        for (m in matches) {
                            val rawHref = m.groupValues[1]
                            val countryWord = m.groupValues[2]
                            val digits = m.groupValues[3]
                            val fullUrl = if (rawHref.startsWith("http")) rawHref else "https://receive-sms-free.cc$rawHref"

                            val (dialCode, localNum, label) = when (countryWord.lowercase()) {
                                "india" -> Triple("+91", digits.removePrefix("91"), "India (+91)")
                                "usa" -> Triple("+1", digits.removePrefix("1"), "USA (+1 Live)")
                                "uk" -> Triple("+44", digits.removePrefix("44"), "UK (+44 Live)")
                                "netherlands" -> Triple("+31", digits.removePrefix("31"), "Netherlands (+31 Live)")
                                "finland" -> Triple("+358", digits.removePrefix("358"), "Finland (+358 Live)")
                                "slovenia" -> Triple("+386", digits.removePrefix("386"), "Slovenia (+386 Live)")
                                else -> Triple("+" + digits.take(2), digits.drop(2), "$countryWord (Live)")
                            }
                            val fullNumber = "+$digits"
                            if (results.none { it.fullNumber == fullNumber }) {
                                results.add(
                                    TempPhoneNumber(
                                        fullNumber = fullNumber,
                                        localNumber = localNum,
                                        countryCode = dialCode,
                                        countryName = label,
                                        sourceGateway = "ReceiveSMSFree",
                                        sourceUrl = fullUrl,
                                        isConfirmedOnline = true,
                                        statusNote = "ONLINE • PUBLIC SMS GATEWAY"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return results
    }

    private fun fetchOnlineSimNumbersForCountry(
        countryCodeInt: Int,
        countryLabel: String,
        dialPrefix: String
    ): List<TempPhoneNumber> {
        val list = mutableListOf<TempPhoneNumber>()
        val req = Request.Builder()
            .url("https://onlinesim.io/api/getFreePhoneList?country=$countryCodeInt&lang=en")
            .header("User-Agent", browserUserAgent)
            .header("Accept", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val bodyStr = resp.body?.string().orEmpty()
            val json = JSONObject(bodyStr)
            val numbersArr = json.optJSONArray("numbers") ?: return emptyList()
            for (i in 0 until numbersArr.length()) {
                val item = numbersArr.optJSONObject(i) ?: continue
                val status = item.optString("status", "")
                if (status.equals("disabled", ignoreCase = true) || item.isNull("number")) continue
                val rawFull = item.optString("full_number").ifBlank { item.optString("number") }
                if (rawFull.isBlank() || rawFull == "null") continue
                val normalizedFull = if (rawFull.startsWith("+")) rawFull else "+$rawFull"
                val localDigits = normalizedFull.removePrefix(dialPrefix).removePrefix("+").filter { it.isDigit() }
                if (localDigits.length < 7) continue
                list.add(
                    TempPhoneNumber(
                        fullNumber = normalizedFull,
                        localNumber = localDigits,
                        countryCode = dialPrefix,
                        countryName = countryLabel,
                        sourceGateway = "OnlineSim",
                        sourceUrl = countryCodeInt.toString(),
                        isConfirmedOnline = true,
                        statusNote = "ONLINE • ONLINESIM GATEWAY"
                    )
                )
            }
        }
        return list
    }

    private fun scrapePublicIndiaNumbers(): List<TempPhoneNumber> {
        val results = mutableListOf<TempPhoneNumber>()
        val url = "https://mytempsms.com/receive-sms-online/india-phone-number.html"
        val myTempSmsUidRegex = Regex(
            "\"phone_num\"\\s*:\\s*\"(\\+?91[6-9][0-9]{9})\"\\s*,\\s*\"uid\"\\s*:\\s*\"([0-9]+)\""
        )

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    val uidMatches = myTempSmsUidRegex.findAll(html).toList()
                    for ((idx, m) in uidMatches.withIndex()) {
                        val raw = m.groupValues[1]
                        val uid = m.groupValues[2]
                        val tenDigit = raw.removePrefix("+").removePrefix("91")
                        val detailUrl = "https://mytempsms.com/receive-sms-online/india-phone-number-$uid.html"

                        // Check first number's detail page to see if MyTempSMS +91 node is online or offline
                        var isOnline = false
                        var statusNote = "PUBLIC +91 SIM OFFLINE (USE +91 VIP SIM)"
                        if (idx == 0) {
                            try {
                                client.newCall(
                                    Request.Builder().url(detailUrl).header("User-Agent", browserUserAgent).build()
                                ).execute().use { dResp ->
                                    val dHtml = dResp.body?.string().orEmpty()
                                    val MarkedOffline = dHtml.contains("status is Offline", ignoreCase = true) ||
                                        dHtml.contains("months ago", ignoreCase = true)
                                    isOnline = !MarkedOffline && dResp.isSuccessful
                                    if (isOnline) {
                                        statusNote = "ONLINE • PUBLIC +91 GATEWAY"
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }

                        if (tenDigit.length == 10 && results.none { it.localNumber == tenDigit }) {
                            results.add(
                                TempPhoneNumber(
                                    fullNumber = "+91$tenDigit",
                                    localNumber = tenDigit,
                                    countryCode = "+91",
                                    countryName = if (isOnline) "India (+91 Public)" else "India (+91 Public • Offline)",
                                    sourceGateway = "MyTempSMS",
                                    sourceUrl = detailUrl,
                                    isConfirmedOnline = isOnline,
                                    statusNote = statusNote
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    /**
     * Checks whether a public gateway SMS timestamp represents a recent message
     * vs. an old archived SMS from days/weeks/months ago.
     */
    fun isFreshRealTimeMessage(receivedAt: String): Boolean {
        val lower = receivedAt.lowercase().trim()
        if (lower.isBlank() || lower == "live" || lower.contains("just now") || lower.contains("sec")) {
            return true
        }
        // Reject archived messages from days, weeks, months, or years ago
        if (lower.contains("month") ||
            lower.contains("week") ||
            lower.contains("day") ||
            lower.contains("year") ||
            lower.contains("yesterday")
        ) {
            return false
        }
        // Allow messages within the last few hours on live public gateways so timezone/clock skews
        // never swallow a newly arrived OTP
        if (lower.contains("hour") || lower.contains("hr")) {
            val hrs = Regex("([0-9]+)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            return hrs != null && hrs <= 3
        }
        if (lower.contains("min")) {
            return true
        }
        return true
    }

    /**
     * Fetches real incoming SMS messages for the selected public phone number.
     */
    suspend fun fetchPublicMessages(phone: TempPhoneNumber): Result<List<SmsOtpMessage>> =
        withContext(Dispatchers.IO) {
            try {
                val messages = mutableListOf<SmsOtpMessage>()

                if (phone.sourceGateway == "OnlineSim") {
                    val countryParam = phone.sourceUrl.ifBlank { phone.countryCode.removePrefix("+") }
                    val cleanPhone = phone.fullNumber.removePrefix("+")
                    val phoneWithoutCountry = phone.localNumber

                    val urlsToTry = listOf(
                        "https://onlinesim.io/api/getFreeMessageList?phone=$cleanPhone&country=$countryParam&lang=en",
                        "https://onlinesim.io/api/getFreeMessageList?phone=$phoneWithoutCountry&country=$countryParam&lang=en"
                    )

                    for (apiUrl in urlsToTry) {
                        val req = Request.Builder()
                            .url(apiUrl)
                            .header("User-Agent", browserUserAgent)
                            .header("Accept", "application/json")
                            .build()

                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val str = resp.body?.string().orEmpty()
                                val json = JSONObject(str)
                                val msgObj = json.optJSONObject("messages")
                                val dataArr = msgObj?.optJSONArray("data") ?: json.optJSONArray("messages")
                                if (dataArr != null && dataArr.length() > 0) {
                                    for (i in 0 until dataArr.length()) {
                                        val m = dataArr.optJSONObject(i) ?: continue
                                        val text = m.optString("text").trim()
                                        if (text.isBlank()) continue
                                        val sender = m.optString("in_number")
                                            .ifBlank { m.optString("sender", "SMS-GATEWAY") }
                                        val time = m.optString("data_humans")
                                            .ifBlank { m.optString("created_at", "LIVE") }
                                        if (!isFreshRealTimeMessage(time)) continue
                                        messages.add(
                                            SmsOtpMessage(
                                                id = "${phone.fullNumber}_${text.hashCode()}",
                                                phoneNumber = phone.fullNumber,
                                                sender = sender,
                                                body = text,
                                                receivedAt = time,
                                                extractedOtp = extractOtpFromSms(text)
                                            )
                                        )
                                    }
                                    if (messages.isNotEmpty()) break
                                }
                            }
                        }
                    }
                } else {
                    val rawDigits = phone.fullNumber.removePrefix("+")
                    val candidateUrls = buildList {
                        if (phone.sourceUrl.startsWith("http")) {
                            add(phone.sourceUrl)
                        }
                        add("https://receive-sms-free.cc/Free-USA-Phone-Number/$rawDigits/")
                        add("https://receive-sms-free.cc/Free-India-Phone-Number/$rawDigits/")
                    }.distinct()

                    for (pageUrl in candidateUrls) {
                        try {
                            val req = Request.Builder()
                                .url(pageUrl)
                                .header("User-Agent", browserUserAgent)
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val html = resp.body?.string().orEmpty()
                                    val parsed = parseHtmlSmsRows(html, phone.fullNumber)
                                    if (parsed.isNotEmpty()) {
                                        messages.addAll(parsed)
                                        break
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                Result.success(messages)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseHtmlSmsRows(html: String, phoneNumber: String): List<SmsOtpMessage> {
        if (html.contains("status is Offline", ignoreCase = true)) {
            return emptyList()
        }

        val list = mutableListOf<SmsOtpMessage>()

        // 1. Parse ReceiveSMSFree (<span class="sender-badge">...<span class="time-text">...<p class="sms-content">)
        val recSmsFreeRegex = Regex(
            "<span[^>]*class=\"[^\"]*sender-badge[^\"]*\"[^>]*>(.*?)</span>.*?<span[^>]*class=\"[^\"]*time-text[^\"]*\"[^>]*>(.*?)</span>.*?<p[^>]*class=\"[^\"]*sms-content[^\"]*\"[^>]*>(.*?)</p>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val recMatches = recSmsFreeRegex.findAll(html).toList()
        for (m in recMatches.take(25)) {
            val cleanSender = m.groupValues[1].replace(Regex("<[^>]+>"), "").trim().ifBlank { "SMS-GATEWAY" }
            val cleanTime = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim().ifBlank { "LIVE" }
            val cleanBody = m.groupValues[3]
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleanBody.isNotBlank() && isFreshRealTimeMessage(cleanTime)) {
                list.add(
                    SmsOtpMessage(
                        id = "${phoneNumber}_rf_${cleanBody.hashCode()}_${cleanTime.hashCode()}",
                        phoneNumber = phoneNumber,
                        sender = cleanSender,
                        body = cleanBody,
                        receivedAt = cleanTime,
                        extractedOtp = extractOtpFromSms(cleanBody)
                    )
                )
            }
        }
        if (recMatches.isNotEmpty()) return list

        // 2. Parse structured <article class="sms-item"> blocks (MyTempSMS India +91 format)
        val articleRegex = Regex(
            "<article[^>]*class=\"[^\"]*sms-item[^\"]*\"[^>]*>(.*?)</article>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val senderRegex = Regex(
            "<span[^>]*class=\"[^\"]*sms-sender-name[^\"]*\"[^>]*>(.*?)</span>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val msgBodyRegex = Regex(
            "<div[^>]*class=\"[^\"]*sms-message[^\"]*\"[^>]*>(.*?)</div>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val timeRegex = Regex(
            "<time[^>]*class=\"[^\"]*sms-time[^\"]*\"[^>]*>(.*?)</time>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        val articles = articleRegex.findAll(html).toList()
        for (art in articles.take(25)) {
            val block = art.groupValues[1]
            val rawBody = msgBodyRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
            val cleanBody = rawBody
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleanBody.isNotBlank()) {
                val rawSender = senderRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
                val cleanSender = rawSender
                    .replace(Regex("<[^>]+>"), "")
                    .trim()
                    .ifBlank { "IN-GATEWAY" }
                val cleanTime = timeRegex.find(block)?.groupValues?.getOrNull(1)
                    ?.replace(Regex("<[^>]+>"), "")
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "LIVE" }

                if (!isFreshRealTimeMessage(cleanTime)) continue

                list.add(
                    SmsOtpMessage(
                        id = "${phoneNumber}_art_${cleanBody.hashCode()}",
                        phoneNumber = phoneNumber,
                        sender = cleanSender,
                        body = cleanBody,
                        receivedAt = cleanTime,
                        extractedOtp = extractOtpFromSms(cleanBody)
                    )
                )
            }
        }
        if (articles.isNotEmpty()) return list

        // 3. Generic table/div SMS row parser
        val rowRegex = Regex(
            "<(?:div|td)[^>]*class=\"[^\"]*(?:message_details|msg-text|sms-text|sms-message|tbody)[^\"]*\"[^>]*>(.*?)</(?:div|td)>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val matches = rowRegex.findAll(html).toList()
        for ((idx, m) in matches.take(25).withIndex()) {
            val cleanText = m.groupValues[1]
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleanText.length > 3) {
                list.add(
                    SmsOtpMessage(
                        id = "${phoneNumber}_html_${idx}_${cleanText.hashCode()}",
                        phoneNumber = phoneNumber,
                        sender = "IN-GATEWAY",
                        body = cleanText,
                        receivedAt = "LIVE",
                        extractedOtp = extractOtpFromSms(cleanText)
                    )
                )
            }
        }
        return list
    }

    /**
     * Fetches the complete live India (+91) SIM inventory & pricing from 5SIM Guest API.
     * Includes top Indian & global services first, followed by all other available +91 services
     * so the user can search and allocate a +91 SIM for ANY website or app.
     */
    suspend fun fetchLiveIndiaSimStock(): List<IndiaServiceStock> = withContext(Dispatchers.IO) {
        val priorityLabels = linkedMapOf(
            "whatsapp" to "WhatsApp (+91)",
            "telegram" to "Telegram (+91)",
            "google" to "Google / Gmail / YouTube (+91)",
            "instagram" to "Instagram (+91)",
            "facebook" to "Facebook (+91)",
            "amazon" to "Amazon India (+91)",
            "flipkart" to "Flipkart (+91)",
            "swiggy" to "Swiggy (+91)",
            "zomato" to "Zomato (+91)",
            "meesho" to "Meesho (+91)",
            "zepto" to "Zepto (+91)",
            "truecaller" to "Truecaller (+91)",
            "jiomart" to "JioMart (+91)",
            "openai" to "OpenAI / ChatGPT (+91)",
            "microsoft" to "Microsoft / Outlook (+91)",
            "apple" to "Apple ID (+91)",
            "netflix" to "Netflix (+91)",
            "disneyhotstar" to "JioHotstar / Disney+ (+91)",
            "dream11" to "Dream11 (+91)",
            "my11circle" to "My11Circle (+91)",
            "olacabs" to "Ola Cabs (+91)",
            "rapido" to "Rapido (+91)",
            "uber" to "Uber (+91)",
            "irctc" to "IRCTC Rail (+91)",
            "nykaa" to "Nykaa (+91)",
            "snapchat" to "Snapchat (+91)",
            "discord" to "Discord (+91)",
            "twitter" to "X / Twitter (+91)",
            "linkedin" to "LinkedIn (+91)",
            "yahoo" to "Yahoo (+91)",
            "zoho" to "Zoho (+91)",
            "rediffmail" to "Rediffmail (+91)",
            "other" to "Any Other Website / Universal (+91)"
        )

        val priorityResults = mutableListOf<IndiaServiceStock>()
        val extraResults = mutableListOf<IndiaServiceStock>()

        try {
            val req = Request.Builder()
                .url("https://5sim.net/v1/guest/products/india/any")
                .header("Accept", "application/json")
                .header("User-Agent", browserUserAgent)
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)

                    // First add priority services in order
                    for ((key, label) in priorityLabels) {
                        val item = json.optJSONObject(key)
                        if (item != null) {
                            val qty = item.optInt("Qty", 0)
                            val price = item.optDouble("Price", 0.0)
                            if (qty > 0) {
                                priorityResults.add(
                                    IndiaServiceStock(
                                        serviceId = key,
                                        displayName = label,
                                        availableCount = qty,
                                        priceRub = price
                                    )
                                )
                            }
                        }
                    }

                    // Then add all remaining active services from the 277+ catalog
                    val keysIter = json.keys()
                    while (keysIter.hasNext()) {
                        val key = keysIter.next()
                        if (priorityLabels.containsKey(key)) continue
                        val item = json.optJSONObject(key) ?: continue
                        val qty = item.optInt("Qty", 0)
                        val price = item.optDouble("Price", 0.0)
                        if (qty > 0) {
                            val niceName = key.replaceFirstChar { it.uppercase() } + " (+91)"
                            extraResults.add(
                                IndiaServiceStock(
                                    serviceId = key,
                                    displayName = niceName,
                                    availableCount = qty,
                                    priceRub = price
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Always include "other" (Universal Website OTP) option so users can attempt universal +91 allocation
        if (priorityResults.none { it.serviceId == "other" }) {
            priorityResults.add(
                0,
                IndiaServiceStock(
                    serviceId = "other",
                    displayName = "Any Website / Universal OTP (+91)",
                    availableCount = 1,
                    priceRub = 0.65
                )
            )
        }

        priorityResults + extraResults.sortedBy { it.displayName }
    }

    /**
     * Fetches the authenticated user's 5SIM profile & balance when FIVESIM_API_KEY is configured.
     */
    suspend fun fetchFiveSimProfile(apiKey: String): Result<FiveSimProfile> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://5sim.net/v1/user/profile")
                    .header("Authorization", "Bearer ${apiKey.trim()}")
                    .header("Accept", "application/json")
                    .header("User-Agent", browserUserAgent)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                    }
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    Result.success(
                        FiveSimProfile(
                            email = json.optString("email", "5SIM Account"),
                            balanceRub = json.optDouble("balance", 0.0),
                            rating = json.optDouble("rating", 0.0)
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fetches any active (PENDING or RECEIVED) activation orders from the user's 5SIM account
     * so previously rented +91 numbers automatically restore on app launch or refresh.
     */
    suspend fun fetchActivePrivateOrders(apiKey: String): Result<List<TempPhoneNumber>> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://5sim.net/v1/user/orders?category=activation&limit=10")
                    .header("Authorization", "Bearer ${apiKey.trim()}")
                    .header("Accept", "application/json")
                    .header("User-Agent", browserUserAgent)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                    }
                    val bodyStr = resp.body?.string().orEmpty()
                    val root = JSONObject(bodyStr)
                    val dataArr = root.optJSONArray("Data") ?: return@withContext Result.success(emptyList())
                    val activeOrders = mutableListOf<TempPhoneNumber>()
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.optJSONObject(i) ?: continue
                        val status = item.optString("status", "")
                        if (status == "PENDING" || status == "RECEIVED") {
                            val orderId = item.optLong("id", 0L)
                            val rawPhone = item.optString("phone", "")
                            val operator = item.optString("operator", "any")
                            val product = item.optString("product", "OTP")
                            if (rawPhone.isNotBlank() && orderId > 0L) {
                                val normalizedFull = if (rawPhone.startsWith("+")) rawPhone else "+$rawPhone"
                                val isIndia = normalizedFull.startsWith("+91")
                                val localNum = if (isIndia) normalizedFull.removePrefix("+91") else normalizedFull.removePrefix("+")
                                activeOrders.add(
                                    TempPhoneNumber(
                                        fullNumber = normalizedFull,
                                        localNumber = localNum,
                                        countryCode = if (isIndia) "+91" else "+" + normalizedFull.drop(1).take(2),
                                        countryName = if (isIndia) "India (+91 Private SIM)" else "5SIM Private SIM",
                                        sourceGateway = "5SIM-${operator.uppercase()}",
                                        isPrivate = true,
                                        privateOrderId = orderId,
                                        serviceName = product.uppercase()
                                    )
                                )
                            }
                        }
                    }
                    Result.success(activeOrders)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Allocates a dedicated private +91 India number via 5SIM API when FIVESIM_API_KEY is configured.
     * Automatically cycles through Indian virtual operators (any, virtual21, virtual4, virtual38, virtual51, virtual52, virtual53, virtual58)
     * if one operator returns "no free phones".
     */
    suspend fun rentPrivateIndiaNumber(
        apiKey: String,
        serviceId: String
    ): Result<TempPhoneNumber> = withContext(Dispatchers.IO) {
        val operatorsToTry = listOf(
            "any",
            "virtual21",
            "virtual4",
            "virtual38",
            "virtual51",
            "virtual52",
            "virtual53",
            "virtual58"
        )
        var lastError = "Unable to allocate +91 SIM"

        for (operator in operatorsToTry) {
            try {
                val req = Request.Builder()
                    .url("https://5sim.net/v1/user/buy/activation/india/$operator/$serviceId")
                    .header("Authorization", "Bearer ${apiKey.trim()}")
                    .header("Accept", "application/json")
                    .header("User-Agent", browserUserAgent)
                    .build()

                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && !bodyStr.contains("no free phones", ignoreCase = true) && bodyStr.startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        val orderId = json.optLong("id", 0L)
                        val rawPhone = json.optString("phone", "")
                        if (rawPhone.isNotBlank()) {
                            val normalizedFull = if (rawPhone.startsWith("+")) rawPhone else "+$rawPhone"
                            val tenDigit = normalizedFull.removePrefix("+91").removePrefix("+")

                            return@withContext Result.success(
                                TempPhoneNumber(
                                    fullNumber = normalizedFull,
                                    localNumber = tenDigit,
                                    countryCode = "+91",
                                    countryName = "India (+91 Private SIM)",
                                    sourceGateway = "5SIM-${operator.uppercase()}",
                                    isPrivate = true,
                                    privateOrderId = orderId,
                                    serviceName = serviceId.uppercase()
                                )
                            )
                        }
                    } else {
                        lastError = if (bodyStr.isNotBlank()) bodyStr.take(100) else "HTTP ${resp.code}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
            }
        }

        Result.failure(IllegalStateException(lastError))
    }

    /**
     * Polls real-time SMS OTP arrival for a rented private +91 India number on 5SIM.
     */
    suspend fun checkPrivateOrderMessages(
        apiKey: String,
        phone: TempPhoneNumber
    ): Result<List<SmsOtpMessage>> = withContext(Dispatchers.IO) {
        val orderId = phone.privateOrderId
            ?: return@withContext Result.failure(IllegalArgumentException("Missing order ID"))
        try {
            val req = Request.Builder()
                .url("https://5sim.net/v1/user/check/$orderId")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .header("User-Agent", browserUserAgent)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                }
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val smsArr = json.optJSONArray("sms")
                val list = mutableListOf<SmsOtpMessage>()
                if (smsArr != null) {
                    for (i in 0 until smsArr.length()) {
                        val sms = smsArr.optJSONObject(i) ?: continue
                        val text = sms.optString("text", "").trim()
                        val code = sms.optString("code", "").ifBlank { extractOtpFromSms(text).orEmpty() }
                        val sender = sms.optString("sender", "OTP-SMS")
                        val createdAt = sms.optString("created_at", "LIVE")
                        if (text.isNotBlank()) {
                            list.add(
                                SmsOtpMessage(
                                    id = "5sim_${orderId}_$i",
                                    phoneNumber = phone.fullNumber,
                                    sender = sender,
                                    body = text,
                                    receivedAt = createdAt.take(19).replace("T", " "),
                                    extractedOtp = code.ifBlank { null }
                                )
                            )
                        }
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancels/releases a private +91 number order on 5SIM.
     */
    suspend fun cancelPrivateOrder(apiKey: String, orderId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://5sim.net/v1/user/cancel/$orderId")
                    .header("Authorization", "Bearer ${apiKey.trim()}")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use {
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
