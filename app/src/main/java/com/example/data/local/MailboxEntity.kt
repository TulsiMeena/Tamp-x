package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mailboxes")
data class MailboxEntity(
    @PrimaryKey val address: String,
    val login: String,
    val domain: String,
    val sidToken: String? = null,
    val password: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)
