package com.example.textport.data

import android.content.ContentResolver
import android.content.Context
import android.provider.Telephony
import com.example.textport.data.model.Message
import com.example.textport.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads SMS messages from the system content provider
 * ([Telephony.Sms.CONTENT_URI]).
 */
class SmsRepository(private val contentResolver: ContentResolver) {

    constructor(context: Context) : this(context.contentResolver)

    /**
     * Queries all SMS messages, newest first. Runs the query off the main thread.
     *
     * @throws SecurityException if the READ_SMS permission is not granted.
     */
    suspend fun loadMessages(): List<Message> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.READ,
        )

        val messages = mutableListOf<Message>()
        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeCol = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val threadCol = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val readCol = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)

            while (cursor.moveToNext()) {
                messages += Message(
                    id = cursor.getLong(idCol),
                    address = cursor.getString(addressCol) ?: "",
                    body = cursor.getString(bodyCol) ?: "",
                    date = cursor.getLong(dateCol),
                    type = MessageType.fromRaw(cursor.getInt(typeCol)),
                    threadId = cursor.getLong(threadCol),
                    read = cursor.getInt(readCol) != 0,
                )
            }
        }
        messages
    }
}
