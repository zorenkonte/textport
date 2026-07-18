package com.example.textport.smsrole

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Receives incoming SMS while Textport is the default SMS app.
 *
 * Textport is a backup tool, not a messenger — but the default SMS app is the
 * only component that gets `SMS_DELIVER`, and it alone is responsible for
 * writing incoming messages to the provider. So this receiver persists each
 * incoming message to the inbox to make sure nothing is lost while the user has
 * Textport set as default. It does not notify.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming SMS", e)
            return
        } ?: return
        if (messages.isEmpty()) return

        // A multipart SMS arrives as several PDUs sharing one logical message.
        val first = messages.first()
        val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, first.displayOriginatingAddress)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, first.timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            first.serviceCenterAddress?.let { put(Telephony.Sms.SERVICE_CENTER, it) }
        }

        try {
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist incoming SMS to inbox", e)
        }
    }

    private companion object {
        private const val TAG = "SmsDeliverReceiver"
    }
}
