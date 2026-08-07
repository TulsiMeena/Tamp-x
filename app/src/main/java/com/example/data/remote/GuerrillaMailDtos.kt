package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GmAddressResponse(
    @Json(name = "email_addr") val emailAddr: String? = null,
    @Json(name = "email_timestamp") val emailTimestamp: Long? = null,
    @Json(name = "alias") val alias: String? = null,
    @Json(name = "sid_token") val sidToken: String? = null
)

@JsonClass(generateAdapter = true)
data class GmSetUserResponse(
    @Json(name = "email_addr") val emailAddr: String? = null,
    @Json(name = "alias") val alias: String? = null,
    @Json(name = "sid_token") val sidToken: String? = null,
    @Json(name = "site_id") val siteId: Int? = null
)

@JsonClass(generateAdapter = true)
data class GmListResponse(
    @Json(name = "list") val list: List<GmMailSummaryDto>? = null,
    @Json(name = "count") val count: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "sid_token") val sidToken: String? = null
)

@JsonClass(generateAdapter = true)
data class GmMailSummaryDto(
    @Json(name = "mail_id") val mailId: Any? = null,
    @Json(name = "mail_from") val mailFrom: String? = null,
    @Json(name = "mail_subject") val mailSubject: String? = null,
    @Json(name = "mail_excerpt") val mailExcerpt: String? = null,
    @Json(name = "mail_timestamp") val mailTimestamp: Long? = null,
    @Json(name = "mail_read") val mailRead: Int? = null,
    @Json(name = "mail_date") val mailDate: String? = null,
    @Json(name = "mail_body") val mailBody: String? = null
)

@JsonClass(generateAdapter = true)
data class GmMailDetailDto(
    @Json(name = "mail_id") val mailId: Any? = null,
    @Json(name = "mail_from") val mailFrom: String? = null,
    @Json(name = "mail_subject") val mailSubject: String? = null,
    @Json(name = "mail_excerpt") val mailExcerpt: String? = null,
    @Json(name = "mail_body") val mailBody: String? = null,
    @Json(name = "mail_timestamp") val mailTimestamp: Long? = null,
    @Json(name = "mail_date") val mailDate: String? = null,
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "sid_token") val sidToken: String? = null
)
