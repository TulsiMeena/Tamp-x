package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TempMailDao {

    // Mailboxes
    @Query("SELECT * FROM mailboxes ORDER BY createdAt DESC")
    fun getAllMailboxes(): Flow<List<MailboxEntity>>

    @Query("SELECT * FROM mailboxes WHERE isActive = 1 LIMIT 1")
    fun getActiveMailbox(): Flow<MailboxEntity?>

    @Query("SELECT * FROM mailboxes WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveMailboxSync(): MailboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMailbox(mailbox: MailboxEntity)

    @Query("UPDATE mailboxes SET isActive = 0")
    suspend fun deactivateAllMailboxes()

    @Query("UPDATE mailboxes SET isActive = 1 WHERE address = :address")
    suspend fun activateMailbox(address: String)

    @Transaction
    suspend fun setActiveMailbox(address: String) {
        deactivateAllMailboxes()
        activateMailbox(address)
    }

    @Query("DELETE FROM mailboxes WHERE address = :address")
    suspend fun deleteMailbox(address: String)

    // Emails
    @Query("SELECT * FROM emails WHERE mailboxAddress = :mailboxAddress ORDER BY id DESC")
    fun getEmailsForMailbox(mailboxAddress: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE mailboxAddress = :mailboxAddress ORDER BY id DESC")
    suspend fun getEmailsForMailboxSync(mailboxAddress: String): List<EmailEntity>

    @Query("SELECT * FROM emails WHERE id = :id AND mailboxAddress = :mailboxAddress LIMIT 1")
    fun getEmailById(id: Int, mailboxAddress: String): Flow<EmailEntity?>

    @Query("SELECT * FROM emails WHERE id = :id AND mailboxAddress = :mailboxAddress LIMIT 1")
    suspend fun getEmailByIdSync(id: Int, mailboxAddress: String): EmailEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailEntity)

    @Query("UPDATE emails SET isRead = :isRead WHERE id = :id AND mailboxAddress = :mailboxAddress")
    suspend fun updateEmailReadStatus(id: Int, mailboxAddress: String, isRead: Boolean)

    @Query("UPDATE emails SET aiSummary = :summary, aiSpamScore = :spamScore, aiSpamReason = :spamReason, aiSmartRepliesJson = :smartRepliesJson WHERE id = :id AND mailboxAddress = :mailboxAddress")
    suspend fun updateEmailAiDetails(
        id: Int,
        mailboxAddress: String,
        summary: String?,
        spamScore: Int?,
        spamReason: String?,
        smartRepliesJson: String?
    )

    @Query("DELETE FROM emails WHERE id = :id AND mailboxAddress = :mailboxAddress")
    suspend fun deleteEmail(id: Int, mailboxAddress: String)

    @Query("DELETE FROM emails WHERE mailboxAddress = :mailboxAddress")
    suspend fun clearMailboxEmails(mailboxAddress: String)

    @Query("DELETE FROM emails")
    suspend fun clearAllEmails()

    @Query("DELETE FROM mailboxes")
    suspend fun clearAllMailboxes()
}
