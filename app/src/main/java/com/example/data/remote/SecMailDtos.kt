package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SecMailMessageDto(
    @Json(name = "id") val id: Int,
    @Json(name = "from") val from: String? = null,
    @Json(name = "subject") val subject: String? = null,
    @Json(name = "date") val date: String? = null
)

@JsonClass(generateAdapter = true)
data class SecMailAttachmentDto(
    @Json(name = "filename") val filename: String? = null,
    @Json(name = "contentType") val contentType: String? = null,
    @Json(name = "size") val size: Int? = null
)

@JsonClass(generateAdapter = true)
data class SecMailDetailDto(
    @Json(name = "id") val id: Int,
    @Json(name = "from") val from: String? = null,
    @Json(name = "subject") val subject: String? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "textBody") val textBody: String? = null,
    @Json(name = "htmlBody") val htmlBody: String? = null,
    @Json(name = "attachments") val attachments: List<SecMailAttachmentDto>? = null
)
