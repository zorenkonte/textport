package com.example.textport.data.model

import android.provider.Telephony

/**
 * The kind of an SMS row, mapped from [Telephony.Sms.TYPE] into a readable enum.
 */
enum class MessageType(val label: String) {
    RECEIVED("received"),
    SENT("sent"),
    DRAFT("draft"),
    OUTBOX("outbox"),
    FAILED("failed"),
    QUEUED("queued"),
    ALL("all"),
    UNKNOWN("unknown");

    companion object {
        /** Maps a raw Telephony type constant to a [MessageType]. */
        fun fromRaw(raw: Int): MessageType = when (raw) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> RECEIVED
            Telephony.Sms.MESSAGE_TYPE_SENT -> SENT
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> DRAFT
            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> OUTBOX
            Telephony.Sms.MESSAGE_TYPE_FAILED -> FAILED
            Telephony.Sms.MESSAGE_TYPE_QUEUED -> QUEUED
            Telephony.Sms.MESSAGE_TYPE_ALL -> ALL
            else -> UNKNOWN
        }
    }
}

/**
 * A single SMS message read from the system content provider.
 *
 * @param date epoch milliseconds the message was sent/received.
 */
data class Message(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: MessageType,
    val threadId: Long,
    val read: Boolean,
)
