package com.example.textport.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.example.textport.data.model.Message
import com.example.textport.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads SMS messages from the system content provider.
 *
 * The base [Telephony.Sms.CONTENT_URI] (`content://sms`) is meant to be the
 * union of every message box, but some OEM ROMs under-report the outgoing boxes
 * there. To make sure nothing is missed — in particular **failed**, **queued**
 * and **outbox** messages (e.g. texts to a number that never sends) — we also
 * query each box explicitly and merge the results, de-duplicating by row id.
 */
class SmsRepository(private val contentResolver: ContentResolver) {

    constructor(context: Context) : this(context.contentResolver)

    /**
     * Queries all SMS messages across every box, newest first. Runs off the
     * main thread.
     *
     * @throws SecurityException if the READ_SMS permission is not granted.
     */
    suspend fun loadMessages(): List<Message> = withContext(Dispatchers.IO) {
        // Keyed by row id so the same message read from the union URI and from a
        // per-box URI collapses into one entry.
        val byId = LinkedHashMap<Long, Message>()

        for (uri in SOURCE_URIS) {
            queryInto(uri, byId)
        }

        byId.values.sortedByDescending { it.date }
    }

    /**
     * Runs one query and folds rows into [target]. A denied permission surfaces
     * as a [SecurityException], which is rethrown so the caller can react; any
     * other per-URI failure (e.g. a box a given ROM doesn't expose) is logged
     * and skipped so one odd box can't sink the whole load.
     */
    private fun queryInto(uri: Uri, target: MutableMap<Long, Message>) {
        val cursor = try {
            contentResolver.query(uri, PROJECTION, null, null, "${Telephony.Sms.DATE} DESC")
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Skipping SMS box $uri: ${e.message}")
            return
        } ?: return

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeCol = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val threadCol = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val readCol = c.getColumnIndexOrThrow(Telephony.Sms.READ)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                target[id] = Message(
                    id = id,
                    address = c.getString(addressCol) ?: "",
                    body = c.getString(bodyCol) ?: "",
                    date = c.getLong(dateCol),
                    type = MessageType.fromRaw(c.getInt(typeCol)),
                    threadId = c.getLong(threadCol),
                    read = c.getInt(readCol) != 0,
                )
            }
        }
    }

    private companion object {
        private const val TAG = "SmsRepository"

        private val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.READ,
        )

        // The union first, then each box explicitly so under-reported outgoing
        // boxes (failed / queued / outbox / draft) are still captured.
        private val SOURCE_URIS: List<Uri> = listOf(
            Telephony.Sms.CONTENT_URI,
            Telephony.Sms.Inbox.CONTENT_URI,
            Telephony.Sms.Sent.CONTENT_URI,
            Telephony.Sms.Draft.CONTENT_URI,
            Telephony.Sms.Outbox.CONTENT_URI,
            Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "failed"),
            Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "queued"),
        )
    }
}
