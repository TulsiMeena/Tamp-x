package com.example.util

import java.util.regex.Pattern

data class ExtractedVerificationData(
    val otpCode: String? = null,
    val verificationUrl: String? = null,
    val verificationLinkLabel: String = "Open Verification Link"
)

object VerificationExtractor {

    private val OTP_PATTERN = Pattern.compile(
        "(?i)(?:code|otp|pin|verification|passcode|secret|login|confirm|auth|token|verify)[^\\d\\r\\n]{0,20}?(\\b\\d{4,8}\\b|\\b[A-Z0-9]{5,8}\\b)",
        Pattern.CASE_INSENSITIVE
    )

    private val ISOLATED_NUMERIC_CODE = Pattern.compile("\\b(\\d{4,8})\\b")

    private val URL_PATTERN = Pattern.compile(
        "https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
        Pattern.CASE_INSENSITIVE
    )

    private val HREF_PATTERN = Pattern.compile(
        "<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    fun extract(subject: String?, bodyText: String?, bodyHtml: String?): ExtractedVerificationData {
        val fullContent = "${subject ?: ""} ${bodyText ?: ""} ${bodyHtml ?: ""}"
        val cleanContent = fullContent.replace("&nbsp;", " ")

        // 1. Extract OTP code
        var foundCode: String? = null

        val matcher = OTP_PATTERN.matcher(cleanContent)
        if (matcher.find()) {
            foundCode = matcher.group(1)
        }

        if (foundCode == null) {
            // Check subject directly for 4-8 digit code
            val subjectMatcher = ISOLATED_NUMERIC_CODE.matcher(subject ?: "")
            if (subjectMatcher.find()) {
                foundCode = subjectMatcher.group(1)
            }
        }

        if (foundCode == null && (cleanContent.contains("code", true) || cleanContent.contains("otp", true) || cleanContent.contains("verify", true))) {
            val bodyMatcher = ISOLATED_NUMERIC_CODE.matcher(bodyText ?: "")
            if (bodyMatcher.find()) {
                foundCode = bodyMatcher.group(1)
            }
        }

        // 2. Extract Verification Link
        var foundUrl: String? = null
        var foundLabel = "Open Verification Link"

        // First check href tags in HTML
        if (!bodyHtml.isNullOrBlank()) {
            val hrefMatcher = HREF_PATTERN.matcher(bodyHtml)
            while (hrefMatcher.find()) {
                val url = hrefMatcher.group(1)
                val rawLabel = hrefMatcher.group(2)?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""

                if (url != null && (url.startsWith("http://", true) || url.startsWith("https://", true))) {
                    val isVerificationKey = url.contains("verify", true) ||
                            url.contains("confirm", true) ||
                            url.contains("token", true) ||
                            url.contains("activate", true) ||
                            url.contains("auth", true) ||
                            rawLabel.contains("verify", true) ||
                            rawLabel.contains("confirm", true) ||
                            rawLabel.contains("click", true) ||
                            rawLabel.contains("activate", true)

                    if (isVerificationKey || foundUrl == null) {
                        foundUrl = url
                        if (rawLabel.isNotBlank() && rawLabel.length < 40) {
                            foundLabel = rawLabel
                        }
                        if (isVerificationKey) break
                    }
                }
            }
        }

        // Fallback check standard URL pattern in text
        if (foundUrl == null) {
            val urlMatcher = URL_PATTERN.matcher(cleanContent)
            while (urlMatcher.find()) {
                val url = urlMatcher.group(0)
                if (url != null) {
                    val isVerificationKey = url.contains("verify", true) ||
                            url.contains("confirm", true) ||
                            url.contains("token", true) ||
                            url.contains("activate", true) ||
                            url.contains("auth", true)

                    if (isVerificationKey || foundUrl == null) {
                        foundUrl = url
                        if (isVerificationKey) break
                    }
                }
            }
        }

        return ExtractedVerificationData(
            otpCode = foundCode?.trim(),
            verificationUrl = foundUrl?.trim(),
            verificationLinkLabel = foundLabel.trim().ifBlank { "Open Verification Link" }
        )
    }
}
