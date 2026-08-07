package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MailTmDomainCollection(
    @Json(name = "hydra:member") val member: List<MailTmDomainMember>? = null
)

@JsonClass(generateAdapter = true)
data class MailTmDomainMember(
    @Json(name = "id") val id: String? = null,
    @Json(name = "domain") val domain: String? = null,
    @Json(name = "isActive") val isActive: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class MailTmAccountRequest(
    @Json(name = "address") val address: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class MailTmAccountResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "address") val address: String? = null
)

@JsonClass(generateAdapter = true)
data class MailTmTokenResponse(
    @Json(name = "token") val token: String? = null,
    @Json(name = "id") val id: String? = null
)

@JsonClass(generateAdapter = true)
data class MailTmMessageCollection(
    @Json(name = "hydra:member") val member: List<MailTmMessageSummary>? = null
)

@JsonClass(generateAdapter = true)
data class MailTmMessageSummary(
    @Json(name = "id") val id: String? = null,
    @Json(name = "from") val from: MailTmSender? = null,
    @Json(name = "subject") val subject: String? = null,
    @Json(name = "intro") val intro: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "seen") val seen: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class MailTmSender(
    @Json(name = "address") val address: String? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class MailTmMessageDetail(
    @Json(name = "id") val id: String? = null,
    @Json(name = "from") val from: MailTmSender? = null,
    @Json(name = "subject") val subject: String? = null,
    @Json(name = "intro") val intro: String? = null,
    @Json(name = "text") val text: String? = null,
    @Json(name = "html") val html: List<String>? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)
