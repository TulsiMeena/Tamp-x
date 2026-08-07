package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

interface GeminiRestApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

class GeminiApiService {

    private val apiService: GeminiRestApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiRestApi::class.java)
    }

    data class AiAnalysisResult(
        val summary: String,
        val spamScore: Int,
        val spamReason: String,
        val smartReplies: List<String>
    )

    suspend fun analyzeEmail(subject: String, sender: String, body: String): AiAnalysisResult {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return generateLocalRuleBasedAnalysis(subject, sender, body)
        }

        val prompt = """
            You are an AI Email Safety and Summarization Assistant for disposable emails.
            Analyze this email:
            From: $sender
            Subject: $subject
            Body: $body

            Return a structured output in this exact format:
            SUMMARY: <1-2 concise sentences summarizing the email>
            SPAM_SCORE: <integer from 0 to 100 representing phishing/spam risk level>
            SPAM_REASON: <1 sentence explanation of risk level>
            SMART_REPLIES: <3 short, practical bullet points separated by pipe '|' symbol>
        """.trimIndent()

        return try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                )
            )
            val response = apiService.generateContent(apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (text != null) {
                parseAiOutput(text, subject, body)
            } else {
                generateLocalRuleBasedAnalysis(subject, sender, body)
            }
        } catch (e: Exception) {
            generateLocalRuleBasedAnalysis(subject, sender, body)
        }
    }

    private fun parseAiOutput(rawText: String, subject: String, body: String): AiAnalysisResult {
        var summary = "Email received with subject: $subject"
        var spamScore = 10
        var spamReason = "Normal transactional or temporary email."
        var smartReplies = listOf("Thank you, received.", "Please confirm details.", "Unsubscribe from list.")

        val lines = rawText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("SUMMARY:", ignoreCase = true) -> {
                    summary = trimmed.removePrefix("SUMMARY:").removePrefix("summary:").trim()
                }
                trimmed.startsWith("SPAM_SCORE:", ignoreCase = true) -> {
                    val scoreStr = trimmed.removePrefix("SPAM_SCORE:").removePrefix("spam_score:").trim()
                    spamScore = scoreStr.filter { it.isDigit() }.toIntOrNull() ?: 10
                }
                trimmed.startsWith("SPAM_REASON:", ignoreCase = true) -> {
                    spamReason = trimmed.removePrefix("SPAM_REASON:").removePrefix("spam_reason:").trim()
                }
                trimmed.startsWith("SMART_REPLIES:", ignoreCase = true) -> {
                    val repliesStr = trimmed.removePrefix("SMART_REPLIES:").removePrefix("smart_replies:").trim()
                    val parsed = repliesStr.split("|").map { it.trim().removePrefix("-").trim() }.filter { it.isNotEmpty() }
                    if (parsed.isNotEmpty()) {
                        smartReplies = parsed.take(3)
                    }
                }
            }
        }

        return AiAnalysisResult(
            summary = summary.ifBlank { "Summary: $subject" },
            spamScore = spamScore.coerceIn(0, 100),
            spamReason = spamReason,
            smartReplies = smartReplies
        )
    }

    private fun generateLocalRuleBasedAnalysis(subject: String, sender: String, body: String): AiAnalysisResult {
        val lowerBody = (subject + " " + body + " " + sender).lowercase()
        var spamScore = 5
        val reasons = mutableListOf<String>()

        if (lowerBody.contains("verify") || lowerBody.contains("code") || lowerBody.contains("otp")) {
            reasons.add("Contains verification code or OTP.")
            spamScore = 0
        }
        if (lowerBody.contains("urgent") || lowerBody.contains("winner") || lowerBody.contains("free crypto")) {
            reasons.add("Uses high-pressure or reward promotional keywords.")
            spamScore += 45
        }
        if (lowerBody.contains("click here") || lowerBody.contains("password reset")) {
            reasons.add("Includes action link or account security notice.")
            spamScore += 20
        }

        val summary = if (body.isNotBlank()) {
            body.take(120).trim() + if (body.length > 120) "..." else ""
        } else {
            "New incoming temporary message: '$subject'."
        }

        val reasonText = if (reasons.isNotEmpty()) reasons.joinToString(" ") else "Clean disposable communication."

        val smartReplies = listOf(
            "Got it, thanks!",
            "Confirm receipt",
            "Delete temporary mailbox"
        )

        return AiAnalysisResult(
            summary = summary,
            spamScore = spamScore.coerceIn(0, 100),
            spamReason = reasonText,
            smartReplies = smartReplies
        )
    }
}
