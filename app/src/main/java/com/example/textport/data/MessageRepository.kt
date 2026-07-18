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
 * Reads messages from the system telephony providers — both **SMS**
 * ([Telephony.Sms]) and **MMS** ([Telephony.Mms]).
 *
 * MMS matters because a text that fails to a number that can't receive it is
 * frequently stored by the messaging app as an MMS in the outbox, not as an SMS
 * — so an SMS-only read misses those "didn't send" notes entirely.
 *
 * Reading both providers needs only the `READ_SMS` permission; the app does not
 * have to be the default SMS handler.
 */
class MessageRepository(private val contentResolver: ContentResolver) {

    constructor(context: Context) : this(context.contentResolver)

    /**
     * Loads all SMS and MMS messages, newest first, off the main thread.
     *
     * @throws SecurityException if the READ_SMS permission is not granted.
     */
    suspend fun loadMessages(): List<Message> = withContext(Dispatchers.IO) {
        // Keyed by stableKey so a row read from both the union URI and a per-box
        // URI collapses into one entry (SMS and MMS ids overlap, hence the key).
        val byKey = LinkedHashMap<String, Message>()

        for (uri in SMS_SOURCE_URIS) {
            querySmsInto(uri, byKey)
        }
        queryMmsInto(byKey)

        byKey.values.sortedByDescending { it.date }
    }

    private fun querySmsInto(uri: Uri, target: MutableMap<String, Message>) {
        val cursor = try {
            contentResolver.query(uri, SMS_PROJECTION, null, null, "${Telephony.Sms.DATE} DESC")
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
                val message = Message(
                    id = id,
                    address = c.getString(addressCol) ?: "",
                    body = c.getString(bodyCol) ?: "",
                    date = c.getLong(dateCol),
                    type = MessageType.fromRaw(c.getInt(typeCol)),
                    threadId = c.getLong(threadCol),
                    read = c.getInt(readCol) != 0,
                    isMms = false,
                )
                target[message.stableKey] = message
            }
        }
    }

    private fun queryMmsInto(target: MutableMap<String, Message>) {
        val cursor = try {
            contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                MMS_PROJECTION,
                null,
                null,
                "${Telephony.Mms.DATE} DESC",
            )
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Skipping MMS: ${e.message}")
            return
        } ?: return

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(Telephony.Mms._ID)
            val threadCol = c.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
            val dateCol = c.getColumnIndexOrThrow(Telephony.Mms.DATE)
            val boxCol = c.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
            val readCol = c.getColumnIndexOrThrow(Telephony.Mms.READ)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val box = c.getInt(boxCol)
                val message = Message(
                    id = id,
                    address = readMmsAddress(id, box),
                    body = readMmsText(id),
                    // MMS date is in SECONDS; SMS is in millis. Normalize to millis.
                    date = c.getLong(dateCol) * 1000L,
                    type = MessageType.fromMmsBox(box),
                    threadId = c.getLong(threadCol),
                    read = c.getInt(readCol) != 0,
                    isMms = true,
                )
                target[message.stableKey] = message
            }
        }
    }

    /** Concatenates the text/plain parts of an MMS into its body. */
    private fun readMmsText(mmsId: Long): String {
        val cursor = try {
            contentResolver.query(
                MMS_PART_URI,
                arrayOf(PART_ID, PART_CONTENT_TYPE, PART_TEXT),
                "$PART_MSG_ID = ?",
                arrayOf(mmsId.toString()),
                null,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not read MMS parts for $mmsId: ${e.message}")
            return ""
        } ?: return ""

        val builder = StringBuilder()
        cursor.use { c ->
            val idCol = c.getColumnIndex(PART_ID)
            val ctCol = c.getColumnIndex(PART_CONTENT_TYPE)
            val textCol = c.getColumnIndex(PART_TEXT)
            while (c.moveToNext()) {
                if (c.getString(ctCol) != "text/plain") continue
                val inline = if (textCol >= 0) c.getString(textCol) else null
                if (!inline.isNullOrEmpty()) {
                    builder.append(inline)
                } else {
                    // Larger text is stored in a file-backed part.
                    builder.append(readMmsPartFile(c.getLong(idCol)))
                }
            }
        }
        return builder.toString()
    }

    private fun readMmsPartFile(partId: Long): String = try {
        contentResolver.openInputStream(Uri.withAppendedPath(MMS_PART_URI, partId.toString()))
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            .orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Could not read MMS part file $partId: ${e.message}")
        ""
    }

    /**
     * Resolves the counterpart address of an MMS from its `addr` sub-table: the
     * sender (FROM) for incoming messages, the recipient (TO) otherwise.
     */
    private fun readMmsAddress(mmsId: Long, box: Int): String {
        val uri = Telephony.Mms.CONTENT_URI.buildUpon()
            .appendPath(mmsId.toString()).appendPath("addr").build()
        val cursor = try {
            contentResolver.query(uri, arrayOf(ADDR_ADDRESS, ADDR_TYPE), null, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read MMS address for $mmsId: ${e.message}")
            return ""
        } ?: return ""

        var from = ""
        var to = ""
        cursor.use { c ->
            val addrCol = c.getColumnIndex(ADDR_ADDRESS)
            val typeCol = c.getColumnIndex(ADDR_TYPE)
            while (c.moveToNext()) {
                val addr = c.getString(addrCol)
                if (addr.isNullOrBlank() || addr == INSERT_ADDRESS_TOKEN) continue
                when (c.getInt(typeCol)) {
                    PDU_FROM -> if (from.isEmpty()) from = addr
                    PDU_TO -> if (to.isEmpty()) to = addr
                }
            }
        }
        return if (box == Telephony.Mms.MESSAGE_BOX_INBOX) {
            from.ifEmpty { to }
        } else {
            to.ifEmpty { from }
        }
    }

    private companion object {
        private const val TAG = "MessageRepository"

        private val SMS_PROJECTION = arrayOf(
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
        private val SMS_SOURCE_URIS: List<Uri> = listOf(
            Telephony.Sms.CONTENT_URI,
            Telephony.Sms.Inbox.CONTENT_URI,
            Telephony.Sms.Sent.CONTENT_URI,
            Telephony.Sms.Draft.CONTENT_URI,
            Telephony.Sms.Outbox.CONTENT_URI,
            Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "failed"),
            Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "queued"),
        )

        private val MMS_PROJECTION = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.READ,
        )

        private val MMS_PART_URI: Uri = Uri.parse("content://mms/part")

        // MMS part / address column names (stable public provider columns).
        private const val PART_ID = "_id"
        private const val PART_MSG_ID = "mid"
        private const val PART_CONTENT_TYPE = "ct"
        private const val PART_TEXT = "text"
        private const val ADDR_ADDRESS = "address"
        private const val ADDR_TYPE = "type"
        private const val INSERT_ADDRESS_TOKEN = "insert-address-token"

        // PDU address types (see MMS PDU headers).
        private const val PDU_FROM = 137
        private const val PDU_TO = 151
    }
}
