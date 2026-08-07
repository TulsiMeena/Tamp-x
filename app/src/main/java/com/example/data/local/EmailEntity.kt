package com.example.data.local

import androidx.room.Entity

@Entity(tableName = "emails", primaryKeys = ["id", "mailboxAddress"])
data class EmailEntity(
    val id: Int,
    val mailboxAddress: String,
    val from: String,
    val subject: String,
    val date: String,
    val timestamp: Long = System.currentTimeMillis(),
    val bodyHtml: String = "",
    val bodyText: String = "",
    val isRead: Boolean = false,
    val aiSummary: String? = null,
    val aiSpamScore: Int? = null,
    val aiSpamReason: String? = null,
    val aiSmartRepliesJson: String? = null,
    val attachmentsJson: String? = null
)
